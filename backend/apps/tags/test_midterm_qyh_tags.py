from datetime import datetime, timezone
from types import SimpleNamespace
from unittest.mock import patch

from django.db import IntegrityError
from rest_framework.exceptions import ValidationError
from rest_framework.test import APITestCase

from apps.accounts.models import AppOperationAuditLog, Department, Team, User
from apps.logs.models import DailyLog, DailyLogTag
from apps.tasks.models import Task

from .models import PALETTE, Tag, TaskTag
from .services import validate_tag_ids
from .views import founder_only


class TagsMidtermTests(APITestCase):
    url = "/api/v1/app/tags/"

    @classmethod
    def setUpTestData(cls):
        department = Department.objects.create(name="研发")
        team = Team.objects.create(name="服务端", department=department)
        cls.founder = User.objects.create(username="founder", business_role=User.Role.FOUNDER)
        cls.manager = User.objects.create(
            username="manager", business_role=User.Role.DEPARTMENT_MANAGER,
            department=department, manager=cls.founder,
        )
        cls.leader = User.objects.create(
            username="leader", business_role=User.Role.TEAM_LEADER,
            department=department, team=team, manager=cls.manager,
        )
        cls.employee = User.objects.create(
            username="employee", business_role=User.Role.EMPLOYEE,
            department=department, team=team, manager=cls.leader,
        )

    def setUp(self):
        self.client.force_authenticate(user=self.founder)

    def tag(self, name, *, active=True):
        return Tag.objects.create(name=name, color=PALETTE[0], is_active=active)

    def detail(self, tag):
        return f"{self.url}{tag.pk}/"

    def response_data(self, response, status=200):
        self.assertEqual(response.status_code, status, response.data)
        return response.data["data"]

    def test_founder_gate_accepts_founder(self):
        founder_only(SimpleNamespace(user=self.founder))

    def test_founder_gate_denies_each_managerial_role(self):
        self.tag("停用", active=False)
        for actor in (self.manager, self.leader):
            with self.subTest(actor=actor):
                self.client.force_authenticate(user=actor)
                denied = self.response_data(self.client.get(self.url, {"include_inactive": "true"}), 403)
                self.assertNotIn("results", denied)

    def test_founder_gate_blocks_employee_mutations_without_side_effects(self):
        tag = self.tag("原标签")
        self.client.force_authenticate(user=self.employee)
        self.response_data(self.client.post(self.url, {"name": "越权", "color": PALETTE[0]}, format="json"), 403)
        self.response_data(self.client.patch(self.detail(tag), {"name": "越权"}, format="json"), 403)
        self.response_data(self.client.delete(self.detail(tag)), 403)
        tag.refresh_from_db()
        self.assertEqual((tag.name, tag.is_active, Tag.objects.count()), ("原标签", True, 1))
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_tag_list_employee_searches_only_active_tags(self):
        matching = self.tag("前端开发")
        self.tag("开发历史", active=False)
        self.tag("设计")
        self.client.force_authenticate(user=self.employee)
        data = self.response_data(self.client.get(self.url, {"keyword": "  开发  "}))
        self.assertEqual((data["count"], [row["id"] for row in data["results"]]), (1, [matching.pk]))

    def test_tag_list_founder_switch_includes_inactive_with_status(self):
        tag = self.tag("仅存历史", active=False)
        self.assertEqual(self.response_data(self.client.get(self.url))["results"], [])
        data = self.response_data(self.client.get(self.url, {"include_inactive": "true"}))
        self.assertEqual([(row["id"], row["is_active"]) for row in data["results"]], [(tag.pk, False)])

    def test_tag_list_rejects_bad_flag_and_employee_management_query(self):
        self.client.force_authenticate(user=self.employee)
        self.assertIn("include_inactive", self.response_data(self.client.get(self.url, {"include_inactive": "yes"}), 400))
        self.response_data(self.client.get(self.url, {"include_inactive": "true"}), 403)

    def test_tag_create_trims_name_and_records_selected_color(self):
        data = self.response_data(self.client.post(
            self.url, {"name": "  设计  ", "color": PALETTE[-1]}, format="json",
        ), 201)
        tag = Tag.objects.get(pk=data["id"])
        self.assertEqual((tag.name, tag.color, tag.is_active), ("设计", PALETTE[-1], True))
        audit = AppOperationAuditLog.objects.get(action="create", object_id=str(tag.pk))
        self.assertEqual(audit.after_data["color"], PALETTE[-1])

    def test_tag_create_accepts_100_chars_and_reuses_inactive_name(self):
        name = "签" * 100
        historical = self.tag(name, active=False)
        data = self.response_data(self.client.post(self.url, {"name": name, "color": PALETTE[0]}, format="json"), 201)
        self.assertNotEqual(data["id"], historical.pk)
        self.assertEqual(data["name"], name)
        self.assertCountEqual(Tag.objects.filter(name=name).values_list("is_active", flat=True), [False, True])

    def test_tag_create_rejects_duplicate_bad_color_empty_and_oversize_name(self):
        self.tag("重名")
        for fields, status in (
            ({"name": " 重名 "}, 409), ({"color": "#000000"}, 400),
            ({"name": "  "}, 400), ({"name": "签" * 101}, 400),
        ):
            with self.subTest(fields=fields):
                payload = {"name": "新标签", "color": PALETTE[0], **fields}
                self.response_data(self.client.post(self.url, payload, format="json"), status)
        self.assertEqual(list(Tag.objects.values_list("name", flat=True)), ["重名"])
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_tag_patch_updates_existing_links_without_replacing_tag(self):
        tag = self.tag("旧")
        today = datetime(2026, 11, 1, 2, tzinfo=timezone.utc)
        log = DailyLog.objects.create(author=self.founder, log_date=today.date())
        log_link = DailyLogTag.objects.create(daily_log=log, tag=tag)
        task = Task.objects.create(
            creator=self.founder, assignee=self.founder, title="任务", due_at=today,
            approval_status=Task.Approval.APPROVED,
        )
        task_link = TaskTag.objects.create(task=task, tag=tag)
        data = self.response_data(self.client.patch(
            self.detail(tag), {"name": "新", "color": PALETTE[1]}, format="json",
        ))
        self.assertEqual((data["id"], data["name"], data["color"]), (tag.pk, "新", PALETTE[1]))
        self.assertEqual(DailyLogTag.objects.get(pk=log_link.pk).tag.color, PALETTE[1])
        self.assertEqual(TaskTag.objects.get(pk=task_link.pk).tag.name, "新")
        audit = AppOperationAuditLog.objects.get(action="update", object_id=str(tag.pk))
        self.assertEqual(audit.before_data, {"name": "旧", "color": PALETTE[0]})

    def test_tag_patch_same_normalized_value_is_noop(self):
        tag = self.tag("原")
        updated_at = tag.updated_at
        data = self.response_data(self.client.patch(self.detail(tag), {"name": " 原 "}, format="json"))
        tag.refresh_from_db()
        self.assertEqual((data["name"], tag.updated_at), ("原", updated_at))
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_tag_patch_leaves_data_intact_on_conflict_or_audit_failure(self):
        tag = self.tag("原")
        self.tag("重名")
        inactive = self.tag("停用", active=False)
        self.response_data(self.client.patch(
            self.detail(tag), {"name": "重名", "color": PALETTE[1]}, format="json",
        ), 409)
        self.response_data(self.client.patch(self.detail(inactive), {"name": "禁止"}, format="json"), 400)
        with patch("apps.tags.views.record_app_operation", side_effect=IntegrityError("forced audit failure")):
            response = self.client.patch(self.detail(tag), {"color": PALETTE[1]}, format="json")
            self.assertGreaterEqual(response.status_code, 400, response.data)
        tag.refresh_from_db()
        inactive.refresh_from_db()
        self.assertEqual((tag.name, tag.color, inactive.name), ("原", PALETTE[0], "停用"))
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_tag_delete_disables_but_keeps_historical_links(self):
        tag = self.tag("历史")
        today = datetime(2026, 11, 1, 2, tzinfo=timezone.utc)
        log = DailyLog.objects.create(author=self.founder, log_date=today.date())
        DailyLogTag.objects.create(daily_log=log, tag=tag)
        task = Task.objects.create(
            creator=self.founder, assignee=self.founder, title="历史任务", due_at=today,
            approval_status=Task.Approval.APPROVED,
        )
        TaskTag.objects.create(task=task, tag=tag)
        self.assertEqual(self.response_data(self.client.delete(self.detail(tag))), {"id": tag.pk, "is_active": False})
        tag.refresh_from_db()
        self.assertFalse(tag.is_active)
        self.assertTrue(TaskTag.objects.filter(task=task, tag=tag).exists())
        data = self.response_data(self.client.get(f"/api/v1/app/logs/{log.pk}/"))
        self.assertEqual(data["tags"], [{"id": tag.pk, "name": "历史", "color": PALETTE[0]}])
        self.assertEqual(AppOperationAuditLog.objects.filter(action="deactivate", object_id=str(tag.pk)).count(), 1)

    def test_tag_delete_repeated_request_does_not_write_twice(self):
        tag = self.tag("重复")
        first = self.response_data(self.client.delete(self.detail(tag)))
        tag.refresh_from_db()
        updated_at = tag.updated_at
        self.assertEqual(self.response_data(self.client.delete(self.detail(tag))), first)
        tag.refresh_from_db()
        self.assertEqual(tag.updated_at, updated_at)
        self.assertEqual(AppOperationAuditLog.objects.filter(action="deactivate").count(), 1)

    def test_tag_delete_rejects_unknown_id_without_writes(self):
        tag = self.tag("保留")
        self.response_data(self.client.delete(f"{self.url}{tag.pk + 1}/"), 400)
        self.assertTrue(Tag.objects.filter(pk=tag.pk, is_active=True).exists())
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_tag_ids_preserve_input_order_for_active_tags(self):
        first = self.tag("先创建")
        second = self.tag("后创建")
        self.assertEqual(validate_tag_ids([second.pk, first.pk]), [second, first])

    def test_tag_ids_allow_empty_and_retention_of_existing_inactive(self):
        tag = self.tag("历史", active=False)
        self.assertEqual(validate_tag_ids([], {tag.pk}), [])
        self.assertEqual(validate_tag_ids([tag.pk], {tag.pk}), [tag])

    def test_tag_ids_reject_duplicates_missing_and_readding_inactive(self):
        active = self.tag("启用")
        inactive = self.tag("历史", active=False)
        for ids in ([active.pk, active.pk], [inactive.pk + 1], [inactive.pk]):
            with self.subTest(ids=ids), self.assertRaises(ValidationError) as error:
                validate_tag_ids(ids)
            self.assertIn("tag_ids", error.exception.detail)

        now = datetime(2026, 11, 1, 2, tzinfo=timezone.utc)
        log = DailyLog.objects.create(author=self.founder, log_date=now.date(), completed_work="已做")
        DailyLogTag.objects.create(daily_log=log, tag=inactive)
        task = Task.objects.create(
            creator=self.founder, assignee=self.founder, title="历史任务", due_at=now,
            approval_status=Task.Approval.APPROVED,
        )
        TaskTag.objects.create(task=task, tag=inactive)
        task_url = f"/api/v1/app/tasks/{task.pk}/"
        log_url = f"/api/v1/app/logs/mine/{log.log_date}/"
        with patch("apps.logs.views.timezone.localdate", return_value=log.log_date):
            self.response_data(self.client.put(log_url, {"tag_ids": []}, format="json"))
            denied_log = self.response_data(self.client.put(log_url, {"tag_ids": [inactive.pk]}, format="json"), 400)
        self.response_data(self.client.patch(task_url, {"tag_ids": []}, format="json"))
        denied_task = self.response_data(self.client.patch(task_url, {"tag_ids": [inactive.pk]}, format="json"), 400)
        self.assertIn("tag_ids", denied_log)
        self.assertIn("tag_ids", denied_task)
        self.assertFalse(log.tag_links.exists())
        self.assertFalse(task.tag_links.exists())
