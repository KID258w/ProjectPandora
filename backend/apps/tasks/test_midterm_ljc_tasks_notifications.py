from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

from django.test import override_settings
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from apps.accounts.models import AppOperationAuditLog, Department, Team, User
from apps.home.services import unread_notification_count
from apps.tags.models import PALETTE, Tag

from .models import Task, TaskApprovalRecord, TaskChangeHistory, TaskNotification, TaskPin, TaskSubmission
from .services import (
    assignable_users, create_approval_round, create_task_notification, require_executable,
    resolve_task_notifications, send_assignment, validate_assignee, visible_task,
)
from .views import filtered_tasks, notification_data, validate_task_schedule


TASKS = "/api/v1/app/tasks/"
INBOX = "/api/v1/app/task-approvals/inbox/"
OUTBOX = "/api/v1/app/task-approvals/outbox/"
NOTICES = "/api/v1/app/notifications/"
DUE = datetime(2026, 11, 20, 4, tzinfo=timezone.utc)
APPROVED = Task.Approval.APPROVED
PENDING = Task.Approval.PENDING
RETURNED = Task.Approval.RETURNED
ACTIVE = Task.Lifecycle.ACTIVE
CANCELLED = Task.Lifecycle.CANCELLED
COMPLETED = Task.Completion.COMPLETED
UNFINISHED = Task.Completion.UNFINISHED
NOTICE = TaskNotification.Type


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"], TIME_ZONE="Asia/Shanghai")
class MidtermTaskFixtures(APITestCase):
    @classmethod
    def setUpTestData(cls):
        dev = Department.objects.create(name="研发")
        product = Department.objects.create(name="产品")
        team = Team.objects.create(name="研发组", department=dev)
        other_team = Team.objects.create(name="产品组", department=product)

        def member(name, role, manager=None, department=None, team=None, active=True):
            return User.objects.create_user(
                username=name, display_name=name, account_type=User.AccountType.BUSINESS,
                business_role=role, manager=manager, department=department, team=team,
                is_active=active,
            )

        cls.founder = member("创始人", User.Role.FOUNDER)
        cls.manager = member("研发经理", User.Role.DEPARTMENT_MANAGER, cls.founder, dev)
        cls.leader = member("研发组长", User.Role.TEAM_LEADER, cls.manager, dev, team)
        cls.worker = member("研发员工", User.Role.EMPLOYEE, cls.leader, dev, team)
        cls.disabled = member("停用员工", User.Role.EMPLOYEE, cls.leader, dev, team, active=False)
        cls.other_manager = member("产品经理", User.Role.DEPARTMENT_MANAGER, cls.founder, product)
        cls.other_leader = member("产品组长", User.Role.TEAM_LEADER, cls.other_manager, product, other_team)
        cls.other_worker = member("产品员工", User.Role.EMPLOYEE, cls.other_leader, product, other_team)
        cls.tag = Tag.objects.create(name="测试标签", color=PALETTE[0])
        cls.disabled_tag = Tag.objects.create(name="停用标签", color=PALETTE[1], is_active=False)

    def task(self, creator=None, assignee=None, approval=APPROVED, **fields):
        creator = creator or self.leader
        assignee = assignee or self.worker
        return Task.objects.create(
            creator=creator, assignee=assignee, title=fields.pop("title", "中期任务"),
            description="测试任务说明", due_at=fields.pop("due_at", DUE), approval_status=approval,
            **fields,
        )

    def pending(self):
        task = self.task(approval=PENDING)
        create_approval_round(task)
        return task

    def submission(self, task, **fields):
        return TaskSubmission.objects.create(
            task=task, submitted_by=task.assignee, completion_note="成果", review_status="waiting", **fields,
        )

    def notice(self, task, recipient=None, kind=NOTICE.ASSIGNMENT_RECEIVED, **fields):
        key = fields.pop("event_key", f"test:{TaskNotification.objects.count() + 1}")
        return TaskNotification.objects.create(
            task=task, recipient=recipient or task.assignee, notification_type=kind, event_key=key, **fields,
        )

    @staticmethod
    def route(task, suffix=""):
        return f"{TASKS}{task.pk}/{suffix}"

    def request(self, user, method, url, status, **data):
        if user is None:
            self.client.credentials()
        else:
            token = AccessToken.for_user(user)
            token["account_type"] = user.account_type
            self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")
        response = getattr(self.client, method)(url, data, format="json")
        self.assertEqual(response.status_code, status, response.data)
        return response.data["data"]

    def create(self, creator, assignee, status=201, **values):
        payload = {
            "assignee_id": assignee.pk, "title": "新任务", "description": "需求",
            "due_at": DUE.isoformat(), **values,
        }
        return self.request(creator, "post", TASKS, status, **payload)

    def results(self, user, url, **params):
        return self.request(user, "get", url, 200, **params)["results"]

    def audit(self, actor, action, object_id):
        return AppOperationAuditLog.objects.get(actor=actor, action=action, object_id=object_id)

    def assertTaskState(self, task, **expected):
        task.refresh_from_db()
        for key, value in expected.items():
            self.assertEqual(getattr(task, key), value, key)


class MidtermTaskQueryTests(MidtermTaskFixtures):

    def test_assignable_manager(self):
        # TC-LJC-001 · assignable_users()
        self.assertCountEqual(assignable_users(self.manager), [self.manager, self.leader, self.other_manager])

    def test_assignable_employee_self(self):
        # TC-LJC-002 · assignable_users()
        self.assertEqual(assignable_users(self.worker), [self.worker])

    def test_assignable_excludes_disabled(self):
        # TC-LJC-003 · assignable_users()
        members = assignable_users(self.founder)
        self.assertIn(self.worker, members)
        self.assertNotIn(self.disabled, members)

    def test_assignee_same_team(self):
        # TC-LJC-004 · validate_assignee()
        self.assertEqual(validate_assignee(self.leader, self.worker.pk), self.worker)

    def test_assignee_self(self):
        # TC-LJC-005 · validate_assignee()
        self.assertEqual(validate_assignee(self.worker, self.worker.pk), self.worker)

    def test_assignee_denied(self):
        # TC-LJC-006 · validate_assignee()
        for person in (self.other_worker, self.disabled):
            with self.subTest(person=person.username):
                with self.assertRaises(ValidationError) as error:
                    validate_assignee(self.leader, person.pk)
                self.assertIn("assignee_id", error.exception.detail)

    def test_schedule_before_due(self):
        # TC-LJC-007 · validate_task_schedule()
        self.assertIsNone(validate_task_schedule(DUE - timedelta(seconds=1), DUE))

    def test_schedule_equal_due(self):
        # TC-LJC-008 · validate_task_schedule()
        self.assertIsNone(validate_task_schedule(DUE, DUE))
        self.assertIsNone(validate_task_schedule(None, DUE))

    def test_schedule_after_due(self):
        # TC-LJC-009 · validate_task_schedule()
        with self.assertRaises(ValidationError) as error:
            validate_task_schedule(DUE + timedelta(seconds=1), DUE)
        self.assertIn("estimated_start_at", error.exception.detail)

    def test_create_pending_audit(self):
        # TC-LJC-010 · TaskListView.post()
        data = self.create(self.leader, self.worker, tag_ids=[self.tag.pk])
        task = Task.objects.get(pk=data["id"])
        self.assertEqual(data["approval_status"], PENDING)
        self.assertTaskState(task, creator_id=self.leader.pk, assignee_id=self.worker.pk)
        self.assertEqual(list(task.tag_links.values_list("tag_id", flat=True)), [self.tag.pk])
        self.assertEqual(task.changes.get().actor, self.leader)
        self.assertEqual(task.approval_records.get().approver, self.manager)
        self.assertEqual(task.notifications.get().recipient, self.manager)
        self.assertEqual(task.notifications.get().notification_type, NOTICE.APPROVAL_REQUIRED)
        self.assertEqual(self.audit(self.leader, "create", task.pk).object_label_snapshot, "新任务")

    def test_create_founder_max_title(self):
        # TC-LJC-011 · TaskListView.post()
        title = "测" * 200
        data = self.create(self.founder, self.worker, title=title)
        task = Task.objects.get(pk=data["id"])
        self.assertTaskState(task, title=title, approval_status=APPROVED, estimated_start_at=None)
        self.assertFalse(task.approval_records.exists())
        self.assertEqual(task.notifications.get().notification_type, NOTICE.ASSIGNMENT_RECEIVED)

    def test_create_rejects_inactive_tag(self):
        # TC-LJC-012 · TaskListView.post()
        self.create(self.leader, self.worker, 400, tag_ids=[self.disabled_tag.pk])
        for model in (Task, TaskChangeHistory, TaskApprovalRecord, TaskNotification, AppOperationAuditLog):
            self.assertFalse(model.objects.exists(), model.__name__)

    def test_list_mine_only(self):
        # TC-LJC-013 · TaskListView.get()
        approved = self.task()
        self.pending()
        self.task(self.other_leader, self.other_worker)
        rows = self.results(self.worker, TASKS)
        self.assertEqual([row["id"] for row in rows], [approved.pk])
        self.assertEqual(rows[0]["completion_status"], UNFINISHED)

    def test_list_union_deduplicates(self):
        # TC-LJC-014 · TaskListView.get()
        task = self.task(self.worker, self.worker)
        rows = self.results(self.worker, TASKS, scope="all")
        self.assertEqual([row["id"] for row in rows], [task.pk])

    def test_list_rejects_invalid_scope(self):
        # TC-LJC-015 · TaskListView.get()
        data = self.request(self.worker, "get", TASKS, 400, scope="department")
        self.assertIn("scope", data)

    def test_filter_statuses(self):
        # TC-LJC-016 · filtered_tasks()
        completed = self.task(completion_status=COMPLETED)
        self.task()
        self.task(lifecycle_status=CANCELLED)
        request = SimpleNamespace(query_params={"completion_status": "completed"})
        self.assertEqual(list(filtered_tasks(request, Task.objects.all())), [completed])

    def test_filter_inclusive_local_day(self):
        # TC-LJC-017 · filtered_tasks()
        start = datetime(2026, 11, 19, 16, tzinfo=timezone.utc)
        self.task(due_at=start - timedelta(minutes=1))
        target = self.task(due_at=start)
        self.task(due_at=start + timedelta(days=1))
        request = SimpleNamespace(query_params={"due_from": "2026-11-20", "due_to": "2026-11-20"})
        self.assertEqual(list(filtered_tasks(request, Task.objects.all())), [target])

    def test_filter_rejects_reversed_dates(self):
        # TC-LJC-018 · filtered_tasks()
        self.task()
        request = SimpleNamespace(query_params={
            "estimated_start_from": "2026-11-21", "estimated_start_to": "2026-11-20",
        })
        with self.assertRaises(ValidationError) as error:
            filtered_tasks(request, Task.objects.all())
        self.assertIn("estimated_start_to", error.exception.detail)

    def test_visible_approved_assignee(self):
        # TC-LJC-019 · visible_task()
        task = self.task()
        self.assertEqual(visible_task(SimpleNamespace(user=self.worker), task.pk), task)

    def test_visible_pending_approver(self):
        # TC-LJC-020 · visible_task()
        task = self.pending()
        self.assertEqual(visible_task(SimpleNamespace(user=self.manager), task.pk), task)

    def test_visible_denies_pending_and_unrelated(self):
        # TC-LJC-021 · visible_task()
        pending = self.pending()
        approved = self.task()
        for user, task_id in ((self.worker, pending.pk), (self.other_worker, approved.pk), (self.worker, 99999)):
            with self.subTest(user=user.username, task_id=task_id), self.assertRaises(NotFound):
                visible_task(SimpleNamespace(user=user), task_id)

    def test_pin_creates_no_side_effects(self):
        # TC-LJC-022 · TaskPinView.post()
        task = self.task()
        data = self.request(self.worker, "post", self.route(task, "pin/"), 201)
        self.assertEqual(data["id"], task.pk)
        self.assertTrue(TaskPin.objects.filter(user=self.worker, task=task).exists())
        self.assertTaskState(task, approval_status=APPROVED, completion_status=UNFINISHED)
        self.assertFalse(TaskNotification.objects.exists())
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_pin_repeat_and_cancelled(self):
        # TC-LJC-023 · TaskPinView.post()
        task = self.task(lifecycle_status=CANCELLED)
        for status in (201, 200):
            self.request(self.worker, "post", self.route(task, "pin/"), status)
        self.assertEqual(TaskPin.objects.filter(user=self.worker, task=task).count(), 1)

    def test_pin_rejects_readonly_pending(self):
        # TC-LJC-024 · TaskPinView.post()
        approved = self.task()
        pending = self.pending()
        self.request(self.manager, "post", self.route(approved, "pin/"), 403)
        self.request(self.worker, "post", self.route(pending, "pin/"), 403)
        self.assertFalse(TaskPin.objects.exists())

    def test_unpin_keeps_other_account(self):
        # TC-LJC-025 · TaskPinView.delete()
        task = self.task()
        TaskPin.objects.create(user=self.worker, task=task)
        TaskPin.objects.create(user=self.leader, task=task)
        self.assertEqual(self.request(self.worker, "delete", self.route(task, "pin/"), 200), {"pinned": False})
        self.assertEqual(list(TaskPin.objects.filter(task=task).values_list("user_id", flat=True)), [self.leader.pk])

    def test_unpin_absent_is_idempotent(self):
        # TC-LJC-026 · TaskPinView.delete()
        task = self.task()
        for _ in range(2):
            self.assertEqual(self.request(self.worker, "delete", self.route(task, "pin/"), 200), {"pinned": False})
        self.assertFalse(TaskPin.objects.exists())

    def test_unpin_rejects_body(self):
        # TC-LJC-027 · TaskPinView.delete()
        task = self.task()
        pin = TaskPin.objects.create(user=self.worker, task=task)
        self.request(self.worker, "delete", self.route(task, "pin/"), 400, user_id=self.other_worker.pk)
        self.assertTrue(TaskPin.objects.filter(pk=pin.pk).exists())

    def test_pinned_ignores_normal_filters(self):
        # TC-LJC-028 · PinnedTaskListView.get()
        completed = self.task(completion_status=COMPLETED)
        cancelled = self.task(lifecycle_status=CANCELLED)
        for task in (completed, cancelled):
            TaskPin.objects.create(user=self.worker, task=task)
        rows = self.results(self.worker, TASKS + "pinned/", completion_status=UNFINISHED)
        self.assertCountEqual([row["id"] for row in rows], [completed.pk, cancelled.pk])

    def test_pinned_empty_other_account(self):
        # TC-LJC-029 · PinnedTaskListView.get()
        task = self.task()
        TaskPin.objects.create(user=self.leader, task=task)
        data = self.request(self.worker, "get", TASKS + "pinned/", 200)
        self.assertEqual((data["count"], data["results"]), (0, []))

    def test_pinned_hides_stale(self):
        # TC-LJC-030 · PinnedTaskListView.get()
        pending = self.task()
        reassigned = self.task()
        for task in (pending, reassigned):
            TaskPin.objects.create(user=self.worker, task=task)
        Task.objects.filter(pk=pending.pk).update(approval_status=PENDING)
        Task.objects.filter(pk=reassigned.pk).update(assignee=self.other_worker)
        self.assertEqual(self.results(self.worker, TASKS + "pinned/"), [])


class MidtermTaskLifecycleTests(MidtermTaskFixtures):

    def test_edit_key_field_reopens_approval(self):
        # TC-LJC-031 · TaskDetailView.patch()
        task = self.pending()
        self.request(self.manager, "post", self.route(task, "approval/"), 200, decision=APPROVED)
        old_notice = task.notifications.get(notification_type=NOTICE.ASSIGNMENT_RECEIVED)
        new_due = DUE + timedelta(days=1)
        self.request(self.leader, "patch", self.route(task), 200, due_at=new_due.isoformat())
        self.assertTaskState(task, due_at=new_due, approval_status=PENDING)
        self.assertEqual(task.approval_records.latest("round").round, 2)
        old_notice.refresh_from_db()
        self.assertIsNotNone(old_notice.resolved_at)
        self.audit(self.leader, "update", task.pk)

    def test_edit_tag_only_preserves_approval(self):
        # TC-LJC-032 · TaskDetailView.patch()
        task = self.task()
        self.request(self.leader, "patch", self.route(task), 200, tag_ids=[self.tag.pk])
        self.assertTaskState(task, approval_status=APPROVED)
        self.assertEqual(task.tag_links.get().tag, self.tag)
        self.assertFalse(task.approval_records.exists())

    def test_edit_denies_assignee_and_closed(self):
        # TC-LJC-033 · TaskDetailView.patch()
        active = self.task()
        completed = self.task(completion_status=COMPLETED)
        cancelled = self.task(lifecycle_status=CANCELLED)
        for actor, task, status in (
            (self.worker, active, 403), (self.leader, completed, 400), (self.leader, cancelled, 400),
        ):
            with self.subTest(task=task.pk):
                self.request(actor, "patch", self.route(task), status, title="不得写入")
                self.assertTaskState(task, title="中期任务")
                self.assertFalse(task.changes.exists())

    def test_resubmit_after_edit(self):
        # TC-LJC-034 · TaskResubmitView.post()
        task = self.pending()
        self.request(self.manager, "post", self.route(task, "approval/"), 200, decision=RETURNED, comment="补充说明")
        self.request(self.leader, "patch", self.route(task), 200, description="补充说明")
        data = self.request(self.leader, "post", self.route(task, "resubmit/"), 200)
        self.assertEqual(data["approval_round"], 2)
        self.assertTaskState(task, approval_status=PENDING)
        self.assertTrue(task.notifications.filter(event_key=f"approval:{task.pk}:2", recipient=self.manager).exists())
        self.audit(self.leader, "resubmit", task.pk)

    def test_resubmit_before_edit(self):
        # TC-LJC-035 · TaskResubmitView.post()
        task = self.pending()
        self.request(self.manager, "post", self.route(task, "approval/"), 200, decision=RETURNED, comment="需修改")
        self.request(self.leader, "post", self.route(task, "resubmit/"), 400)
        self.assertTaskState(task, approval_status=RETURNED, returned_edit_required=True)
        self.assertEqual(task.approval_records.count(), 1)

    def test_resubmit_rejects_non_creator(self):
        # TC-LJC-036 · TaskResubmitView.post()
        task = self.task(approval=RETURNED)
        self.request(self.worker, "post", self.route(task, "resubmit/"), 403)
        self.assertTaskState(task, approval_status=RETURNED)
        self.assertFalse(task.approval_records.exists())

    def test_history_preserves_recorded_names(self):
        # TC-LJC-037 · TaskHistoryView.get()
        data = self.create(self.leader, self.worker)
        task = Task.objects.get(pk=data["id"])
        self.request(self.leader, "patch", self.route(task), 200, assignee_id=self.leader.pk, tag_ids=[self.tag.pk])
        User.objects.filter(pk=self.leader.pk).update(display_name="新组长名")
        Tag.objects.filter(pk=self.tag.pk).update(name="新标签名")
        changes = self.results(self.leader, self.route(task, "history/"))[-1]["changes"]
        self.assertEqual(changes["assignee_id"]["before"], {"id": self.worker.pk, "display_name": "研发员工"})
        self.assertEqual(changes["assignee_id"]["after"], {"id": self.leader.pk, "display_name": "研发组长"})
        self.assertEqual(changes["tag_ids"]["after"], [{"id": self.tag.pk, "name": "测试标签"}])

    def test_history_resolves_legacy_ids(self):
        # TC-LJC-038 · TaskHistoryView.get()
        task = self.task()
        TaskChangeHistory.objects.create(task=task, actor=self.leader, change_type="created", changes={
            "tag_ids": {"before": [], "after": [self.tag.pk]},
            "assignee_id": {"before": None, "after": self.worker.pk},
        })
        changes = self.results(self.leader, self.route(task, "history/"))[0]["changes"]
        self.assertEqual(changes["tag_ids"], {"before": [], "after": [{"id": self.tag.pk, "name": self.tag.name}]})
        self.assertEqual(changes["assignee_id"], {
            "before": None, "after": {"id": self.worker.pk, "display_name": self.worker.display_name},
        })

    def test_history_rejects_outsider(self):
        # TC-LJC-039 · TaskHistoryView.get()
        task = self.task()
        data = self.request(self.other_worker, "get", self.route(task, "history/"), 404)
        self.assertNotIn("results", data)

    def test_round_increments_and_notifies(self):
        # TC-LJC-040 · create_approval_round()
        task = self.pending()
        round_two = create_approval_round(task)
        self.assertEqual((round_two.round, round_two.approver), (2, self.manager))
        notice = task.notifications.get(event_key=f"approval:{task.pk}:2")
        self.assertEqual((notice.recipient, notice.notification_type), (self.manager, NOTICE.APPROVAL_REQUIRED))

    def test_round_starts_at_one(self):
        # TC-LJC-041 · create_approval_round()
        task = self.task(approval=PENDING)
        row = create_approval_round(task)
        self.assertEqual((row.round, row.decision), (1, PENDING))
        self.assertEqual(task.notifications.count(), 1)

    def test_round_unavailable_manager(self):
        # TC-LJC-042 · create_approval_round()
        task = self.task(approval=PENDING)
        User.objects.filter(pk=self.manager.pk).update(is_active=False)
        task.refresh_from_db()
        with self.assertRaises(ValidationError):
            create_approval_round(task)
        self.assertFalse(task.approval_records.exists())
        self.assertFalse(task.notifications.exists())

    def test_inbox_current_approver_only(self):
        # TC-LJC-043 · ApprovalInboxView.get()
        own = self.pending()
        create_approval_round(self.task(self.other_leader, self.other_worker, PENDING))
        rows = self.results(self.manager, INBOX)
        self.assertEqual([(row["task_id"], row["approver"]["id"]) for row in rows], [(own.pk, self.manager.pk)])

    def test_inbox_excludes_cancelled_and_handled(self):
        # TC-LJC-044 · ApprovalInboxView.get()
        cancelled = self.pending()
        handled = self.pending()
        self.request(self.leader, "post", self.route(cancelled, "cancel/"), 200, reason="取消")
        self.request(self.manager, "post", self.route(handled, "approval/"), 200, decision=APPROVED)
        data = self.request(self.manager, "get", INBOX, 200)
        self.assertEqual((data["count"], data["results"]), (0, []))

    def test_inbox_rejects_anonymous(self):
        # TC-LJC-045 · ApprovalInboxView.get()
        self.request(None, "get", INBOX, 401)

    def test_outbox_own_pending_returned(self):
        # TC-LJC-046 · ApprovalOutboxView.get()
        pending = self.pending()
        returned = self.pending()
        self.request(self.manager, "post", self.route(returned, "approval/"), 200, decision=RETURNED, comment="需补充")
        create_approval_round(self.task(self.other_leader, self.other_worker, PENDING))
        rows = self.results(self.leader, OUTBOX)
        self.assertCountEqual([(row["task_id"], row["approval_status"]) for row in rows],
                              [(pending.pk, PENDING), (returned.pk, RETURNED)])

    def test_outbox_excludes_approved_and_cancelled(self):
        # TC-LJC-047 · ApprovalOutboxView.get()
        approved = self.pending()
        cancelled = self.pending()
        self.request(self.manager, "post", self.route(approved, "approval/"), 200, decision=APPROVED)
        self.request(self.leader, "post", self.route(cancelled, "cancel/"), 200, reason="取消")
        data = self.request(self.leader, "get", OUTBOX, 200)
        self.assertEqual((data["count"], data["results"]), (0, []))

    def test_outbox_rejects_anonymous(self):
        # TC-LJC-048 · ApprovalOutboxView.get()
        self.request(None, "get", OUTBOX, 401)

    def test_approval_accepts_and_dispatches(self):
        # TC-LJC-049 · TaskApprovalView.post()
        task = self.pending()
        data = self.request(self.manager, "post", self.route(task, "approval/"), 200, decision=APPROVED)
        self.assertEqual(data["decision"], APPROVED)
        self.assertTaskState(task, approval_status=APPROVED)
        self.assertIsNotNone(task.approval_records.get().decided_at)
        self.assertIsNotNone(task.notifications.get(notification_type=NOTICE.APPROVAL_REQUIRED).resolved_at)
        self.assertEqual(task.notifications.get(notification_type=NOTICE.APPROVAL_APPROVED).recipient, self.leader)
        self.assertEqual(task.notifications.get(notification_type=NOTICE.ASSIGNMENT_RECEIVED).recipient, self.worker)
        self.audit(self.manager, "approve", task.pk)

    def test_approval_returns_with_reason(self):
        # TC-LJC-050 · TaskApprovalView.post()
        task = self.pending()
        self.request(self.manager, "post", self.route(task, "approval/"), 200, decision=RETURNED, comment="改")
        self.assertTaskState(task, approval_status=RETURNED, returned_edit_required=True)
        notice = task.notifications.get(notification_type=NOTICE.APPROVAL_RETURNED, recipient=self.leader)
        self.assertEqual((notification_data(notice)["approval_round"], notification_data(notice)["summary"]), (1, "改"))
        self.assertFalse(task.notifications.filter(notification_type=NOTICE.ASSIGNMENT_RECEIVED).exists())

    def test_approval_denies_wrong_actor_and_repeat(self):
        # TC-LJC-051 · TaskApprovalView.post()
        task = self.pending()
        url = self.route(task, "approval/")
        self.request(self.founder, "post", url, 403, decision=APPROVED)
        self.assertTaskState(task, approval_status=PENDING)
        self.request(self.manager, "post", url, 200, decision=APPROVED)
        self.request(self.manager, "post", url, 400, decision=APPROVED)
        self.assertEqual(task.notifications.filter(notification_type=NOTICE.APPROVAL_APPROVED).count(), 1)

    def test_executable_approved_active(self):
        # TC-LJC-052 · require_executable()
        self.assertIsNone(require_executable(self.task(), self.worker))

    def test_executable_closed_or_pending(self):
        # TC-LJC-053 · require_executable()
        for fields in ({"approval": PENDING}, {"completion_status": COMPLETED}, {"lifecycle_status": CANCELLED}):
            with self.subTest(fields=fields), self.assertRaises(ValidationError):
                require_executable(self.task(**fields), self.worker)

    def test_executable_denies_non_owner(self):
        # TC-LJC-054 · require_executable()
        with self.assertRaises(PermissionDenied):
            require_executable(self.task(), self.leader)

    def test_progress_appends_without_completing(self):
        # TC-LJC-055 · TaskUpdateView.post()
        task = self.task()
        url = self.route(task, "updates/")
        self.request(self.worker, "post", url, 201, content="第一项")
        data = self.request(self.worker, "post", url, 201, content="第二项")
        self.assertEqual(list(task.updates.values_list("content", flat=True)), ["第一项", "第二项"])
        self.assertEqual(data["author_id"], self.worker.pk)
        self.assertTaskState(task, completion_status=UNFINISHED)
        self.audit(self.worker, "progress", data["id"])

    def test_progress_one_char_trimmed(self):
        # TC-LJC-056 · TaskUpdateView.post()
        task = self.task()
        data = self.request(self.worker, "post", self.route(task, "updates/"), 201, content="  好  ")
        self.assertEqual(data["content"], "好")
        self.assertEqual(task.updates.get().content, "好")

    def test_progress_rejects_blank_and_non_owner(self):
        # TC-LJC-057 · TaskUpdateView.post()
        task = self.task()
        url = self.route(task, "updates/")
        self.request(self.worker, "post", url, 400, content="  ")
        self.request(self.leader, "post", url, 403, content="无权提交")
        self.assertFalse(task.updates.exists())
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_submit_waits_and_notifies_creator(self):
        # TC-LJC-058 · TaskSubmissionView.post()
        task = self.task(required_materials="测试材料")
        old = self.notice(task)
        returned = self.notice(task, kind=NOTICE.SUBMISSION_RETURNED)
        data = self.request(self.worker, "post", self.route(task, "submissions/"), 201,
                            completion_note="已完成", materials_note="共享目录")
        self.assertEqual(data["review_status"], "waiting")
        self.assertEqual(task.submissions.get().materials_note, "共享目录")
        self.assertTaskState(task, completion_status=UNFINISHED)
        notice = task.notifications.get(notification_type=NOTICE.SUBMISSION_RECEIVED)
        self.assertEqual((notice.recipient, notice.submission_id), (self.leader, data["id"]))
        for old_notice in (old, returned):
            old_notice.refresh_from_db()
            self.assertIsNotNone(old_notice.resolved_at)
        self.audit(self.worker, "submit", data["id"])

    def test_submit_self_completes(self):
        # TC-LJC-059 · TaskSubmissionView.post()
        task = self.task(self.worker, self.worker)
        data = self.request(self.worker, "post", self.route(task, "submissions/"), 201, completion_note="好")
        self.assertEqual(data["review_status"], "self_completed")
        self.assertTaskState(task, completion_status=COMPLETED)
        self.assertIsNotNone(task.completed_at)
        self.assertFalse(task.notifications.exists())

    def test_submit_rejects_missing_material_and_duplicate(self):
        # TC-LJC-060 · TaskSubmissionView.post()
        task = self.task(required_materials="材料")
        url = self.route(task, "submissions/")
        self.request(self.worker, "post", url, 400, completion_note="已完成")
        self.assertFalse(task.submissions.exists())
        self.request(self.worker, "post", url, 201, completion_note="已完成", materials_note="材料位置")
        self.request(self.worker, "post", url, 400, completion_note="重复提交", materials_note="材料位置")
        self.assertEqual(task.submissions.count(), 1)
        self.assertEqual(task.notifications.filter(notification_type=NOTICE.SUBMISSION_RECEIVED).count(), 1)

    def test_review_accepts_and_resolves_notice(self):
        # TC-LJC-061 · TaskReviewView.post()
        task = self.task()
        row = self.submission(task)
        waiting = self.notice(task, self.leader, NOTICE.SUBMISSION_RECEIVED, submission=row)
        data = self.request(self.leader, "post", self.route(task, f"submissions/{row.pk}/review/"), 200, decision="accepted")
        row.refresh_from_db()
        waiting.refresh_from_db()
        self.assertEqual((row.review_status, row.reviewed_by), ("accepted", self.leader))
        self.assertEqual(data["task_completion_status"], COMPLETED)
        self.assertTaskState(task, completion_status=COMPLETED, completed_at=row.reviewed_at)
        self.assertIsNotNone(waiting.resolved_at)
        self.assertEqual(task.notifications.get(notification_type=NOTICE.SUBMISSION_ACCEPTED).recipient, self.worker)
        self.audit(self.leader, "accept", row.pk)

    def test_review_returns_then_allows_new_submission(self):
        # TC-LJC-062 · TaskReviewView.post()
        task = self.task()
        first = self.submission(task)
        self.request(self.leader, "post", self.route(task, f"submissions/{first.pk}/review/"), 200,
                     decision="returned", comment="改")
        first.refresh_from_db()
        self.assertEqual((first.review_status, first.review_comment), ("returned", "改"))
        self.assertTaskState(task, completion_status=UNFINISHED)
        old = task.notifications.get(notification_type=NOTICE.SUBMISSION_RETURNED)
        self.assertEqual(notification_data(old)["summary"], "改")
        data = self.request(self.worker, "post", self.route(task, "submissions/"), 201, completion_note="新版")
        old.refresh_from_db()
        self.assertIsNotNone(old.resolved_at)
        self.assertNotEqual(data["id"], first.pk)
        self.assertEqual(task.submissions.count(), 2)

    def test_review_denies_manager_and_second_decision(self):
        # TC-LJC-063 · TaskReviewView.post()
        task = self.task()
        row = self.submission(task)
        url = self.route(task, f"submissions/{row.pk}/review/")
        self.request(self.manager, "post", url, 403, decision="accepted")
        row.refresh_from_db()
        self.assertEqual(row.review_status, "waiting")
        self.request(self.leader, "post", url, 200, decision="returned", comment="补材料")
        self.request(self.leader, "post", url, 400, decision="accepted")
        self.assertEqual(task.notifications.count(), 1)

    def test_cancel_approved_notifies_assignee(self):
        # TC-LJC-064 · TaskCancelView.post()
        task = self.task()
        old = self.notice(task)
        self.request(self.leader, "post", self.route(task, "cancel/"), 200, reason="计划调整")
        self.assertTaskState(task, lifecycle_status=CANCELLED, cancellation_reason="计划调整")
        self.assertIsNotNone(task.cancelled_at)
        old.refresh_from_db()
        self.assertIsNotNone(old.resolved_at)
        notice = task.notifications.get(notification_type=NOTICE.TASK_CANCELLED)
        self.assertEqual((notice.recipient, notification_data(notice)["summary"]), (self.worker, "计划调整"))
        self.audit(self.leader, "cancel", task.pk)

    def test_cancel_pending_notifies_approver(self):
        # TC-LJC-065 · TaskCancelView.post()
        task = self.pending()
        self.request(self.leader, "post", self.route(task, "cancel/"), 200, reason="计划调整")
        self.assertIsNotNone(task.notifications.get(notification_type=NOTICE.APPROVAL_REQUIRED).resolved_at)
        self.assertEqual(task.notifications.get(notification_type=NOTICE.TASK_CANCELLED).recipient, self.manager)
        self.assertFalse(task.notifications.filter(recipient=self.worker).exists())

    def test_cancel_denies_non_creator_closed_and_repeat(self):
        # TC-LJC-066 · TaskCancelView.post()
        active = self.task()
        completed = self.task(completion_status=COMPLETED)
        cancelled = self.task(lifecycle_status=CANCELLED)
        for actor, task, status, lifecycle in (
            (self.worker, active, 403, ACTIVE),
            (self.leader, completed, 400, ACTIVE),
            (self.leader, cancelled, 400, CANCELLED),
        ):
            with self.subTest(task=task.pk):
                self.request(actor, "post", self.route(task, "cancel/"), status, reason="取消")
                self.assertTaskState(task, lifecycle_status=lifecycle)
        self.assertFalse(TaskNotification.objects.exists())
        self.assertFalse(AppOperationAuditLog.objects.exists())


class MidtermTaskNotificationTests(MidtermTaskFixtures):

    def test_notice_creation_links_submission(self):
        # TC-LJC-067 · create_task_notification()
        task = self.task()
        row = self.submission(task)
        notice = create_task_notification(task, self.leader, NOTICE.SUBMISSION_RECEIVED, "test:submit", row)
        self.assertEqual((notice.recipient, notice.task, notice.submission), (self.leader, task, row))
        self.assertFalse(notice.is_read)

    def test_notice_creation_deduplicates(self):
        # TC-LJC-068 · create_task_notification()
        task = self.task()
        first = create_task_notification(task, self.worker, NOTICE.ASSIGNMENT_RECEIVED, "test:assignment")
        second = create_task_notification(task, self.worker, NOTICE.ASSIGNMENT_RECEIVED, "test:assignment")
        self.assertEqual(first.pk, second.pk)
        self.assertEqual(TaskNotification.objects.count(), 1)

    def test_notice_without_recipient(self):
        # TC-LJC-069 · create_task_notification()
        self.assertIsNone(create_task_notification(self.task(), None, NOTICE.ASSIGNMENT_RECEIVED, "test:none"))
        self.assertFalse(TaskNotification.objects.exists())

    def test_resolve_scopes_type_and_submission(self):
        # TC-LJC-070 · resolve_task_notifications()
        task = self.task()
        first, second = self.submission(task), self.submission(task)
        target = self.notice(task, self.leader, NOTICE.SUBMISSION_RECEIVED, submission=first)
        other = self.notice(task, self.leader, NOTICE.SUBMISSION_RECEIVED, submission=second)
        different = self.notice(task, self.worker, NOTICE.SUBMISSION_RETURNED, submission=first)
        self.assertEqual(resolve_task_notifications(task, [NOTICE.SUBMISSION_RECEIVED], first), 1)
        target.refresh_from_db()
        self.assertIsNotNone(target.resolved_at)
        self.assertFalse(target.is_read)
        for notice in (other, different):
            notice.refresh_from_db()
            self.assertIsNone(notice.resolved_at)

    def test_resolve_keeps_existing_timestamp(self):
        # TC-LJC-071 · resolve_task_notifications()
        task = self.task()
        notice = self.notice(task, resolved_at=DUE)
        self.assertEqual(resolve_task_notifications(task), 0)
        notice.refresh_from_db()
        self.assertEqual(notice.resolved_at, DUE)

    def test_resolve_does_not_touch_other_task(self):
        # TC-LJC-072 · resolve_task_notifications()
        unrelated = self.notice(self.task())
        self.assertEqual(resolve_task_notifications(self.task(), [NOTICE.ASSIGNMENT_RECEIVED]), 0)
        unrelated.refresh_from_db()
        self.assertIsNone(unrelated.resolved_at)

    def test_dispatch_notifies_assignee(self):
        # TC-LJC-073 · send_assignment()
        task = self.task()
        send_assignment(task, 17)
        notice = task.notifications.get()
        self.assertEqual((notice.recipient, notice.notification_type, notice.event_key),
                         (self.worker, NOTICE.ASSIGNMENT_RECEIVED, f"assignment:{task.pk}:17:{self.worker.pk}"))

    def test_dispatch_default_and_repeat(self):
        # TC-LJC-074 · send_assignment()
        task = self.task(self.founder, self.worker)
        send_assignment(task)
        send_assignment(task)
        self.assertEqual(task.notifications.get().event_key, f"assignment:{task.pk}:0:{self.worker.pk}")

    def test_dispatch_suppresses_self_assignment(self):
        # TC-LJC-075 · send_assignment()
        task = self.task(self.worker, self.worker)
        send_assignment(task)
        self.assertFalse(task.notifications.exists())

    def test_notice_data_keeps_original_round(self):
        # TC-LJC-076 · notification_data()
        task = self.pending()
        self.request(self.manager, "post", self.route(task, "approval/"), 200, decision=RETURNED, comment="第 1 轮缺说明")
        self.request(self.leader, "patch", self.route(task), 200, description="已补")
        self.request(self.leader, "post", self.route(task, "resubmit/"), 200)
        notice = TaskNotification.objects.get(task=task, notification_type=NOTICE.APPROVAL_RETURNED)
        data = notification_data(notice)
        self.assertEqual((data["approval_round"], data["approval_decision"], data["summary"], data["task_approval_status"]),
                         (1, RETURNED, "第 1 轮缺说明", PENDING))

    def test_notice_data_without_approval_context(self):
        # TC-LJC-077 · notification_data()
        task = self.task()
        data = notification_data(self.notice(task))
        self.assertEqual((data["task_id"], data["approval_round"], data["approval_decision"], data["summary"]),
                         (task.pk, None, None, ""))

    def test_notice_data_tolerates_bad_round(self):
        # TC-LJC-078 · notification_data()
        task = self.task()
        notice = self.notice(task, self.leader, NOTICE.APPROVAL_REQUIRED, event_key=f"approval:{task.pk}:bad")
        data = notification_data(notice)
        self.assertEqual((data["id"], data["approval_round"], data["approval_decision"]), (notice.pk, None, None))

    def test_notices_history_read_has_no_side_effects(self):
        # TC-LJC-079 · NotificationListView.get()
        task = self.task()
        unread = self.notice(task)
        resolved = self.notice(task, resolved_at=DUE)
        self.notice(task, self.leader)
        rows = self.results(self.worker, NOTICES)
        self.assertCountEqual([row["id"] for row in rows], [unread.pk, resolved.pk])
        self.assertFalse(TaskNotification.objects.filter(pk__in=[unread.pk, resolved.pk], is_read=True).exists())
        self.assertFalse(TaskNotification.objects.filter(read_at__isnull=False).exists())

    def test_notices_unread_filters_resolved_and_read(self):
        # TC-LJC-080 · NotificationListView.get()
        task = self.task()
        target = self.notice(task)
        self.notice(task, resolved_at=DUE)
        self.notice(task, is_read=True, read_at=DUE)
        data = self.request(self.worker, "get", NOTICES, 200, unread_only="true", page_size=1)
        self.assertEqual((data["count"], data["next"], [row["id"] for row in data["results"]]), (1, None, [target.pk]))

    def test_notices_reject_invalid_unread_filter(self):
        # TC-LJC-081 · NotificationListView.get()
        self.assertIn("unread_only", self.request(self.worker, "get", NOTICES, 400, unread_only="yes"))

    def test_notice_count_counts_only_active_unread(self):
        # TC-LJC-082 · NotificationUnreadCountView.get()
        task = self.task()
        self.notice(task)
        self.notice(task)
        self.notice(task, is_read=True, read_at=DUE)
        self.notice(task, resolved_at=DUE)
        self.notice(task, self.leader)
        self.assertEqual(self.request(self.worker, "get", NOTICES + "unread-count/", 200), {"unread_count": 2})

    def test_notice_count_zero_after_resolving(self):
        # TC-LJC-083 · NotificationUnreadCountView.get()
        task = self.task()
        notice = self.notice(task)
        resolve_task_notifications(task)
        self.assertEqual(self.request(self.worker, "get", NOTICES + "unread-count/", 200), {"unread_count": 0})
        notice.refresh_from_db()
        self.assertFalse(notice.is_read)

    def test_notice_count_rejects_anonymous(self):
        # TC-LJC-084 · NotificationUnreadCountView.get()
        self.notice(self.task())
        self.assertNotIn("unread_count", self.request(None, "get", NOTICES + "unread-count/", 401))

    def test_mark_read_changes_only_requested_owned(self):
        # TC-LJC-085 · NotificationMarkReadView.post()
        task = self.task()
        selected, omitted, foreign = self.notice(task), self.notice(task), self.notice(task, self.leader)
        data = self.request(self.worker, "post", NOTICES + "mark-read/", 200, notification_ids=[selected.pk, foreign.pk])
        self.assertEqual(data["marked_read_count"], 1)
        selected.refresh_from_db()
        self.assertTrue(selected.is_read)
        self.assertIsNotNone(selected.read_at)
        for notice in (omitted, foreign):
            notice.refresh_from_db()
            self.assertFalse(notice.is_read)
            self.assertIsNone(notice.read_at)

    def test_mark_read_empty_duplicate_and_repeat(self):
        # TC-LJC-086 · NotificationMarkReadView.post()
        notice = self.notice(self.task())
        url = NOTICES + "mark-read/"
        for ids, expected in (([], 0), ([notice.pk, notice.pk], 1)):
            self.assertEqual(self.request(self.worker, "post", url, 200, notification_ids=ids)["marked_read_count"], expected)
        notice.refresh_from_db()
        first_read_at = notice.read_at
        self.assertIsNotNone(first_read_at)
        self.assertEqual(self.request(self.worker, "post", url, 200, notification_ids=[notice.pk])["marked_read_count"], 0)
        notice.refresh_from_db()
        self.assertEqual(notice.read_at, first_read_at)

    def test_mark_read_rejects_invalid_id_atomically(self):
        # TC-LJC-087 · NotificationMarkReadView.post()
        notice = self.notice(self.task())
        data = self.request(self.worker, "post", NOTICES + "mark-read/", 400, notification_ids=[notice.pk, 0])
        self.assertIn("notification_ids", data)
        notice.refresh_from_db()
        self.assertFalse(notice.is_read)
        self.assertIsNone(notice.read_at)

    def test_home_count_matches_notification_endpoint(self):
        # TC-LJC-088 · unread_notification_count()
        task = self.task()
        self.notice(task)
        self.notice(task)
        self.notice(task, is_read=True, read_at=DUE)
        self.notice(task, resolved_at=DUE)
        self.assertEqual(unread_notification_count(self.worker), 2)
        self.assertEqual(self.request(self.worker, "get", NOTICES + "unread-count/", 200)["unread_count"], 2)

    def test_home_count_zero_for_empty_account(self):
        # TC-LJC-089 · unread_notification_count()
        self.assertEqual(unread_notification_count(self.worker), 0)

    def test_home_count_excludes_foreign_and_resolved(self):
        # TC-LJC-090 · unread_notification_count()
        task = self.task()
        self.notice(task, resolved_at=DUE)
        self.notice(task, self.leader)
        self.assertEqual(unread_notification_count(self.worker), 0)
        self.assertEqual(unread_notification_count(self.leader), 1)
