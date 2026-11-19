from datetime import date, datetime, timezone as dt_timezone
from decimal import Decimal
from uuid import UUID

from django.test import TestCase

from apps.tags.models import Tag
from apps.tags.services import tag_name_snapshots

from .app_audit import (
    _json_safe, _sanitized_values, actor_snapshot, record_app_operation,
    user_reference_snapshot,
)
from .api_serializers import AppOperationLogDetailSerializer
from .models import AppOperationAuditLog, Department, Team, User


class MidtermMsyAuditSnapshotTests(TestCase):
    def setUp(self):
        self.department = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="平台组", department=self.department)
        self.actor = User.objects.create_user(
            username="msy", password="test-password", display_name="闵世宇",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=self.department, team=self.team,
        )

    # actor_snapshot: populated relations, nullable relations, and field-length bound.
    def test_actor_snapshot_captures_current_org_names_and_role(self):
        self.assertEqual(actor_snapshot(self.actor), {
            "actor_name_snapshot": "闵世宇",
            "actor_business_role_snapshot": User.Role.EMPLOYEE,
            "actor_department_id_snapshot": self.department.pk,
            "actor_department_name_snapshot": "研发",
            "actor_team_id_snapshot": self.team.pk,
            "actor_team_name_snapshot": "平台组",
        })

    def test_actor_snapshot_handles_missing_org_and_name(self):
        user = User.objects.create_superuser(username="no-org", password="x", display_name="")
        self.assertEqual(actor_snapshot(user), {
            "actor_name_snapshot": "no-org", "actor_business_role_snapshot": "",
            "actor_department_id_snapshot": None, "actor_department_name_snapshot": "",
            "actor_team_id_snapshot": None, "actor_team_name_snapshot": "",
        })

    def test_actor_snapshot_limits_name_to_model_field_length(self):
        self.actor.display_name = "名" * 150
        self.assertEqual(len(actor_snapshot(self.actor)["actor_name_snapshot"]), 100)

    # user_reference_snapshot: null, known IDs, explicit snapshots, and unknown IDs.
    def test_user_reference_snapshot_none_and_known_user_id(self):
        self.assertIsNone(user_reference_snapshot(None))
        self.assertEqual(user_reference_snapshot(self.actor.pk), {"id": self.actor.pk, "display_name": "闵世宇"})

    def test_user_reference_snapshot_prefers_supplied_historical_name(self):
        self.actor.display_name = "现在的名字"
        self.actor.save(update_fields=["display_name"])
        self.assertEqual(user_reference_snapshot({"id": str(self.actor.pk), "name": "操作时的名字"}),
                         {"id": self.actor.pk, "display_name": "操作时的名字"})

    def test_user_reference_snapshot_handles_unknown_or_malformed_id(self):
        self.assertEqual(user_reference_snapshot(999999), {"id": 999999, "display_name": "未知成员"})
        self.assertEqual(user_reference_snapshot({"id": "bad"}), {"id": "bad", "display_name": "未知成员"})

    # tag_name_snapshots: already snapshotted, legacy IDs, unknown and malformed entries.
    def test_tag_name_snapshots_resolves_legacy_ids_and_preserves_order(self):
        first = Tag.objects.create(name="紧急", color="#C7DDFF")
        second = Tag.objects.create(name="跟进", color="#BDEAF2")
        self.assertEqual(tag_name_snapshots([second.pk, first.pk]), [
            {"id": second.pk, "name": "跟进"}, {"id": first.pk, "name": "紧急"},
        ])

    def test_tag_name_snapshots_preserves_existing_names_without_database_lookup(self):
        self.assertEqual(tag_name_snapshots([{"id": 81, "name": "历史标签"}, {"id": "x", "name": "自定义"}]), [
            {"id": 81, "name": "历史标签"}, {"id": "x", "name": "自定义"},
        ])

    def test_tag_name_snapshots_uses_fallbacks_for_unknown_and_missing_names(self):
        self.assertEqual(tag_name_snapshots([999999, {"id": "bad"}, {"id": None, "name": ""}]), [
            {"id": 999999, "name": "未知标签"}, {"id": "bad", "name": "未知标签"},
            {"id": None, "name": "未知标签"},
        ])

    # _json_safe: common scalar unchanged, supported non-JSON types, recursive containers.
    def test_json_safe_keeps_json_native_values(self):
        value = {"title": "计划", "count": 2, "enabled": True, "nothing": None}
        self.assertEqual(_json_safe(value), value)

    def test_json_safe_serializes_date_decimal_and_uuid(self):
        value = {"day": date(2026, 11, 19), "amount": Decimal("2.50"),
                 "id": UUID("12345678-1234-5678-1234-567812345678")}
        self.assertEqual(_json_safe(value), {
            "day": "2026-11-19", "amount": "2.50", "id": "12345678-1234-5678-1234-567812345678",
        })

    def test_json_safe_recurses_through_datetime_and_iterables(self):
        stamp = datetime(2026, 11, 19, 8, tzinfo=dt_timezone.utc)
        self.assertEqual(_json_safe((stamp, {"dates": {date(2026, 1, 1)}})),
                         [stamp.isoformat(), {"dates": ["2026-01-01"]}])

    # record_app_operation: inferred diffs, explicit deduplication, and allowlisted snapshots.
    def test_record_app_operation_infers_changed_fields_and_persists_actor_snapshot(self):
        row = record_app_operation(
            actor=self.actor, module="profile", action="update", object_type="user",
            object_id=self.actor.pk, before={"display_name": "旧名"}, after={"display_name": "闵世宇"},
        )
        self.assertEqual(row.changed_fields, ["display_name"])
        self.assertEqual(row.before_data, {"display_name": "旧名"})
        self.assertEqual(row.after_data, {"display_name": "闵世宇"})
        self.assertEqual(row.actor_department_name_snapshot, "研发")

    def test_record_app_operation_honors_explicit_changed_fields_once(self):
        row = record_app_operation(
            actor=self.actor, module="tasks", action="update",
            changed_fields=["title", "title", 7], before={}, after={},
        )
        self.assertEqual(row.changed_fields, ["title", "7"])

    def test_record_app_operation_drops_disallowed_and_sensitive_values(self):
        row = record_app_operation(
            actor=self.actor, module="auth", action="login",
            before={"password": "secret"}, after={"password": "secret", "token": "private"},
            object_type="t" * 50, object_id="i" * 100, object_label="l" * 250,
        )
        self.assertIsNone(row.before_data)
        self.assertIsNone(row.after_data)
        self.assertEqual((len(row.object_type), len(row.object_id), len(row.object_label_snapshot)), (32, 64, 200))

    # _sanitized_values allowlist branch: accepted nested values and empty snapshots.
    def test_sanitized_values_keeps_allowed_task_fields_and_converts_dates(self):
        value = _sanitized_values({"title": "计划", "due_at": date(2026, 11, 20), "ignored": "x"}, "tasks")
        self.assertEqual(value, {"title": "计划", "due_at": "2026-11-20"})

    def test_sanitized_values_snapshots_assignees_and_tags(self):
        tag = Tag.objects.create(name="重点", color="#C7DDFF")
        value = _sanitized_values({"assignee_id": self.actor.pk, "tag_ids": [tag.pk]}, "tasks")
        self.assertEqual(value, {"assignee_id": {"id": self.actor.pk, "display_name": "闵世宇"},
                                 "tag_ids": [{"id": tag.pk, "name": "重点"}]})

    def test_sanitized_values_removes_sensitive_allowlisted_keys_case_insensitively(self):
        self.assertIsNone(_sanitized_values({"PASSWORD": "secret", "id_number": "private"}, "profile"))

    # Detail serializer: empty, tag-name and assignee-name snapshots.
    def test_detail_serializer_keeps_null_snapshots(self):
        row = record_app_operation(actor=self.actor, module="auth", action="login")
        data = AppOperationLogDetailSerializer(row).data
        self.assertIsNone(data["before_data"])
        self.assertIsNone(data["after_data"])

    def test_detail_serializer_resolves_historical_tag_ids_to_names(self):
        tag = Tag.objects.create(name="已归档标签", color="#C7DDFF")
        row = record_app_operation(actor=self.actor, module="tasks", action="update",
                                   before={"tag_ids": [tag.pk]}, after={})
        self.assertEqual(AppOperationLogDetailSerializer(row).data["before_data"]["tag_ids"],
                         [{"id": tag.pk, "name": "已归档标签"}])

    def test_detail_serializer_resolves_assignee_reference_and_leaves_other_fields(self):
        row = record_app_operation(actor=self.actor, module="tasks", action="update",
                                   before={"assignee_id": self.actor.pk, "title": "任务"}, after={})
        self.assertEqual(AppOperationLogDetailSerializer(row).data["before_data"], {
            "assignee_id": {"id": self.actor.pk, "display_name": "闵世宇"}, "title": "任务",
        })

