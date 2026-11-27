from django.test import override_settings
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from .models import AppOperationAuditLog, User


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class EmployeeProfileValidationTests(APITestCase):
    ADMIN_USERS = "/api/v1/admin/users/"
    APP_ME = "/api/v1/app/me/"
    # Example values use a synthetic area code and valid calendar/check digits.
    VALID_ID = "99000020000229001X"
    OTHER_ID = "990000200002290028"
    INVALID_DATE_ID = "990000200102290017"

    def setUp(self):
        self.admin = User.objects.create_superuser("admin")

    def authenticate(self, user):
        token = AccessToken.for_user(user)
        token["account_type"] = user.account_type
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def create_founder(self, **values):
        self.authenticate(self.admin)
        return self.client.post(self.ADMIN_USERS, {
            "username": "01234567890",
            "display_name": "李 · O’Neil-Éva",
            "id_number": f" {self.VALID_ID.lower()} ",
            "business_role": User.Role.FOUNDER,
            **values,
        }, format="json")

    def test_admin_creation_normalizes_id_accepts_unicode_and_has_no_carrier_filter(self):
        response = self.create_founder()
        self.assertEqual(response.status_code, 201, response.data)
        user = User.objects.get(username="01234567890")
        self.assertEqual((user.display_name, user.id_number), ("李 · O’Neil-Éva", self.VALID_ID))
        self.assertNotIn("id_number", response.data["data"])

        self.authenticate(user)
        for name in ("李", "É" * 50):
            response = self.client.patch(self.APP_ME, {"display_name": name}, format="json")
            self.assertEqual(response.status_code, 200, response.data)
            user.refresh_from_db()
            self.assertEqual((user.display_name, user.id_number, user.username),
                             (name, self.VALID_ID, "01234567890"))

    def test_both_endpoints_enforce_field_specific_format_and_leave_data_unchanged(self):
        invalid_fields = (
            ("display_name", "李1"), ("display_name", "安🙂"),
            ("display_name", " "), ("display_name", "安" * 51),
            ("username", "1234567890"), ("username", "123456789012"),
            ("username", "1234567890x"), ("username", "１２３４５６７８９０１"),
            ("id_number", self.VALID_ID[:-1]), ("id_number", self.VALID_ID[:-1] + "0"),
            ("id_number", self.INVALID_DATE_ID),
        )
        for field, value in invalid_fields:
            with self.subTest(endpoint="admin", field=field, value=value):
                response = self.create_founder(**{field: value})
                self.assertEqual(response.status_code, 400, response.data)
                self.assertIn(field, response.data["data"])
        self.assertFalse(User.objects.filter(account_type=User.AccountType.BUSINESS).exists())

        response = self.create_founder()
        self.assertEqual(response.status_code, 201, response.data)
        user = User.objects.get(username="01234567890")
        self.authenticate(user)
        original = (user.display_name, user.id_number, user.username)
        for field, value in invalid_fields:
            with self.subTest(endpoint="app", field=field, value=value):
                data = {"display_name": "新姓名", "username": "22345678901", field: value}
                response = self.client.patch(self.APP_ME, data, format="json")
                self.assertEqual(response.status_code, 400, response.data)
                self.assertIn(field, response.data["data"])
                user.refresh_from_db()
                self.assertEqual((user.display_name, user.id_number, user.username), original)
        self.assertFalse(AppOperationAuditLog.objects.filter(actor=user, module="profile").exists())

    def test_disabled_accounts_reserve_phone_and_normalized_identity(self):
        disabled = User.objects.create_user(
            username="11234567890", display_name="停用创始人", id_number=self.OTHER_ID,
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER, is_active=False,
        )
        response = self.create_founder(username=disabled.username, id_number=f" {self.OTHER_ID} ")
        self.assertEqual(response.status_code, 400, response.data)
        self.assertEqual(set(response.data["data"]), {"username", "id_number"})

        response = self.create_founder()
        self.assertEqual(response.status_code, 201, response.data)
        user = User.objects.get(username="01234567890")
        self.authenticate(user)
        for field, value in (("username", disabled.username), ("id_number", self.OTHER_ID)):
            response = self.client.patch(self.APP_ME, {field: value}, format="json")
            self.assertEqual(response.status_code, 400, response.data)
            self.assertIn("已被使用", str(response.data["data"][field]))
        self.assertEqual(self.client.patch(self.APP_ME, {
            "username": user.username, "id_number": f" {self.VALID_ID.lower()} ",
        }, format="json").status_code, 200)

    def test_patch_does_not_revalidate_unsubmitted_legacy_values(self):
        user = User.objects.create_user(
            username="legacy-founder", display_name="旧姓名1", id_number="LEGACY-ID",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        self.authenticate(user)
        response = self.client.patch(self.APP_ME, {"display_name": "新姓名"}, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        user.refresh_from_db()
        self.assertEqual((user.display_name, user.username, user.id_number),
                         ("新姓名", "legacy-founder", "LEGACY-ID"))
