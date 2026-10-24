import json
from uuid import UUID

from django.db import IntegrityError, transaction
from django.test import override_settings
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken, RefreshToken

from .models import AdminAuditLog, Department, Team, User


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class AccountApiTests(APITestCase):
    password = "Pandora-test-938!"

    def setUp(self):
        self.initial_passwords = {}
        self.identity_numbers = {}
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + self.login("admin", "admin")["access"])
        self.department = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="后端", department=self.department)

    def login(self, scope, username, expected=200, password=None):
        credential = password if password is not None else self.initial_passwords.get(username, self.password)
        response = self.client.post(f"/api/v1/{scope}/auth/token/", {"username": username, "password": credential}, format="json")
        self.assertEqual(response.status_code, expected, response.data)
        UUID(response.data["requestId"])
        return response.data["data"]

    def create_user(self, username, role, department=None, team=None, **extra):
        identity_number = extra.pop("id_number", f"TEST-ID-{username}")
        self.identity_numbers[username] = identity_number
        data = {"username": username, "id_number": identity_number, "display_name": username, "business_role": role, **extra}
        if department:
            data["department_id"] = department.pk
        if team:
            data["team_id"] = team.pk
        response = self.client.post("/api/v1/admin/users/", data, format="json")
        if response.status_code == 201:
            self.initial_passwords[username] = response.data["data"]["initial_password"]
        return response

    def hierarchy(self):
        rows = [("founder", "founder", None, None), ("manager", "department_manager", self.department, None), ("leader", "team_leader", self.department, self.team), ("employee", "employee", self.department, self.team)]
        users = []
        for name, role, department, team in rows:
            response = self.create_user(name, role, department, team)
            self.assertEqual(response.status_code, 201, response.data)
            users.append(User.objects.get(pk=response.data["data"]["id"]))
        return users

    def test_full_provision_login_and_profile(self):
        users = self.hierarchy()
        self.assertEqual([u.manager_id for u in users], [None] + [u.pk for u in users[:-1]])
        for user in users:
            initial_password = self.initial_passwords[user.username]
            self.assertEqual(len(initial_password), 16)
            self.assertNotEqual(user.password, initial_password)
            self.assertTrue(user.check_password(initial_password))
            token = self.login("app", user.username)
            self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token["access"])
            profile = self.client.get("/api/v1/app/me/")
            self.assertEqual(profile.status_code, 200)
            self.assertEqual(profile.data["data"]["manager_id"], user.manager_id)
            self.assertEqual(profile.data["data"]["display_name"], user.username)
            self.assertEqual(profile.data["data"]["id_number_masked"], self.mask_identity_number(self.identity_numbers[user.username]))
            self.assertNotIn("id_number", profile.data["data"])
            self.assertNotIn("password", profile.data["data"])
            self.assertNotIn("initial_password", profile.data["data"])

    @staticmethod
    def mask_identity_number(value):
        if len(value) <= 4:
            return "*" * len(value)
        return "*" * (len(value) - 4) + value[-4:]

    def test_creation_requires_identity_and_returns_one_time_password_without_caching(self):
        missing_id = self.client.post(
            "/api/v1/admin/users/",
            {"username": "missing-id", "display_name": "missing-id", "business_role": "founder"},
            format="json",
        )
        self.assertEqual(missing_id.status_code, 400)
        self.assertFalse(User.objects.filter(username="missing-id").exists())

        response = self.create_user("new-founder", "founder")
        self.assertEqual(response.status_code, 201, response.data)
        data = response.data["data"]
        initial_password = data["initial_password"]
        user = User.objects.get(username="new-founder")
        self.assertEqual(len(initial_password), 16)
        self.assertTrue(user.is_active)
        self.assertTrue(user.check_password(initial_password))
        self.assertNotIn("id_number", data)
        self.assertEqual(data["id_number_masked"], self.mask_identity_number(self.identity_numbers["new-founder"]))
        self.assertEqual(response["Cache-Control"], "no-store, no-cache, max-age=0, private")
        self.assertEqual(response["Pragma"], "no-cache")
        self.assertEqual(response["Expires"], "0")
        self.login("app", "new-founder", password=initial_password)

    def test_type_isolation_login_access_refresh_and_legacy_routes(self):
        employee = self.hierarchy()[-1]
        admin_tokens = self.login("admin", "admin")
        app_tokens = self.login("app", employee.username)
        self.login("app", "admin", 401)
        self.login("admin", employee.username, 401)
        for scope, tokens, other in [("admin", admin_tokens, "app"), ("app", app_tokens, "admin")]:
            self.client.credentials(HTTP_AUTHORIZATION="Bearer " + tokens["access"])
            self.assertEqual(self.client.get(f"/api/v1/{other}/me/").status_code, 403)
            refresh = self.client.post(f"/api/v1/{scope}/auth/token/refresh/", {"refresh": tokens["refresh"]})
            self.assertEqual(refresh.status_code, 200, refresh.data)
            self.assertIn("access", refresh.data["data"])
            self.assertEqual(self.client.post(f"/api/v1/{other}/auth/token/refresh/", {"refresh": tokens["refresh"]}).status_code, 401)
        self.assertEqual(self.client.post("/api/auth/token/", {}).status_code, 404)
        self.assertEqual(self.client.post("/api/auth/token/refresh/", {}).status_code, 404)

    def test_anonymous_and_business_cannot_manage(self):
        employee = self.hierarchy()[-1]
        for header, expected in [(None, 401), (self.login("app", employee.username)["access"], 403)]:
            self.client.credentials(**({"HTTP_AUTHORIZATION": "Bearer " + header} if header else {}))
            for resource in ["departments", "teams", "users"]:
                for method in [self.client.get, self.client.post]:
                    response = method(f"/api/v1/admin/{resource}/", {}, format="json")
                    self.assertEqual(response.status_code, expected)
                    self.assertEqual(response.data["code"], expected)

    def test_disabled_users_cannot_login_access_or_refresh(self):
        self.hierarchy()
        for scope, username in [("admin", "admin"), ("app", "employee")]:
            tokens = self.login(scope, username)
            User.objects.filter(username=username).update(is_active=False)
            self.login(scope, username, 401)
            self.client.credentials(HTTP_AUTHORIZATION="Bearer " + tokens["access"])
            self.assertEqual(self.client.get(f"/api/v1/{scope}/me/").status_code, 401)
            self.assertEqual(self.client.post(f"/api/v1/{scope}/auth/token/refresh/", {"refresh": tokens["refresh"]}).status_code, 401)

    def test_login_errors_and_invalid_tokens(self):
        self.login("admin", "admin", 401, password="wrong")
        self.login("app", "missing", 401)
        self.assertEqual(self.client.post("/api/v1/app/auth/token/", {}).status_code, 400)
        for token in ["invalid", str(AccessToken.for_user(self.admin))]:
            self.assertEqual(self.client.post("/api/v1/admin/auth/token/refresh/", {"refresh": token}).status_code, 401)
        refresh = RefreshToken.for_user(self.admin)
        refresh["account_type"] = "admin"
        refresh["exp"] = 1
        self.assertEqual(self.client.post("/api/v1/admin/auth/token/refresh/", {"refresh": str(refresh)}).status_code, 401)

    def test_organization_validation_and_missing_superiors(self):
        for role in ["department_manager", "team_leader", "employee"]:
            response = self.create_user("candidate", role, self.department, self.team if role != "department_manager" else None)
            self.assertEqual(response.status_code, 400)
        self.hierarchy()
        other = Department.objects.create(name="财务")
        cases = [
            ("founder", self.department, None), ("department_manager", None, None),
            ("department_manager", other, self.team), ("team_leader", self.department, None),
            ("employee", other, self.team), ("employee", self.department, None), ("invalid", None, None),
        ]
        for role, department, team in cases:
            self.assertEqual(self.create_user("candidate", role, department, team).status_code, 400)
        User.objects.filter(username="leader").update(is_active=False)
        self.assertEqual(self.create_user("candidate", "employee", self.department, self.team).status_code, 400)

    def test_unique_accounts_roles_and_protected_input(self):
        self.hierarchy()
        for username, role, department, team in [
            ("employee", "employee", self.department, self.team), ("another", "founder", None, None),
            ("another", "department_manager", self.department, None), ("another", "team_leader", self.department, self.team),
        ]:
            self.assertEqual(self.create_user(username, role, department, team).status_code, 400)
        for extra in [{"manager_id": self.admin.pk}, {"account_type": "admin"}, {"is_staff": True}, {"is_superuser": True}, {"password": "123"}, {"is_active": False}, {"display_name": "  "}]:
            self.assertEqual(self.create_user("candidate", "employee", self.department, self.team, **extra).status_code, 400)
        self.assertFalse(User.objects.filter(username="candidate").exists())

    def test_department_team_creation_filter_and_pagination(self):
        department = self.client.post("/api/v1/admin/departments/", {"name": "销售"})
        self.assertEqual(department.status_code, 201)
        department_id = department.data["data"]["id"]
        team = self.client.post("/api/v1/admin/teams/", {"name": "一组", "department_id": department_id})
        self.assertEqual(team.status_code, 201, team.data)
        for payload in [{"name": "一组", "department_id": department_id}, {"name": "无效", "department_id": 9999}, {"name": "  ", "department_id": department_id}]:
            self.assertEqual(self.client.post("/api/v1/admin/teams/", payload).status_code, 400)
        self.assertEqual(self.client.post("/api/v1/admin/departments/", {"name": "销售"}).status_code, 400)
        response = self.client.get("/api/v1/admin/teams/", {"department_id": department_id})
        self.assertEqual(response.data["data"]["count"], 1)
        self.assertEqual(response.data["data"]["results"][0]["department_id"], department_id)
        self.assertEqual(self.client.get("/api/v1/admin/teams/?department_id=abc").status_code, 400)
        response = self.client.get("/api/v1/admin/departments/?page_size=1")
        self.assertIsNotNone(response.data["data"]["next"])
        self.assertEqual(len(response.data["data"]["results"]), 1)

    def test_invalid_pagination_and_missing_department_filter(self):
        self.hierarchy()
        for query in ["page=abc", "page=999999"]:
            response = self.client.get(f"/api/v1/admin/departments/?{query}")
            self.assertEqual(response.status_code, 404)
        response = self.client.get("/api/v1/admin/teams/?department_id=999999")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["count"], 0)

    def test_list_contains_only_business_users_and_password_never_returns(self):
        self.hierarchy()
        response = self.client.get("/api/v1/admin/users/")
        self.assertEqual(response.data["data"]["count"], 4)
        for row in response.data["data"]["results"]:
            self.assertEqual(row["account_type"], "business")
            self.assertNotIn("password", row)
            self.assertNotIn("initial_password", row)
            self.assertNotIn("id_number", row)
            self.assertEqual(row["id_number_masked"], self.mask_identity_number(self.identity_numbers[row["username"]]))

    def test_database_constraints_prevent_duplicate_leaders(self):
        users = self.hierarchy()
        with self.assertRaises(IntegrityError), transaction.atomic():
            User.objects.create_user(username="duplicate", business_role="department_manager", department=self.department, manager=users[0])

    def test_inactive_leaders_do_not_block_active_replacements(self):
        self.hierarchy()
        User.objects.filter(username="founder").update(is_active=False)
        self.assertEqual(self.create_user("founder2", "founder").status_code, 201)
        User.objects.filter(username="manager").update(is_active=False)
        self.assertEqual(self.create_user("manager2", "department_manager", self.department).status_code, 201)
        User.objects.filter(username="leader").update(is_active=False)
        self.assertEqual(self.create_user("leader2", "team_leader", self.department, self.team).status_code, 201)

    def test_schema_matches_envelope_and_user_response(self):
        schema = self.client.get("/api/schema/", HTTP_ACCEPT="application/json").json()
        paths = schema["paths"]
        for path in ["admin/me/", "app/me/", "admin/users/", "admin/departments/", "admin/teams/", "admin/auth/token/", "app/auth/token/", "admin/auth/token/refresh/", "app/auth/token/refresh/"]:
            self.assertIn("/api/v1/" + path, paths)
        response = paths["/api/v1/admin/users/"]["post"]["responses"]["201"]["content"]["application/json"]["schema"]
        self.assertEqual(response["properties"]["data"]["$ref"], "#/components/schemas/BusinessUserCreateResponse")
        self.assertNotIn("/api/auth/token/", paths)

    def test_admin_user_creation_form_defaults_to_valid_admin(self):
        from .admin import PandoraUserCreationForm

        form = PandoraUserCreationForm(
            {
                "username": "admin_ui",
                "password1": self.password,
                "password2": self.password,
                "account_type": "admin",
                "business_role": "",
                "department": "",
                "team": "",
                "manager": "",
            }
        )
        self.assertTrue(form.is_valid(), form.errors)
        user = form.save()
        self.assertEqual(user.account_type, "admin")

    def test_department_detail_and_deactivation_guard(self):
        self.hierarchy()
        response = self.client.get(f"/api/v1/admin/departments/{self.department.pk}/")
        self.assertEqual(response.status_code, 200)
        data = response.data["data"]
        self.assertEqual(data["manager"]["display_name"], "manager")
        self.assertEqual(len(data["members"]), 3)
        self.assertEqual(len(data["teams"]), 1)
        response = self.client.patch(f"/api/v1/admin/departments/{self.department.pk}/", {"is_active": False}, format="json")
        self.assertEqual(response.status_code, 400)
        response = self.client.patch(f"/api/v1/admin/departments/{self.department.pk}/", {"description": "新说明"}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["description"], "新说明")

    def test_team_detail_and_deactivation_guard(self):
        self.hierarchy()
        response = self.client.get(f"/api/v1/admin/teams/{self.team.pk}/")
        self.assertEqual(response.status_code, 200)
        data = response.data["data"]
        self.assertEqual(data["leader"]["display_name"], "leader")
        self.assertEqual(data["department_id"], self.department.pk)
        self.assertEqual(len(data["members"]), 2)
        response = self.client.patch(f"/api/v1/admin/teams/{self.team.pk}/", {"is_active": False}, format="json")
        self.assertEqual(response.status_code, 400)

    def test_user_filtering(self):
        self.hierarchy()
        self.assertEqual(self.client.get("/api/v1/admin/users/", {"business_role": "employee"}).data["data"]["count"], 1)
        self.assertEqual(self.client.get("/api/v1/admin/users/", {"keyword": "manager"}).data["data"]["count"], 1)
        self.assertEqual(self.client.get("/api/v1/admin/users/", {"department_id": self.department.pk}).data["data"]["count"], 3)
        self.assertEqual(self.client.get("/api/v1/admin/users/", {"team_id": self.team.pk}).data["data"]["count"], 2)
        self.assertEqual(self.client.get("/api/v1/admin/users/", {"is_active": "false"}).data["data"]["count"], 0)

    def test_user_update_whitelist_leader_uniqueness_and_vacancy(self):
        users = self.hierarchy()
        employee = users[3]
        leader = users[2]
        for payload in [{"display_name": "改名"}, {"id_number": "110101199001011234"}, {"password": "xxx"}, {"username": "newname"}]:
            self.assertEqual(self.client.patch(f"/api/v1/admin/users/{employee.pk}/", payload, format="json").status_code, 400)
        self.assertEqual(self.client.patch(f"/api/v1/admin/users/{employee.pk}/", {"business_role": "team_leader"}, format="json").status_code, 400)
        self.assertEqual(self.client.patch(f"/api/v1/admin/users/{leader.pk}/", {"is_active": False}, format="json").status_code, 200)
        response = self.client.patch(f"/api/v1/admin/users/{employee.pk}/", {"business_role": "team_leader"}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["business_role"], "team_leader")
        self.assertEqual(response.data["data"]["manager_id"], users[1].pk)

    def test_user_update_rejects_inactive_candidates(self):
        users = self.hierarchy()
        employee = users[3]
        other = Department.objects.create(name="销售")
        other.is_active = False
        other.save(update_fields=["is_active"])
        response = self.client.patch(f"/api/v1/admin/users/{employee.pk}/", {"department_id": other.pk}, format="json")
        self.assertEqual(response.status_code, 400)

    def test_dashboard_counts(self):
        self.hierarchy()
        data = self.client.get("/api/v1/admin/dashboard/").data["data"]
        self.assertEqual(data["active_users"], 4)
        self.assertEqual(data["departments"], 1)
        self.assertEqual(data["teams"], 1)
        self.assertEqual(data["published_company_items"], 0)

    def test_audit_logs_never_contain_sensitive_data(self):
        self.hierarchy()
        self.client.post("/api/v1/admin/departments/", {"name": "销售"}, format="json")
        response = self.client.get("/api/v1/admin/audit-logs/")
        self.assertEqual(response.status_code, 200)
        self.assertGreaterEqual(response.data["data"]["count"], 1)
        for log in AdminAuditLog.objects.all():
            payload = json.dumps({"before": log.before_data, "after": log.after_data}, ensure_ascii=False)
            self.assertNotIn("id_number", payload)
            self.assertNotIn("password", payload)
            self.assertNotIn("initial_password", payload)

    def test_app_me_update_and_password(self):
        users = self.hierarchy()
        employee = users[3]
        initial = self.initial_passwords["employee"]
        token = self.login("app", employee.username, password=initial)
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token["access"])
        response = self.client.patch("/api/v1/app/me/", {"display_name": "张三"}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["display_name"], "张三")
        response = self.client.patch("/api/v1/app/me/", {"username": "13900000000"}, format="json")
        self.assertEqual(response.status_code, 200)
        employee.refresh_from_db()
        self.assertEqual(employee.username, "13900000000")
        response = self.client.patch("/api/v1/app/me/", {"id_number": "110101199001011234"}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["id_number_masked"], "**************1234")
        self.assertEqual(self.client.patch("/api/v1/app/me/", {"username": "leader"}, format="json").status_code, 400)
        self.assertEqual(self.client.post("/api/v1/app/me/password/", {"current_password": "wrong", "new_password": "New-Pass-123!", "new_password_confirm": "New-Pass-123!"}, format="json").status_code, 400)
        self.assertEqual(self.client.post("/api/v1/app/me/password/", {"current_password": initial, "new_password": "New-Pass-123!", "new_password_confirm": "Different"}, format="json").status_code, 400)
        response = self.client.post("/api/v1/app/me/password/", {"current_password": initial, "new_password": "New-Pass-123!", "new_password_confirm": "New-Pass-123!"}, format="json")
        self.assertEqual(response.status_code, 200)
        employee.refresh_from_db()
        self.assertTrue(employee.check_password("New-Pass-123!"))

    def test_id_number_uniqueness(self):
        self.hierarchy()
        # 录入时身份证号重复
        dup = self.create_user("dup", "founder", id_number=self.identity_numbers["employee"])
        self.assertEqual(dup.status_code, 400)
        self.assertFalse(User.objects.filter(username="dup").exists())
        # App 本人修改为他人已用身份证号
        employee = User.objects.get(username="employee")
        token = self.login("app", employee.username)
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token["access"])
        response = self.client.patch("/api/v1/app/me/", {"id_number": self.identity_numbers["leader"]}, format="json")
        self.assertEqual(response.status_code, 400)
        # 数据库层唯一约束兑底（绕过序列化器）
        with self.assertRaises(IntegrityError), transaction.atomic():
            User.objects.create_user(
                username="dbdup", password=self.password,
                id_number=self.identity_numbers["manager"],
                business_role="employee", department=self.department, team=self.team,
                manager=User.objects.get(username="leader"),
            )
