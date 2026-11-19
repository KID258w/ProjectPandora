from django.test import SimpleTestCase, TestCase
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from .app_audit import _changed_keys, _sanitized_values, is_sensitive_field
from .audit import record_audit, redact
from .models import AdminAuditLog, AppOperationAuditLog, User


class MidtermMsyAuditHelperTests(SimpleTestCase):
    def test_redact_keeps_ordinary_values(self):
        self.assertEqual(redact({"title": "安排", "count": 2}), {"title": "安排", "count": 2})

    def test_redact_masks_sensitive_nested_values(self):
        self.assertEqual(redact({"profile": {"password": "x", "id_number": "y"}}),
                         {"profile": {"password": "***", "id_number": "***"}})

    def test_redact_handles_lists_and_none(self):
        self.assertEqual(redact([{"access_token": "x"}, None, ({"secret": "y"},)]),
                         [{"access_token": "***"}, None, [{"secret": "***"}]])

    def test_sensitive_field_recognizes_credentials_and_identity(self):
        for field in ("password", "id_number", "access_token", "secret_key", "avatar"):
            with self.subTest(field=field):
                self.assertTrue(is_sensitive_field(field))

    def test_sensitive_field_allows_business_fields(self):
        for field in ("title", "description", "tag_ids"):
            with self.subTest(field=field):
                self.assertFalse(is_sensitive_field(field))

    def test_sensitive_field_matching_is_case_insensitive_and_partial(self):
        self.assertTrue(is_sensitive_field("Refresh_TOKEN_value"))
        self.assertFalse(is_sensitive_field("title_value"))

    def test_changed_keys_detects_updates_and_additions_once(self):
        self.assertEqual(_changed_keys({"title": "A", "x": 1}, {"title": "B", "y": 2}), ["title", "x", "y"])

    def test_changed_keys_empty_or_equal(self):
        self.assertEqual(_changed_keys(None, None), [])
        self.assertEqual(_changed_keys({"title": "A"}, {"title": "A"}), [])

    def test_changed_keys_handles_none_sides_and_nested_value_changes(self):
        self.assertEqual(_changed_keys(None, {"metadata": {"count": 1}}), ["metadata"])
        self.assertEqual(_changed_keys({"metadata": {"count": 1}}, {"metadata": {"count": 2}}), ["metadata"])

    def test_profile_snapshot_keeps_allowlisted_values_only(self):
        self.assertEqual(_sanitized_values({"display_name": "小明", "password": "secret", "id_number": "full", "unsupported": "x"}, AppOperationAuditLog.Module.PROFILE),
                         {"display_name": "小明"})

    def test_empty_or_unknown_module_snapshot_is_none(self):
        self.assertIsNone(_sanitized_values({}, AppOperationAuditLog.Module.PROFILE))
        self.assertIsNone(_sanitized_values({"title": "A"}, "unknown"))


class MidtermMsyRecordAdminAuditTests(TestCase):
    def setUp(self):
        self.actor = User.objects.create_superuser(username="audit-admin", password="test-password")

    def test_record_audit_persists_normal_before_and_after_values(self):
        record_audit(actor=self.actor, module=AdminAuditLog.Module.COMPANY_ITEMS,
                     action="update", object_type="company_item", object_id=17,
                     before={"title": "旧"}, after={"title": "新"})
        row = AdminAuditLog.objects.get(object_id="17")
        self.assertEqual((row.object_id, row.result), ("17", "success"))
        self.assertEqual(row.before_data, {"title": "旧"})
        self.assertEqual(row.after_data, {"title": "新"})

    def test_record_audit_allows_empty_optional_snapshots_and_failure_result(self):
        record_audit(actor=self.actor, module=AdminAuditLog.Module.USERS,
                     action="delete", object_type="user", result="failure")
        row = AdminAuditLog.objects.get(action="delete")
        self.assertIsNone(row.before_data)
        self.assertIsNone(row.after_data)
        self.assertEqual(row.result, "failure")

    def test_record_audit_redacts_nested_sensitive_values_before_persisting(self):
        record_audit(actor=self.actor, module=AdminAuditLog.Module.USERS,
                     action="update", object_type="user",
                     after={"profile": {"id_card": "private", "display_name": "小明"}})
        row = AdminAuditLog.objects.get(action="update")
        row.refresh_from_db()
        self.assertEqual(row.after_data, {"profile": {"id_card": "***", "display_name": "小明"}})


class MidtermMsyAuditLogFilterTests(APITestCase):
    def setUp(self):
        self.admin = User.objects.create_superuser(username="filter-admin", password="test-password")
        token = AccessToken.for_user(self.admin)
        token["account_type"] = self.admin.account_type
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def make_log(self, action, module=AdminAuditLog.Module.USERS, object_id="1", day="2026-11-18"):
        row = AdminAuditLog.objects.create(actor=self.admin, module=module, action=action,
                                           object_type="user", object_id=object_id)
        AdminAuditLog.objects.filter(pk=row.pk).update(created_at=f"{day}T12:00:00+08:00")
        return row

    def test_audit_log_queryset_filters_by_module_actor_and_date_interval(self):
        first = self.make_log("first", object_id="101", day="2026-11-18")
        self.make_log("second", module=AdminAuditLog.Module.DEPARTMENTS, object_id="102", day="2026-11-19")
        response = self.client.get(
            f"/api/v1/admin/audit-logs/?module=users&actor_id={self.admin.pk}&start_date=2026-11-18&end_date=2026-11-18"
        )
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual([row["id"] for row in response.data["data"]["results"]], [first.pk])

    def test_audit_log_queryset_includes_both_date_boundaries(self):
        self.make_log("first", object_id="101", day="2026-11-18")
        self.make_log("middle", object_id="102", day="2026-11-19")
        last = self.make_log("last", object_id="103", day="2026-11-20")
        response = self.client.get("/api/v1/admin/audit-logs/?start_date=2026-11-18&end_date=2026-11-20")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["data"]["count"], 3)
        self.assertIn(last.pk, [row["id"] for row in response.data["data"]["results"]])

    def test_audit_log_queryset_rejects_invalid_date_or_nonpositive_actor_id(self):
        for query in ("start_date=not-a-date", "actor_id=0", "actor_id=abc"):
            with self.subTest(query=query):
                response = self.client.get(f"/api/v1/admin/audit-logs/?{query}")
                self.assertEqual(response.status_code, 400)
