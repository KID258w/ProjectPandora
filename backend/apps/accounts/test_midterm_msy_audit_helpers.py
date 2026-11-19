from django.test import SimpleTestCase

from .app_audit import _changed_keys, _sanitized_values, is_sensitive_field
from .audit import redact
from .models import AppOperationAuditLog


class MidtermMsyAuditHelperTests(SimpleTestCase):
    def test_redact_keeps_ordinary_values(self):
        self.assertEqual(redact({"title": "安排", "count": 2}), {"title": "安排", "count": 2})

    def test_redact_masks_sensitive_nested_values(self):
        self.assertEqual(redact({"profile": {"password": "x", "id_number": "y"}}),
                         {"profile": {"password": "***", "id_number": "***"}})

    def test_redact_handles_lists_and_none(self):
        self.assertEqual(redact([{"access_token": "x"}, None]), [{"access_token": "***"}, None])

    def test_sensitive_field_recognizes_credentials_and_identity(self):
        for field in ("password", "id_number", "access_token", "secret_key", "avatar"):
            with self.subTest(field=field):
                self.assertTrue(is_sensitive_field(field))

    def test_sensitive_field_allows_business_fields(self):
        for field in ("title", "description", "tag_ids"):
            with self.subTest(field=field):
                self.assertFalse(is_sensitive_field(field))

    def test_changed_keys_detects_updates_and_additions_once(self):
        self.assertEqual(_changed_keys({"title": "A", "x": 1}, {"title": "B", "y": 2}), ["title", "x", "y"])

    def test_changed_keys_empty_or_equal(self):
        self.assertEqual(_changed_keys(None, None), [])
        self.assertEqual(_changed_keys({"title": "A"}, {"title": "A"}), [])

    def test_profile_snapshot_keeps_allowlisted_values_only(self):
        self.assertEqual(_sanitized_values({"display_name": "小明", "password": "secret", "id_number": "full", "unsupported": "x"}, AppOperationAuditLog.Module.PROFILE),
                         {"display_name": "小明"})

    def test_empty_or_unknown_module_snapshot_is_none(self):
        self.assertIsNone(_sanitized_values({}, AppOperationAuditLog.Module.PROFILE))
        self.assertIsNone(_sanitized_values({"title": "A"}, "unknown"))
