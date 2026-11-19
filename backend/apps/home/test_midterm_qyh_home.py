from datetime import date, datetime, timedelta, timezone
from unittest.mock import patch

from rest_framework.test import APITestCase

from apps.accounts.models import Department, User
from apps.content.models import CompanyImportantItem
from apps.logs.models import DailyLog
from apps.tasks.models import Task, TaskNotification

from .models import HomePersonalDailyItem
from .services import candidate_tasks, company_top_tasks, home_payload, ordered_candidates


class HomeMidtermTests(APITestCase):
    now = datetime(2026, 10, 28, 2, tzinfo=timezone.utc)

    @classmethod
    def setUpTestData(cls):
        cls.viewer = User.objects.create(username="viewer", business_role=User.Role.FOUNDER)
        cls.other = User.objects.create(
            username="other", business_role=User.Role.DEPARTMENT_MANAGER,
            department=Department.objects.create(name="研发"), manager=cls.viewer,
        )

    def task(self, title, *, due=1, creator=None, assignee=None, **state):
        return Task.objects.create(
            creator=creator or self.other,
            assignee=assignee or self.viewer,
            title=title,
            due_at=self.now + timedelta(days=due),
            approval_status=state.pop("approval_status", Task.Approval.APPROVED),
            **state,
        )

    def notification(self, key, task, **fields):
        return TaskNotification.objects.create(
            recipient=fields.pop("recipient", self.viewer),
            task=task,
            notification_type=TaskNotification.Type.ASSIGNMENT_RECEIVED,
            event_key=key,
            **fields,
        )

    def test_candidates_include_both_origins(self):
        assigned = self.task("派发")
        created = self.task("自建", creator=self.viewer)
        self.assertCountEqual(candidate_tasks(self.viewer), [assigned, created])

    def test_candidates_only_one_eligible(self):
        eligible = self.task("唯一有效")
        self.task("退回", approval_status=Task.Approval.RETURNED)
        self.assertEqual(list(candidate_tasks(self.viewer)), [eligible])

    def test_candidates_exclude_other_and_invalid_states(self):
        self.task("派给他人", creator=self.viewer, assignee=self.other)
        self.task("待审批", approval_status=Task.Approval.PENDING)
        self.task("已取消", lifecycle_status=Task.Lifecycle.CANCELLED)
        self.task("已完成", completion_status=Task.Completion.COMPLETED)
        self.assertEqual(list(candidate_tasks(self.viewer)), [])

    def test_candidate_order_source_then_due(self):
        own = self.task("最早自建", due=1, creator=self.viewer)
        later = self.task("晚派发", due=8)
        earlier = self.task("早派发", due=4)
        self.assertEqual(list(ordered_candidates(self.viewer)), [earlier, later, own])

    def test_candidate_order_uses_creation_time_on_equal_due(self):
        newer = self.task("较新")
        older = self.task("较旧")
        Task.objects.filter(pk=newer.pk).update(created_at=self.now)
        Task.objects.filter(pk=older.pk).update(created_at=self.now - timedelta(minutes=1))
        self.assertEqual(list(ordered_candidates(self.viewer)), [older, newer])

    def test_candidate_order_ignores_better_ranked_ineligible_rows(self):
        valid = self.task("自建", creator=self.viewer)
        self.task("待审批", due=0, approval_status=Task.Approval.PENDING)
        self.assertEqual(list(ordered_candidates(self.viewer)), [valid])

    def test_top_tasks_keeps_order_below_limit(self):
        second = self.task("第二", due=2)
        first = self.task("第一", due=1)
        self.assertEqual(list(company_top_tasks(self.viewer)), [first, second])

    def test_top_tasks_exact_and_over_default_limit(self):
        initial = [self.task(f"任务{index}", due=index) for index in range(1, 11)]
        self.assertEqual(list(company_top_tasks(self.viewer)), initial)
        new_first = self.task("插入第一名", due=0)
        self.assertEqual(list(company_top_tasks(self.viewer)), [new_first, *initial[:9]])

    def test_top_tasks_excludes_ineligible_only_data(self):
        self.task("已完成", completion_status=Task.Completion.COMPLETED)
        self.assertEqual(list(company_top_tasks(self.viewer)), [])

    def test_home_payload_uses_authoritative_sources(self):
        today = date(2026, 10, 28)
        task = self.task("本人任务")
        company = CompanyImportantItem.objects.create(
            title="已发布公告", status=CompanyImportantItem.Status.PUBLISHED, published_at=self.now,
        )
        log = DailyLog.objects.create(
            author=self.viewer, log_date=today, status=DailyLog.Status.SUBMITTED,
            completed_work="  工作\n  继续 " + "长" * 60,
        )
        item = HomePersonalDailyItem.objects.create(
            owner=self.viewer, title="本人事项", start_date=today, end_date=today, color="#C7DDFF",
        )
        with patch("apps.home.services.timezone.localdate", return_value=today):
            payload = home_payload(self.viewer)
        self.assertEqual([row["id"] for row in payload["company_items"]], [company.pk])
        self.assertEqual([row["id"] for row in payload["company_top_tasks"]], [task.pk])
        self.assertEqual(payload["recent_logs"], [{
            "id": log.pk, "log_date": today, "summary": "工作 继续 " + "长" * 44,
        }])
        self.assertEqual(payload["personal_daily_items"], [{"id": item.pk, "title": "本人事项"}])

    def test_home_payload_caps_board_and_today_items_at_five(self):
        today = date(2026, 10, 28)
        tasks = [self.task(f"任务{i}", due=i) for i in range(6)]
        items = [
            HomePersonalDailyItem.objects.create(
                owner=self.viewer, title=f"事项{i}", start_date=today, end_date=today, color="#C7DDFF",
            )
            for i in range(6)
        ]
        for other_day in (today - timedelta(days=1), today + timedelta(days=1)):
            HomePersonalDailyItem.objects.create(
                owner=self.viewer, title="相邻日期", start_date=other_day, end_date=other_day, color="#C7DDFF",
            )
        with patch("apps.home.services.timezone.localdate", return_value=today):
            payload = home_payload(self.viewer)
        self.assertEqual([row["id"] for row in payload["company_top_tasks"]], [row.pk for row in tasks[:5]])
        self.assertEqual([row["id"] for row in payload["personal_daily_items"]], [row.pk for row in items[:5]])

    def test_home_payload_excludes_private_sources_and_matches_unread_endpoint(self):
        today = date(2026, 10, 28)
        task = self.task("通知任务", assignee=self.other)
        DailyLog.objects.create(author=self.viewer, log_date=today, completed_work="私人草稿")
        DailyLog.objects.create(author=self.other, log_date=today, status=DailyLog.Status.SUBMITTED)
        HomePersonalDailyItem.objects.create(
            owner=self.other, title="他人事项", start_date=today, end_date=today, color="#C7DDFF",
        )
        self.notification("unread", task)
        self.notification("read", task, is_read=True, read_at=self.now)
        self.notification("resolved", task, resolved_at=self.now)
        self.notification("other", task, recipient=self.other)
        self.client.force_authenticate(user=self.viewer)
        with patch("apps.home.services.timezone.localdate", return_value=today):
            home = self.client.get("/api/v1/app/home/")
        unread = self.client.get("/api/v1/app/notifications/unread-count/")
        self.assertEqual((home.status_code, unread.status_code), (200, 200))
        data = home.data["data"]
        self.assertEqual(data["company_top_tasks"], [])
        self.assertEqual(data["recent_logs"], [])
        self.assertEqual(data["personal_daily_items"], [])
        self.assertEqual(data["unread_notification_count"], 1)
        self.assertEqual(data["unread_notification_count"], unread.data["data"]["unread_count"])
