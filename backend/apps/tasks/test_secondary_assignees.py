from datetime import timedelta

from django.db import IntegrityError, transaction
from django.utils import timezone

from apps.accounts.models import AppOperationAuditLog, User

from .models import Task, TaskSecondaryAssignee
from .test_midterm_ljc_tasks_notifications import (
    APPROVED, NOTICE, PENDING, TASKS, MidtermTaskFixtures,
)


class SecondaryAssigneeTests(MidtermTaskFixtures):
    def notification_recipients(self, task, kind, *, unresolved=False):
        rows = task.notifications.filter(notification_type=kind)
        if unresolved:
            rows = rows.filter(resolved_at__isnull=True)
        return set(rows.values_list("recipient_id", flat=True))

    def approve(self, task):
        self.request(self.manager, "post", self.route(task, "approval/"), 200, decision=APPROVED)

    def test_create_validates_duplicate_inactive_and_role_scope_before_writing(self):
        for secondary in (
            [self.worker.pk], [self.leader.pk, self.leader.pk],
            [self.disabled.pk], [self.other_worker.pk],
        ):
            with self.subTest(secondary=secondary):
                result = self.create(self.leader, self.worker, 400, secondary_assignee_ids=secondary)
                self.assertIn("secondary_assignee_ids", result)
        for creator, primary, invalid in (
            (self.manager, self.other_manager, self.worker),
            (self.worker, self.worker, self.leader),
        ):
            self.assertIn("secondary_assignee_ids", self.create(
                creator, primary, 400, secondary_assignee_ids=[invalid.pk],
            ))
        self.assertFalse(Task.objects.exists())
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_founder_can_create_with_multiple_secondary_assignees(self):
        secondary = [self.other_worker, self.leader, self.founder]
        data = self.create(self.founder, self.worker, secondary_assignee_ids=[user.pk for user in secondary])
        task = Task.objects.get(pk=data["id"])
        self.assertTaskState(task, approval_status=APPROVED, assignee_id=self.worker.pk)
        self.assertCountEqual(task.secondary_assignees.all(), secondary)
        self.assertCountEqual([row["id"] for row in data["secondary_assignees"]], [user.pk for user in secondary])
        self.assertEqual(self.notification_recipients(task, NOTICE.ASSIGNMENT_RECEIVED),
                         {self.worker.pk, *[user.pk for user in secondary]})
        with self.assertRaises(IntegrityError), transaction.atomic():
            TaskSecondaryAssignee.objects.create(task=task, user=self.leader)

    def test_candidate_search_by_role_keeps_assignment_scope(self):
        url = "/api/v1/app/organization/assignable-users/"
        for user, keyword, expected in (
            (self.founder, "employee", {self.worker.pk, self.other_worker.pk}),
            (self.leader, User.Role.EMPLOYEE.label, {self.worker.pk}),
            (self.worker, "team_leader", set()),
        ):
            with self.subTest(user=user.pk, keyword=keyword):
                self.assertEqual({row["id"] for row in self.results(user, url, keyword=keyword)}, expected)

    def test_approval_gates_visibility_then_lists_and_pins_without_duplicates(self):
        task = Task.objects.get(pk=self.create(self.manager, self.other_manager, secondary_assignee_ids=[
            self.leader.pk, self.manager.pk,
        ])["id"])
        self.assertEqual(self.results(self.leader, TASKS), [])
        self.assertEqual(self.notification_recipients(task, NOTICE.ASSIGNMENT_RECEIVED), set())
        self.request(self.leader, "get", self.route(task), 404)
        self.request(self.leader, "post", self.route(task, "updates/"), 400, content="尚不可执行")
        self.request(self.founder, "post", self.route(task, "approval/"), 200, decision=APPROVED)

        for user in (self.other_manager, self.manager, self.leader):
            rows = self.results(user, TASKS, scope="all")
            self.assertEqual([row["id"] for row in rows], [task.pk])
            self.assertEqual(rows[0]["assignee_name"], self.other_manager.display_name)
        detail = self.request(self.leader, "get", self.route(task), 200)
        self.assertCountEqual([row["id"] for row in detail["secondary_assignees"]],
                              [self.leader.pk, self.manager.pk])
        for keyword in ("", self.leader.display_name):
            rows = self.results(self.manager, TASKS + "subordinates/", user_id=self.leader.pk, keyword=keyword)
            self.assertEqual([row["id"] for row in rows], [task.pk])
        rows = self.results(self.founder, TASKS + "subordinates/")
        self.assertEqual([row["id"] for row in rows], [task.pk])
        self.request(self.other_manager, "get", TASKS + "subordinates/", 403, user_id=self.leader.pk)
        self.request(self.other_worker, "get", self.route(task), 404)
        self.request(self.leader, "post", self.route(task, "pin/"), 201)
        self.assertEqual([row["id"] for row in self.results(self.leader, TASKS + "pinned/")], [task.pk])
        self.assertEqual(self.notification_recipients(task, NOTICE.ASSIGNMENT_RECEIVED),
                         {self.other_manager.pk, self.leader.pk, self.manager.pk})

    def test_patch_treats_list_as_set_and_reapproves_only_an_actual_change(self):
        worker_two = User.objects.create_user(
            username="second-worker", display_name="第二位成员", account_type=User.AccountType.BUSINESS,
            business_role=User.Role.EMPLOYEE, manager=self.leader, team=self.worker.team,
            department=self.worker.department,
        )
        task = Task.objects.get(pk=self.create(self.leader, self.worker, secondary_assignee_ids=[
            worker_two.pk, self.leader.pk,
        ])["id"])
        self.approve(task)
        self.request(self.leader, "patch", self.route(task), 200, secondary_assignee_ids=[
            self.leader.pk, worker_two.pk,
        ])
        self.request(self.leader, "patch", self.route(task), 200, association_note="只改关联说明")
        self.assertTaskState(task, approval_status=APPROVED)
        self.assertEqual(task.approval_records.count(), 1)
        self.assertCountEqual(task.secondary_assignees.all(), [worker_two, self.leader])

        data = self.request(self.leader, "patch", self.route(task), 400, assignee_id=worker_two.pk)
        self.assertIn("secondary_assignee_ids", data)
        self.assertTaskState(task, assignee_id=self.worker.pk, approval_status=APPROVED)

        self.request(self.leader, "patch", self.route(task), 200, secondary_assignee_ids=[])
        self.assertTaskState(task, approval_status=PENDING)
        self.assertEqual(task.approval_records.count(), 2)
        self.assertFalse(task.secondary_assignees.exists())
        self.assertEqual(self.notification_recipients(task, NOTICE.ASSIGNMENT_RECEIVED, unresolved=True), set())
        self.approve(task)
        self.assertEqual(self.notification_recipients(task, NOTICE.ASSIGNMENT_RECEIVED, unresolved=True), {self.worker.pk})
        self.request(worker_two, "get", self.route(task), 404)

        recorded_name = worker_two.display_name
        User.objects.filter(pk=worker_two.pk).update(display_name="变更后的姓名")
        history = self.results(self.leader, self.route(task, "history/"))[-1]["changes"]["secondary_assignee_ids"]
        self.assertIn({"id": worker_two.pk, "display_name": recorded_name}, history["before"])
        self.assertEqual(history["after"], [])
        audit = AppOperationAuditLog.objects.filter(actor=self.leader, action="update", object_id=task.pk).first()
        self.assertEqual(audit.before_data["secondary_assignee_ids"], history["before"])

    def test_founder_remains_approved_and_notifies_only_new_participants_on_edit(self):
        task = Task.objects.get(pk=self.create(self.founder, self.worker, secondary_assignee_ids=[self.leader.pk])["id"])
        self.request(self.founder, "patch", self.route(task), 200, secondary_assignee_ids=[self.other_worker.pk])
        self.assertTaskState(task, approval_status=APPROVED)
        self.assertFalse(task.approval_records.exists())
        self.assertEqual(self.notification_recipients(task, NOTICE.ASSIGNMENT_RECEIVED, unresolved=True),
                         {self.worker.pk, self.other_worker.pk})
        self.assertEqual(task.notifications.filter(recipient=self.worker, notification_type=NOTICE.ASSIGNMENT_RECEIVED).count(), 1)
        self.assertIsNotNone(task.notifications.get(recipient=self.leader, notification_type=NOTICE.ASSIGNMENT_RECEIVED).resolved_at)

    def test_progress_and_completion_resubmission_permissions_and_outcomes(self):
        task = Task.objects.get(pk=self.create(self.founder, self.worker, secondary_assignee_ids=[self.leader.pk])["id"])
        self.request(self.leader, "post", self.route(task, "updates/"), 201, content="协作进展")
        self.assertEqual(task.updates.get().author, self.leader)
        url = self.route(task, "submissions/")
        self.request(self.leader, "post", url, 403, completion_note="次要负责人不能提交")
        first = self.request(self.worker, "post", url, 201, completion_note="第一版")
        self.request(self.founder, "post", self.route(task, f"submissions/{first['id']}/review/"), 200,
                     decision="returned", comment="补充证明")
        self.request(self.leader, "post", url, 403, completion_note="次要负责人不能重提")
        second = self.request(self.worker, "post", url, 201, completion_note="第二版")
        self.request(self.founder, "post", self.route(task, f"submissions/{second['id']}/review/"), 200,
                     decision="accepted")
        self.assertEqual(task.submissions.count(), 2)
        for kind in (NOTICE.SUBMISSION_RETURNED, NOTICE.SUBMISSION_ACCEPTED):
            self.assertEqual(self.notification_recipients(task, kind), {self.worker.pk})

    def test_cancellation_notifies_other_assignees_and_resolves_assignment_reminders(self):
        cases = (
            ("creator_is_primary", self.founder, [self.leader.pk, self.worker.pk],
             {self.leader.pk, self.worker.pk}),
            ("creator_is_secondary", self.worker, [self.founder.pk, self.leader.pk],
             {self.leader.pk, self.worker.pk}),
            ("creator_is_neither", self.worker, [self.leader.pk], {self.leader.pk, self.worker.pk}),
            ("creator_is_only_assignee", self.founder, [], set()),
        )
        for name, primary, secondary_ids, expected_recipients in cases:
            with self.subTest(name=name):
                data = self.create(self.founder, primary, secondary_assignee_ids=secondary_ids)
                task = Task.objects.get(pk=data["id"])
                self.request(self.founder, "post", self.route(task, "cancel/"), 200, reason="范围取消")
                self.assertTaskState(task, lifecycle_status=Task.Lifecycle.CANCELLED)
                self.assertEqual(self.notification_recipients(task, NOTICE.TASK_CANCELLED), expected_recipients)
                self.assertEqual(
                    self.notification_recipients(task, NOTICE.ASSIGNMENT_RECEIVED, unresolved=True), set(),
                )
                unread = task.notifications.filter(notification_type=NOTICE.TASK_CANCELLED, is_read=False)
                self.assertEqual(unread.count(), len(expected_recipients))

    def test_self_completion_resolves_secondary_assignment_without_review_notice(self):
        task = Task.objects.get(pk=self.create(self.founder, self.founder, secondary_assignee_ids=[
            self.worker.pk,
        ])["id"])
        data = self.request(self.founder, "post", self.route(task, "submissions/"), 201, completion_note="办结")
        self.assertEqual(data["review_status"], "self_completed")
        self.assertEqual(self.notification_recipients(task, NOTICE.ASSIGNMENT_RECEIVED, unresolved=True), set())
        self.assertEqual(set(task.notifications.values_list("notification_type", flat=True)),
                         {NOTICE.ASSIGNMENT_RECEIVED})

    def test_work_views_show_bars_for_both_roles_but_events_only_for_actual_actor(self):
        today = timezone.localdate()
        now = timezone.now()
        task = Task.objects.get(pk=self.create(
            self.founder, self.worker, secondary_assignee_ids=[self.leader.pk],
            estimated_start_at=(now - timedelta(minutes=1)).isoformat(),
            due_at=(now + timedelta(days=1)).isoformat(),
        )["id"])
        self.request(self.leader, "post", self.route(task, "updates/"), 201, content="协作进展")
        base = "/api/v1/app/work-views/"
        for user in (self.leader, self.worker):
            month = self.request(user, "get", base + "month/", 200, year=today.year, month=today.month)
            day_slot = next(day for day in month["days"] if str(day["date"]) == today.isoformat())
            self.assertEqual([row["task_id"] for row in day_slot["bars"]], [task.pk])
            for span in ("week", "day"):
                data = self.request(user, "get", f"{base}{span}/", 200, date=today.isoformat())
                self.assertEqual([row["task_id"] for row in data["all_day_tasks"]], [task.pk])
                update_events = [row["task_id"] for row in data["events"] if row["event_type"] == "progress_updated"]
                self.assertEqual(update_events, [task.pk] if user == self.leader else [])
        manager = self.request(self.manager, "get", base + "day/", 200,
                               date=today.isoformat(), user_id=self.leader.pk)
        self.assertEqual([row["task_id"] for row in manager["all_day_tasks"]], [task.pk])
