from datetime import date, timedelta
from unittest.mock import patch

from django.db import IntegrityError
from django.test import override_settings
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from apps.accounts.models import Department, Team, User
from apps.tags.models import PALETTE, Tag

from .models import DailyLog, DailyLogTag
from .services import recent_submitted_logs


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class LogAndTagApiTests(APITestCase):
    today = date(2026, 10, 28)

    def setUp(self):
        self.clock = patch("apps.logs.views.timezone.localdate", return_value=self.today)
        self.clock.start()
        self.addCleanup(self.clock.stop)
        self.department = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="服务端", department=self.department)
        self.other_department = Department.objects.create(name="市场")
        self.other_team = Team.objects.create(name="销售", department=self.other_department)
        self.founder = self.user("founder", User.Role.FOUNDER)
        self.manager = self.user("manager", User.Role.DEPARTMENT_MANAGER, self.department, manager=self.founder)
        self.leader = self.user("leader", User.Role.TEAM_LEADER, self.department, self.team, self.manager)
        self.employee = self.user("employee", User.Role.EMPLOYEE, self.department, self.team, self.leader)
        self.other_manager = self.user("other-manager", User.Role.DEPARTMENT_MANAGER, self.other_department, manager=self.founder)
        self.other_leader = self.user("other-leader", User.Role.TEAM_LEADER, self.other_department, self.other_team, self.other_manager)
        self.other_employee = self.user("other-employee", User.Role.EMPLOYEE, self.other_department, self.other_team, self.other_leader)
        self.auth(self.employee)

    def user(self, name, role, department=None, team=None, manager=None):
        return User.objects.create_user(username=name, password="test-password", display_name=name,
            account_type=User.AccountType.BUSINESS, business_role=role, department=department,
            team=team, manager=manager)

    def auth(self, user):
        token = AccessToken.for_user(user)
        token["account_type"] = user.account_type
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def url(self, offset=0):
        return f"/api/v1/app/logs/mine/{self.today + timedelta(days=offset)}/"

    def test_three_date_window_uniqueness_submit_and_freeze(self):
        for offset in (-2, -1, 0):
            response = self.client.put(self.url(offset), {"completed_work": f"工作 {offset}"}, format="json")
            self.assertEqual(response.status_code, 200, response.data)
            self.assertEqual(self.client.post(self.url(offset) + "submit/", {}, format="json").status_code, 200)
            self.assertEqual(self.client.put(self.url(offset), {"completed_work": "更新"}, format="json").status_code, 200)
            self.assertEqual(DailyLog.objects.filter(author=self.employee, log_date=self.today + timedelta(days=offset)).count(), 1)
        for offset in (-3, 1):
            self.assertEqual(self.client.put(self.url(offset), {"completed_work": "禁止"}, format="json").status_code, 400)
            self.assertEqual(self.client.post(self.url(offset) + "submit/", {}, format="json").status_code, 400)
            self.assertFalse(self.client.get(self.url(offset)).data["data"]["can_edit"])
        self.assertEqual(DailyLog.objects.filter(author=self.employee).count(), 3)
        with patch("apps.logs.views.timezone.localdate", return_value=self.today + timedelta(days=1)):
            self.assertEqual(self.client.put(self.url(-2), {"completed_work": "冻结"}, format="json").status_code, 400)
        self.assertEqual(self.client.get("/api/v1/app/logs/calendar/?year=2026&month=10").data["data"]["days"].__len__(), 3)

    def test_draft_privacy_subordinate_scope_and_manager_read_only(self):
        draft = self.client.put(self.url(), {"completed_work": "私有草稿"}, format="json").data["data"]
        self.auth(self.manager)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/{draft['id']}/").status_code, 404)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/?user_id={self.employee.pk}").data["data"]["count"], 0)
        self.assertEqual(self.client.put(self.url(), {"completed_work": "越权"}, format="json").status_code, 200)
        self.assertNotEqual(DailyLog.objects.get(pk=draft["id"]).author_id, self.manager.pk)
        self.auth(self.employee)
        self.client.post(self.url() + "submit/", {}, format="json")
        self.auth(self.manager)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/{draft['id']}/").status_code, 200)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/?user_id={self.employee.pk}").data["data"]["count"], 1)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/?user_id={self.other_employee.pk}").status_code, 403)
        self.assertEqual(self.client.get(f"/api/v1/app/log-viewers/?keyword=服务端").data["data"]["count"], 2)
        self.auth(self.leader)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/?user_id={self.other_employee.pk}").status_code, 403)
        self.auth(self.founder)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/?user_id={self.employee.pk}").status_code, 200)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/?user_id={self.other_employee.pk}").status_code, 200)
        self.auth(User.objects.create_superuser(username="admin", password="password"))
        self.assertEqual(self.client.get("/api/v1/app/logs/").status_code, 403)

    def test_tag_creation_colors_soft_delete_and_atomic_log_update(self):
        self.auth(self.founder)
        created = self.client.post("/api/v1/app/tags/", {"name": "客户"}, format="json")
        self.assertEqual(created.status_code, 201, created.data)
        tag_id = created.data["data"]["id"]
        self.assertEqual(created.data["data"]["color"], PALETTE[0])
        second = self.client.post("/api/v1/app/tags/", {"name": "开发"}, format="json")
        self.assertEqual(second.data["data"]["color"], PALETTE[1])
        self.assertEqual(self.client.post("/api/v1/app/tags/", {"name": "客户"}, format="json").status_code, 409)
        self.auth(self.employee)
        self.assertEqual(self.client.post("/api/v1/app/tags/", {"name": "非法"}, format="json").status_code, 403)
        self.assertEqual(self.client.get("/api/v1/app/tags/?include_inactive=true").status_code, 403)
        saved = self.client.put(self.url(), {"completed_work": "已完成", "tag_ids": [tag_id]}, format="json")
        self.assertEqual(saved.status_code, 200, saved.data)
        log_id = saved.data["data"]["id"]
        self.auth(self.founder)
        self.assertEqual(self.client.delete(f"/api/v1/app/tags/{tag_id}/").status_code, 200)
        self.assertEqual(self.client.post("/api/v1/app/tags/", {"name": "客户"}, format="json").status_code, 201)
        self.auth(self.employee)
        self.assertEqual(self.client.get(f"/api/v1/app/logs/{log_id}/").data["data"]["tags"][0]["id"], tag_id)
        self.assertEqual(self.client.put(self.url(), {"completed_work": "保留", "tag_ids": [tag_id]}, format="json").status_code, 200)
        invalid = self.client.put(self.url(), {"completed_work": "不应保存", "tag_ids": [tag_id, 999999]}, format="json")
        self.assertEqual(invalid.status_code, 400)
        self.assertEqual(DailyLog.objects.get(pk=log_id).completed_work, "保留")
        self.assertEqual(self.client.put(self.url(), {"tag_ids": []}, format="json").status_code, 200)
        self.assertEqual(DailyLogTag.objects.filter(daily_log_id=log_id).count(), 0)
        self.assertEqual(self.client.put(self.url(), {"tag_ids": [tag_id]}, format="json").status_code, 400)

    def test_submit_required_and_reject_extra_fields(self):
        self.assertEqual(self.client.put(self.url(), {"completed_work": ""}, format="json").status_code, 400)
        self.assertFalse(DailyLog.objects.filter(author=self.employee, log_date=self.today).exists())
        self.assertEqual(self.client.post(self.url() + "submit/", {}, format="json").status_code, 400)
        self.assertEqual(self.client.put(self.url(), {"task_id": 1}, format="json").status_code, 400)
        self.assertEqual(self.client.put(self.url(), {"completed_work": "完成"}, format="json").status_code, 200)
        self.assertEqual(self.client.post(self.url() + "submit/", {}, format="json").status_code, 200)
        self.assertEqual(self.client.put(self.url(), {"completed_work": ""}, format="json").status_code, 400)
        self.assertEqual(DailyLog.objects.get(author=self.employee).completed_work, "完成")

    def test_recent_log_source_excludes_drafts_and_other_users(self):
        for offset in range(-6, 0):
            DailyLog.objects.create(author=self.employee, log_date=self.today + timedelta(days=offset),
                status=DailyLog.Status.SUBMITTED, completed_work=str(offset))
        DailyLog.objects.create(author=self.employee, log_date=self.today, status=DailyLog.Status.DRAFT)
        DailyLog.objects.create(author=self.other_employee, log_date=self.today,
            status=DailyLog.Status.SUBMITTED, completed_work="other")
        self.assertEqual([row.completed_work for row in recent_submitted_logs(self.employee)],
            [str(offset) for offset in range(-1, -6, -1)])
