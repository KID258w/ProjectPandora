from uuid import UUID

from django.db import IntegrityError, transaction
from django.test import override_settings
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken, RefreshToken

from .models import Department, Team, User


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class AccountApiTests(APITestCase):
    password = "Pandora-test-938!"

    def setUp(self):
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + self.login("admin", "admin")["access"])
        self.department = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="后端", department=self.department)

    def login(self, scope, username, expected=200, password=None):
        response = self.client.post(f"/api/v1/{scope}/auth/token/", {"username": username, "password": password or self.password}, format="json")
        self.assertEqual(response.status_code, expected, response.data)
        UUID(response.data["requestId"])
        return response.data["data"]

    def create_user(self, username, role, department=None, team=None, **extra):
        data = {"username": username, "password": self.password, "display_name": username, "business_role": role, **extra}
        if department:
            data["department_id"] = department.pk
        if team:
            data["team_id"] = team.pk
        return self.client.post("/api/v1/admin/users/", data, format="json")

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
            self.assertNotEqual(user.password, self.password)
            self.assertTrue(user.check_password(self.password))
            token = self.login("app", user.username)
            self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token["access"])
            profile = self.client.get("/api/v1/app/me/")
            self.assertEqual(profile.status_code, 200)
            self.assertEqual(profile.data["data"]["manager_id"], user.manager_id)
            self.assertEqual(profile.data["data"]["display_name"], user.username)
            self.assertNotIn("password", profile.data["data"])

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
        for extra in [{"manager_id": self.admin.pk}, {"account_type": "admin"}, {"is_staff": True}, {"is_superuser": True}, {"password": "123"}, {"display_name": "  "}]:
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

    def test_database_constraints_prevent_duplicate_leaders(self):
        users = self.hierarchy()
        with self.assertRaises(IntegrityError), transaction.atomic():
            User.objects.create_user(username="duplicate", business_role="department_manager", department=self.department, manager=users[0])

    def test_schema_matches_envelope_and_user_response(self):
        schema = self.client.get("/api/schema/", HTTP_ACCEPT="application/json").json()
        paths = schema["paths"]
        for path in ["admin/me/", "app/me/", "admin/users/", "admin/departments/", "admin/teams/", "admin/auth/token/", "app/auth/token/", "admin/auth/token/refresh/", "app/auth/token/refresh/"]:
            self.assertIn("/api/v1/" + path, paths)
        response = paths["/api/v1/admin/users/"]["post"]["responses"]["201"]["content"]["application/json"]["schema"]
        self.assertEqual(response["properties"]["data"]["$ref"], "#/components/schemas/User")
        self.assertNotIn("/api/auth/token/", paths)
