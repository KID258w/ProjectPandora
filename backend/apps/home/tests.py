from datetime import timedelta

from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from apps.accounts.models import Department, Team, User
from apps.content.models import CompanyImportantItem
from apps.logs.models import DailyLog
from apps.tasks.models import Task, TaskNotification

from .models import HomePersonalFocusState, HomePersonalFocusTask


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class HomePanelApiTests(APITestCase):
    def setUp(self):
        self.department = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="后端", department=self.department)
        self.founder = self.business_user("founder", User.Role.FOUNDER)
        self.manager = self.business_user("manager", User.Role.DEPARTMENT_MANAGER, self.department, manager=self.founder)
        self.leader = self.business_user("leader", User.Role.TEAM_LEADER, self.department, self.team, self.manager)
        self.employee = self.business_user("employee", User.Role.EMPLOYEE, self.department, self.team, self.leader)
        self.colleague = self.business_user("colleague", User.Role.EMPLOYEE, self.department, self.team, self.leader)

    def business_user(self, username, role, department=None, team=None, manager=None):
        return User.objects.create_user(
            username=username, password="Pandora-test-938!", display_name=username,
            account_type=User.AccountType.BUSINESS, business_role=role,
            department=department, team=team, manager=manager,
        )

    def auth(self, user):
        token = AccessToken.for_user(user)
        token["account_type"] = user.account_type
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def task(self, creator, assignee, title="任务", due_days=5,
             approval=Task.Approval.APPROVED, completion=Task.Completion.UNFINISHED,
             lifecycle=Task.Lifecycle.ACTIVE):
        return Task.objects.create(
            creator=creator, assignee=assignee, title=title, description="描述",
            due_at=timezone.now() + timedelta(days=due_days),
            approval_status=approval, completion_status=completion, lifecycle_status=lifecycle,
        )

    def log(self, author, day, status=DailyLog.Status.SUBMITTED, text="完成接口梳理"):
        today = timezone.localdate()
        return DailyLog.objects.create(
            author=author, log_date=today - timedelta(days=day), completed_work=text, status=status,
        )

    # ---- 公司十大重要任务 ----

    def test_company_top_tasks_orders_by_source_then_due_and_excludes_ineligible(self):
        assigned = self.task(self.manager, self.employee, title="他人派发", due_days=10)
        self_created = self.task(self.employee, self.employee, title="本人自建", due_days=1)
        later_assigned = self.task(self.manager, self.employee, title="他人派发-后到期", due_days=20)
        self.task(self.manager, self.employee, title="已完成", completion=Task.Completion.COMPLETED)
        self.task(self.manager, self.employee, title="已取消", lifecycle=Task.Lifecycle.CANCELLED)
        self.task(self.manager, self.employee, title="待审批", approval=Task.Approval.PENDING)
        self.task(self.employee, self.colleague, title="派给下属")
        self.task(self.colleague, self.colleague, title="别人的任务")

        self.auth(self.employee)
        rows = self.client.get("/api/v1/app/home/company-top-tasks/").data["data"]["results"]
        self.assertEqual([row["title"] for row in rows], ["他人派发", "他人派发-后到期", "本人自建"])
        self.assertEqual(list(rows[0].keys()), ["id", "title", "completion_status", "due_at"])
        self.assertNotIn("progress", rows[0])

    def test_company_top_tasks_capped_at_ten(self):
        for index in range(12):
            self.task(self.manager, self.employee, title=f"任务{index:02d}", due_days=index + 1)
        self.auth(self.employee)
        data = self.client.get("/api/v1/app/home/company-top-tasks/?page_size=50").data["data"]
        self.assertEqual(data["count"], 10)
        self.assertEqual(len(data["results"]), 10)

    # ---- 个人十大重点工作 ----

    def test_personal_focus_prefills_once_and_then_stays_independent(self):
        for index in range(12):
            self.task(self.manager, self.employee, title=f"任务{index:02d}", due_days=index + 1)

        self.auth(self.employee)
        first = self.client.get("/api/v1/app/home/personal-focus/").data["data"]
        self.assertTrue(first["initialized"])
        self.assertEqual(len(first["tasks"]), 10)
        self.assertEqual([row["sort_order"] for row in first["tasks"]], list(range(1, 11)))
        self.assertTrue(HomePersonalFocusState.objects.get(user=self.employee).initialized_at)

        # 新增候选不会自动重建既有列表
        self.task(self.manager, self.employee, title="新任务", due_days=0)
        second = self.client.get("/api/v1/app/home/personal-focus/").data["data"]
        self.assertEqual([row["task_id"] for row in second["tasks"]], [row["task_id"] for row in first["tasks"]])

        # 用户自行维护顺序
        chosen = [row["task_id"] for row in first["tasks"][:3]][::-1]
        saved = self.client.put("/api/v1/app/home/personal-focus/", {"task_ids": chosen}, format="json")
        self.assertEqual(saved.status_code, 200, saved.data)
        self.assertEqual(
            [(row["task_id"], row["sort_order"]) for row in saved.data["data"]["tasks"]],
            [(chosen[0], 1), (chosen[1], 2), (chosen[2], 3)],
        )
        third = self.client.get("/api/v1/app/home/personal-focus/").data["data"]
        self.assertEqual([row["task_id"] for row in third["tasks"]], chosen)

    def test_first_home_open_initializes_personal_focus_once(self):
        """首次打开首页即完成预填；之后的首页请求不再重建，也不影响已有顺序。"""
        for index in range(3):
            self.task(self.manager, self.employee, title=f"任务{index}", due_days=index + 1)
        self.auth(self.employee)
        self.assertFalse(HomePersonalFocusState.objects.filter(user=self.employee).exists())

        first = self.client.get("/api/v1/app/home/").data["data"]["personal_focus_tasks"]
        self.assertEqual(len(first), 3)
        state = HomePersonalFocusState.objects.get(user=self.employee)
        self.assertIsNotNone(state.initialized_at)
        self.assertEqual(HomePersonalFocusTask.objects.filter(user=self.employee).count(), 3)

        # 之后新增候选不会改变已初始化的个人重点
        self.task(self.manager, self.employee, title="后来的任务", due_days=0)
        second = self.client.get("/api/v1/app/home/").data["data"]["personal_focus_tasks"]
        self.assertEqual([row["id"] for row in second], [row["id"] for row in first])

        # 两个入口共用同一初始化逻辑，不会重复预填
        via_focus = self.client.get("/api/v1/app/home/personal-focus/").data["data"]["tasks"]
        self.assertEqual([row["task_id"] for row in via_focus], [row["id"] for row in first])
        self.assertEqual(HomePersonalFocusTask.objects.filter(user=self.employee).count(), 3)

    def test_first_use_without_candidates_stays_empty_until_user_saves(self):
        """首次使用时无候选任务：记录初始化状态，之后不自动补填，但允许用户手动添加。"""
        self.auth(self.founder)
        empty = self.client.get("/api/v1/app/home/").data["data"]["personal_focus_tasks"]
        self.assertEqual(empty, [])
        self.assertIsNotNone(HomePersonalFocusState.objects.get(user=self.founder).initialized_at)

        task = self.task(self.founder, self.founder, title="后来自己建的任务")
        still_empty = self.client.get("/api/v1/app/home/").data["data"]["personal_focus_tasks"]
        self.assertEqual(still_empty, [])

        saved = self.client.put("/api/v1/app/home/personal-focus/", {"task_ids": [task.pk]}, format="json")
        self.assertEqual(saved.status_code, 200, saved.data)
        self.assertEqual(saved.data["data"]["tasks"], [{"task_id": task.pk, "sort_order": 1}])

    def test_personal_focus_is_per_user(self):
        mine = self.task(self.manager, self.employee, title="我的任务")
        other = self.task(self.manager, self.colleague, title="同事任务")
        self.auth(self.employee)
        self.client.put("/api/v1/app/home/personal-focus/", {"task_ids": [mine.pk]}, format="json")
        self.auth(self.colleague)
        data = self.client.get("/api/v1/app/home/personal-focus/").data["data"]
        self.assertNotIn(mine.pk, [row["task_id"] for row in data["tasks"]])
        self.assertEqual(
            self.client.put("/api/v1/app/home/personal-focus/", {"task_ids": [mine.pk]}, format="json").status_code, 400,
        )
        # 同事的 GET 会按其本人候选预填，因此允许出现自己的任务，但不能出现他人的
        self.assertTrue(HomePersonalFocusTask.objects.filter(user=self.colleague, task=other).exists())
        self.assertFalse(HomePersonalFocusTask.objects.filter(user=self.colleague, task=mine).exists())

    def test_personal_focus_rejects_ineligible_and_oversized_lists(self):
        tasks = [self.task(self.manager, self.employee, title=f"任务{index}") for index in range(11)]
        completed = self.task(self.manager, self.employee, title="已完成", completion=Task.Completion.COMPLETED)
        self.auth(self.employee)

        self.assertEqual(self.client.put(
            "/api/v1/app/home/personal-focus/", {"task_ids": [task.pk for task in tasks]}, format="json",
        ).status_code, 400)
        self.assertEqual(self.client.put(
            "/api/v1/app/home/personal-focus/", {"task_ids": [tasks[0].pk, tasks[0].pk]}, format="json",
        ).status_code, 400)
        rejected = self.client.put(
            "/api/v1/app/home/personal-focus/", {"task_ids": [completed.pk]}, format="json",
        )
        self.assertEqual(rejected.status_code, 400)
        self.assertIn("不可选", str(rejected.data["data"]))
        self.assertFalse(HomePersonalFocusTask.objects.filter(user=self.employee).exists())

    def test_personal_focus_cleanup_removes_completed_and_cancelled_and_compacts_order(self):
        keep_a = self.task(self.manager, self.employee, title="保留A")
        drop = self.task(self.manager, self.employee, title="将被完成")
        keep_b = self.task(self.manager, self.employee, title="保留B")
        drop_cancel = self.task(self.manager, self.employee, title="将被取消")
        self.auth(self.employee)
        self.client.put(
            "/api/v1/app/home/personal-focus/",
            {"task_ids": [keep_a.pk, drop.pk, keep_b.pk, drop_cancel.pk]}, format="json",
        )

        drop.completion_status = Task.Completion.COMPLETED
        drop.save(update_fields=["completion_status"])
        drop_cancel.lifecycle_status = Task.Lifecycle.CANCELLED
        drop_cancel.save(update_fields=["lifecycle_status"])

        data = self.client.get("/api/v1/app/home/personal-focus/").data["data"]
        self.assertEqual([row["task_id"] for row in data["tasks"]], [keep_a.pk, keep_b.pk])
        self.assertEqual([row["sort_order"] for row in data["tasks"]], [1, 2])

    def test_personal_focus_candidates_support_keyword_and_scope(self):
        self.task(self.manager, self.employee, title="完成需求梳理", due_days=2)
        self.task(self.manager, self.employee, title="实现接口", due_days=3)
        self.task(self.manager, self.employee, title="已完成需求验收", completion=Task.Completion.COMPLETED)
        self.task(self.employee, self.colleague, title="派给下属的需求")
        self.auth(self.employee)

        rows = self.client.get("/api/v1/app/home/personal-focus/candidates/?keyword=需求").data["data"]["results"]
        self.assertEqual([row["title"] for row in rows], ["完成需求梳理"])
        all_rows = self.client.get("/api/v1/app/home/personal-focus/candidates/").data["data"]["results"]
        self.assertEqual({row["title"] for row in all_rows}, {"实现接口", "完成需求梳理"})

    # ---- 首页聚合 ----

    def test_home_payload_limits_fields_and_scoping(self):
        for index in range(6):
            CompanyImportantItem.objects.create(
                title=f"事项{index}", summary="摘要", body="正文",
                status=CompanyImportantItem.Status.PUBLISHED, sort_order=index + 1,
            )
        CompanyImportantItem.objects.create(title="草稿事项", summary="摘要", body="正文")
        for index in range(6):
            self.task(self.manager, self.employee, title=f"任务{index:02d}", due_days=index + 1)
        for day in range(6):
            self.log(self.employee, day, text=f"第{day}天完成的工作")
        self.log(self.employee, 7, status=DailyLog.Status.DRAFT, text="草稿不该出现")
        self.log(self.colleague, 0, text="同事的日志")

        task = Task.objects.filter(assignee=self.employee).first()
        TaskNotification.objects.create(
            recipient=self.employee, task=task,
            notification_type=TaskNotification.Type.ASSIGNMENT_RECEIVED, event_key="home-unread",
        )
        TaskNotification.objects.create(
            recipient=self.employee, task=task,
            notification_type=TaskNotification.Type.TASK_CANCELLED, event_key="home-resolved",
            resolved_at=timezone.now(),
        )

        self.auth(self.employee)
        data = self.client.get("/api/v1/app/home/").data["data"]
        self.assertEqual(len(data["company_items"]), 5)
        self.assertEqual(len(data["company_top_tasks"]), 5)
        self.assertEqual(len(data["personal_focus_tasks"]), 5)
        self.assertEqual(len(data["recent_logs"]), 5)
        self.assertEqual(data["unread_notification_count"], 1)
        self.assertEqual([row["summary"] for row in data["recent_logs"]][:2], ["第0天完成的工作", "第1天完成的工作"])
        self.assertEqual(list(data["company_top_tasks"][0].keys()), ["id", "title", "completion_status", "due_at"])
        self.assertTrue(all(row["title"] != "草稿事项" for row in data["company_items"]))

    def test_home_payload_for_user_without_data_is_empty_but_valid(self):
        self.auth(self.founder)
        data = self.client.get("/api/v1/app/home/").data["data"]
        self.assertEqual(data["company_items"], [])
        self.assertEqual(data["company_top_tasks"], [])
        self.assertEqual(data["personal_focus_tasks"], [])
        self.assertEqual(data["recent_logs"], [])
        self.assertEqual(data["unread_notification_count"], 0)

    def test_home_requires_business_account(self):
        admin = User.objects.create_superuser(username="admin", password="Pandora-test-938!")
        self.auth(admin)
        self.assertEqual(self.client.get("/api/v1/app/home/").status_code, 403)
