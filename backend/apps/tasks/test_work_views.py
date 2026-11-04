from datetime import datetime, timedelta, timezone as dt_timezone

from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from apps.accounts.models import Department, Team, User
from apps.tags.models import Tag, TaskTag

from .models import Task, TaskApprovalRecord, TaskChangeHistory, TaskSubmission, TaskUpdate


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class WorkViewTests(APITestCase):
    def setUp(self):
        self.department = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="后端", department=self.department)
        self.founder = self.user("founder", User.Role.FOUNDER)
        self.manager = self.user("manager", User.Role.DEPARTMENT_MANAGER, self.founder)
        self.leader = self.user("leader", User.Role.TEAM_LEADER, self.manager)
        self.worker = self.user("worker", User.Role.EMPLOYEE, self.leader)
        self.outsider = self.manager

    def user(self, name, role, manager=None):
        return User.objects.create_user(
            username=name, password="password", display_name=name,
            account_type=User.AccountType.BUSINESS, business_role=role,
            department=self.department if manager else None,
            team=self.team if role in (User.Role.TEAM_LEADER, User.Role.EMPLOYEE) else None,
            manager=manager,
        )

    def auth(self, user):
        token = AccessToken.for_user(user)
        token["account_type"] = user.account_type
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def task(self, creator, assignee, start, due, approval="approved", title="任务"):
        task = Task.objects.create(
            creator=creator, assignee=assignee, title=title,
            description="不得出现在视图中", estimated_start_at=start, due_at=due,
            approval_status=approval,
        )
        return task

    @staticmethod
    def stamp(day, hour=9):
        return datetime(day.year, day.month, day.day, hour, tzinfo=dt_timezone(timedelta(hours=8)))

    def test_month_planned_and_original_start_overflow_and_color(self):
        today = timezone.localdate()
        first = today.replace(day=1)
        second = first + timedelta(days=1)
        tag = Tag.objects.create(name="优先", color="#C7DDFF", is_active=False)
        task = self.task(self.founder, self.worker, self.stamp(second), self.stamp(second + timedelta(days=2)), title="计划开始")
        TaskTag.objects.create(task=task, tag=tag)
        fallback = self.task(self.founder, self.worker, None, self.stamp(second + timedelta(days=2)), title="原始创建")
        Task.objects.filter(pk=fallback.pk).update(created_at=self.stamp(first))
        for number in range(3):
            extra = self.task(self.founder, self.worker, None, self.stamp(second + timedelta(days=1)), title=f"溢出{number}")
            Task.objects.filter(pk=extra.pk).update(created_at=self.stamp(second))
        self.task(self.founder, self.worker, self.stamp(second), self.stamp(second), approval="pending", title="待审批")

        self.auth(self.leader)
        response = self.client.get(f"/api/v1/app/work-views/month/?user_id={self.worker.pk}&year={today.year}&month={today.month}")
        self.assertEqual(response.status_code, 200, response.data)
        days = {row["date"].isoformat(): row for row in response.data["data"]["days"]}
        self.assertEqual([row["task_id"] for row in days[first.isoformat()]["bars"]], [fallback.pk])
        self.assertEqual(days[second.isoformat()]["overflow_count"], 2)
        self.assertEqual(days[second.isoformat()]["bars"][0]["bar_color"], "#D1D5DB")
        self.assertEqual(next(row["bar_color"] for row in days[second.isoformat()]["bars"] if row["task_id"] == task.pk), tag.color)
        self.assertNotIn("description", str(response.data))

    def test_actor_events_visibility_and_current_only_all_day(self):
        today = timezone.localdate()
        task = self.task(self.leader, self.worker, None, self.stamp(today + timedelta(days=4)))
        created = TaskChangeHistory.objects.create(task=task, actor=self.leader,
            change_type=TaskChangeHistory.Change.CREATED, changes={})
        TaskChangeHistory.objects.filter(pk=created.pk).update(created_at=self.stamp(today, 8))
        approval = TaskApprovalRecord.objects.create(task=task, round=1, approver=self.founder,
            decision=TaskApprovalRecord.Decision.APPROVED, decided_at=self.stamp(today, 9))
        TaskApprovalRecord.objects.filter(pk=approval.pk).update(created_at=self.stamp(today, 8))
        update = TaskUpdate.objects.create(task=task, author=self.worker, content="保密进展")
        TaskUpdate.objects.filter(pk=update.pk).update(created_at=self.stamp(today, 10))
        submission = TaskSubmission.objects.create(task=task, submitted_by=self.worker,
            completion_note="保密成果", review_status=TaskSubmission.Review.WAITING)
        TaskSubmission.objects.filter(pk=submission.pk).update(submitted_at=self.stamp(today, 11))

        self.auth(self.worker)
        own = self.client.get(f"/api/v1/app/work-views/day/?date={today}")
        self.assertEqual(own.status_code, 200, own.data)
        self.assertEqual([e["event_type"] for e in own.data["data"]["events"]], ["progress_updated", "completion_submitted"])
        self.assertEqual([row["task_id"] for row in own.data["data"]["all_day_tasks"]], [task.pk])
        self.assertNotIn("保密", str(own.data))
        self.assertEqual(self.client.get(f"/api/v1/app/work-views/day/?date={today - timedelta(days=1)}").data["data"]["all_day_tasks"], [])

        self.auth(self.leader)
        leader = self.client.get(f"/api/v1/app/work-views/week/?date={today}")
        self.assertEqual([e["event_type"] for e in leader.data["data"]["events"]], ["created_and_submitted"])
        subordinate = self.client.get(f"/api/v1/app/work-views/day/?date={today}&user_id={self.worker.pk}")
        self.assertEqual([e["event_type"] for e in subordinate.data["data"]["events"]], ["progress_updated", "completion_submitted"])
        self.assertEqual(len(leader.data["data"]["dates"]), 7)

        self.auth(self.founder)
        approver = self.client.get(f"/api/v1/app/work-views/day/?date={today}")
        self.assertEqual([e["event_type"] for e in approver.data["data"]["events"]], ["approval_approved"])

    def test_permission_date_validation_and_pending_bar(self):
        today = timezone.localdate()
        pending = self.task(self.leader, self.worker, None, self.stamp(today + timedelta(days=2)), approval="pending")
        self.auth(self.worker)
        self.assertEqual(self.client.get(f"/api/v1/app/work-views/day/?date={today}").data["data"]["all_day_tasks"], [])
        self.assertEqual(self.client.get("/api/v1/app/work-views/day/?date=bad").status_code, 400)
        self.assertEqual(self.client.get("/api/v1/app/work-views/month/?year=2026&month=13").status_code, 400)
        self.assertEqual(self.client.get(f"/api/v1/app/work-views/day/?user_id={self.leader.pk}").status_code, 403)
        self.auth(self.leader)
        profile = self.client.get(f"/api/v1/app/organization/members/{self.worker.pk}/")
        self.assertTrue(profile.data["data"]["can_view_work_views"])
        self.assertEqual(self.client.get(f"/api/v1/app/work-views/day/?user_id={self.outsider.pk}").status_code, 403)
        self.auth(self.worker)
        profile = self.client.get(f"/api/v1/app/organization/members/{self.leader.pk}/")
        self.assertFalse(profile.data["data"]["can_view_work_views"])

    def test_resubmit_acceptance_self_completion_and_hidden_event(self):
        today = timezone.localdate()
        task = self.task(self.leader, self.worker, None, self.stamp(today + timedelta(days=3)))
        change = TaskChangeHistory.objects.create(task=task, actor=self.leader,
            change_type=TaskChangeHistory.Change.UPDATED, changes={"title": {"before": "旧", "after": "新"}})
        TaskChangeHistory.objects.filter(pk=change.pk).update(created_at=self.stamp(today, 8))
        round_two = TaskApprovalRecord.objects.create(task=task, round=2, approver=self.manager)
        TaskApprovalRecord.objects.filter(pk=round_two.pk).update(created_at=self.stamp(today, 9))
        submission = TaskSubmission.objects.create(task=task, submitted_by=self.worker,
            completion_note="敏感成果", review_status=TaskSubmission.Review.ACCEPTED,
            reviewed_by=self.leader, reviewed_at=self.stamp(today, 11))
        TaskSubmission.objects.filter(pk=submission.pk).update(submitted_at=self.stamp(today, 10))
        own = self.task(self.leader, self.leader, None, self.stamp(today + timedelta(days=3)))
        self_done = TaskSubmission.objects.create(task=own, submitted_by=self.leader,
            completion_note="仅一条", review_status=TaskSubmission.Review.SELF_COMPLETED)
        TaskSubmission.objects.filter(pk=self_done.pk).update(submitted_at=self.stamp(today, 12))

        self.auth(self.leader)
        response = self.client.get(f"/api/v1/app/work-views/day/?date={today}")
        self.assertEqual([e["event_type"] for e in response.data["data"]["events"]],
                         ["updated", "resubmitted", "acceptance_approved", "submitted_and_completed"])
        self.assertNotIn("敏感成果", str(response.data))
        self.auth(self.worker)
        self.assertEqual([e["event_type"] for e in self.client.get(f"/api/v1/app/work-views/day/?date={today}").data["data"]["events"]],
                         ["completion_submitted"])

        # A creator's operation on an approved task assigned outside this viewer's
        # existing detail scope must not become visible through the manager view.
        other = self.task(self.founder, self.manager, None, self.stamp(today + timedelta(days=3)))
        hidden = TaskUpdate.objects.create(task=other, author=self.worker, content="越权")
        TaskUpdate.objects.filter(pk=hidden.pk).update(created_at=self.stamp(today, 13))
        self.auth(self.leader)
        scoped = self.client.get(f"/api/v1/app/work-views/day/?date={today}&user_id={self.worker.pk}")
        self.assertNotIn(other.pk, [e["task_id"] for e in scoped.data["data"]["events"]])
