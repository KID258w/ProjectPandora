import io
from datetime import date, timedelta
from unittest.mock import patch

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from django.utils import timezone
from PIL import Image
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from apps.logs.models import DailyLog
from apps.tags.models import PALETTE, Tag
from apps.tasks.models import Task

from .models import AppOperationAuditLog, Department, Team, User


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class AppOperationAuditApiTests(APITestCase):
    password = "Pandora-test-938!"
    today = date(2026, 11, 3)

    def setUp(self):
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        self.department = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="后端", department=self.department)
        self.founder = self.user("founder", User.Role.FOUNDER)
        self.manager = self.user("manager", User.Role.DEPARTMENT_MANAGER, self.department, manager=self.founder)
        self.leader = self.user("leader", User.Role.TEAM_LEADER, self.department, self.team, self.manager)
        self.employee = self.user("employee", User.Role.EMPLOYEE, self.department, self.team, self.leader)

    def user(self, name, role, department=None, team=None, manager=None):
        return User.objects.create_user(
            username=name, password=self.password, display_name=name,
            account_type=User.AccountType.BUSINESS, business_role=role,
            department=department, team=team, manager=manager,
        )

    def auth(self, user):
        token = AccessToken.for_user(user)
        token["account_type"] = user.account_type
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def records(self, **filters):
        return AppOperationAuditLog.objects.filter(**filters)

    def app_login(self, username, password=None):
        return self.client.post(
            "/api/v1/app/auth/token/",
            {"username": username, "password": password or self.password},
            format="json",
        )

    def due(self):
        return (timezone.now() + timedelta(days=5)).isoformat()

    def create_task(self, creator, assignee, **extra):
        self.auth(creator)
        return self.client.post("/api/v1/app/tasks/", {
            "assignee_id": assignee.pk, "title": "上线新接口", "description": "完成需求功能",
            "due_at": self.due(), **extra,
        }, format="json")

    def test_successful_login_records_snapshot_and_failure_does_not(self):
        self.client.credentials()
        response = self.app_login("employee")
        self.assertEqual(response.status_code, 200, response.data)

        log = self.records().get()
        self.assertEqual((log.module, log.action, log.result), ("auth", "login", "success"))
        self.assertEqual(log.actor_id, self.employee.pk)
        self.assertEqual(log.actor_name_snapshot, "employee")
        self.assertEqual(log.actor_business_role_snapshot, User.Role.EMPLOYEE)
        self.assertEqual(log.actor_department_id_snapshot, self.department.pk)
        self.assertEqual(log.actor_department_name_snapshot, "研发")
        self.assertEqual(log.actor_team_id_snapshot, self.team.pk)
        self.assertEqual(log.actor_team_name_snapshot, "后端")
        self.assertEqual(log.changed_fields, [])
        self.assertIsNone(log.before_data)
        self.assertIsNone(log.after_data)
        self.assertEqual(log.object_type, "")

        # 后续改名、调岗不改写历史快照
        self.employee.display_name = "改名后"
        self.employee.save(update_fields=["display_name"])
        log.refresh_from_db()
        self.assertEqual(log.actor_name_snapshot, "employee")

        # 失败登录不产生记录
        self.assertEqual(self.app_login("employee", password="wrong-password").status_code, 401)
        # 管理端登录也不产生员工 App 记录
        self.assertEqual(self.client.post(
            "/api/v1/admin/auth/token/", {"username": "admin", "password": self.password}, format="json",
        ).status_code, 200)
        self.assertEqual(self.records().count(), 1)

    def test_profile_changes_keep_field_names_and_redact_sensitive_values(self):
        self.auth(self.employee)
        new_id_number = "ID-NEW-0001"
        response = self.client.patch("/api/v1/app/me/", {
            "display_name": "张三", "username": "13900000001", "id_number": new_id_number,
        }, format="json")
        self.assertEqual(response.status_code, 200, response.data)

        log = self.records(module="profile").get()
        self.assertEqual(log.action, "update")
        self.assertEqual((log.object_type, log.object_id), ("user", str(self.employee.pk)))
        self.assertEqual(set(log.changed_fields), {"display_name", "username", "id_number"})
        self.assertEqual(log.before_data["display_name"], "employee")
        self.assertEqual(log.before_data["username"], "employee")
        self.assertEqual(log.after_data["display_name"], "张三")
        self.assertEqual(log.after_data["username"], "13900000001")
        self.assertNotIn("id_number", log.before_data)
        self.assertNotIn("id_number", log.after_data)
        self.assertNotIn(new_id_number, f"{log.before_data}{log.after_data}")

        new_password = "NewPandora-pass-42!"
        changed = self.client.post("/api/v1/app/me/password/", {
            "current_password": self.password,
            "new_password": new_password,
            "new_password_confirm": new_password,
        }, format="json")
        self.assertEqual(changed.status_code, 200, changed.data)
        password_log = self.records(module="profile").exclude(pk=log.pk).get()
        self.assertEqual(password_log.changed_fields, ["password"])
        self.assertIsNone(password_log.before_data)
        self.assertIsNone(password_log.after_data)
        self.assertNotIn(new_password, str(password_log.__dict__))

        buffer = io.BytesIO()
        Image.new("RGB", (2, 2), "white").save(buffer, format="PNG")
        upload = SimpleUploadedFile("avatar.png", buffer.getvalue(), content_type="image/png")
        avatar = self.client.put("/api/v1/app/me/avatar/", {"avatar": upload}, format="multipart")
        self.assertEqual(avatar.status_code, 204, avatar.data)
        avatar_log = self.records(module="profile").order_by("-id").first()
        self.assertEqual(avatar_log.changed_fields, ["avatar"])
        self.assertIsNone(avatar_log.before_data)
        self.assertIsNone(avatar_log.after_data)

    def test_log_operations_record_values_but_reads_do_not(self):
        clock = patch("apps.logs.views.timezone.localdate", return_value=self.today)
        clock.start()
        self.addCleanup(clock.stop)
        self.auth(self.employee)
        url = f"/api/v1/app/logs/mine/{self.today}/"
        self.assertEqual(self.client.get(url).status_code, 200)
        self.assertEqual(self.records().count(), 0)

        tag = Tag.objects.create(name="周报", color=PALETTE[0])
        created = self.client.put(url, {"completed_work": "接口开发", "tag_ids": [tag.pk]}, format="json")
        self.assertEqual(created.status_code, 200, created.data)
        log_row = self.records().get()
        self.assertEqual((log_row.module, log_row.action, log_row.object_type), ("logs", "create", "daily_log"))
        self.assertEqual(log_row.object_id, str(created.data["data"]["id"]))
        self.assertEqual(log_row.object_label_snapshot, str(self.today))
        self.assertIsNone(log_row.before_data)
        self.assertEqual(log_row.after_data["completed_work"], "接口开发")
        self.assertEqual(log_row.after_data["tag_ids"], [tag.pk])

        # 无实际变更的保存不产生记录
        self.client.put(url, {"completed_work": "接口开发"}, format="json")
        self.assertEqual(self.records().count(), 1)

        self.client.put(url, {"completed_work": "接口开发与测试"}, format="json")
        updated = self.records(action="update").order_by("-id").first()
        self.assertEqual(updated.changed_fields, ["completed_work"])
        self.assertEqual(updated.before_data, {"completed_work": "接口开发"})
        self.assertEqual(updated.after_data, {"completed_work": "接口开发与测试"})

        submitted = self.client.post(url + "submit/", {}, format="json")
        self.assertEqual(submitted.status_code, 200, submitted.data)
        submit_row = self.records(action="submit").get()
        self.assertEqual(submit_row.changed_fields, ["status"])
        self.assertEqual(submit_row.after_data, {"status": "submitted"})

        self.assertEqual(self.client.get(f"/api/v1/app/logs/{log_row.object_id}/").status_code, 200)
        comment = self.client.post(f"/api/v1/app/logs/{log_row.object_id}/comments/", {"content": "收到"}, format="json")
        self.assertEqual(comment.status_code, 201, comment.data)
        comment_row = self.records(action="comment").get()
        self.assertEqual(comment_row.object_type, "daily_log_comment")
        self.assertEqual(comment_row.after_data, {"content": "收到"})

    def test_task_operations_record_actor_target_and_values(self):
        created = self.create_task(self.manager, self.leader)
        self.assertEqual(created.status_code, 201, created.data)
        task = Task.objects.get(pk=created.data["data"]["id"])

        create_row = self.records(module="tasks", action="create").get()
        self.assertEqual(create_row.actor_id, self.manager.pk)
        self.assertEqual(create_row.object_type, "task")
        self.assertEqual(create_row.object_id, str(task.pk))
        self.assertEqual(create_row.object_label_snapshot, "上线新接口")
        self.assertIsNone(create_row.before_data)
        self.assertEqual(create_row.after_data["title"], "上线新接口")
        self.assertEqual(create_row.after_data["assignee_id"], self.leader.pk)

        # 创建人修改任务定义
        edited = self.client.patch(f"/api/v1/app/tasks/{task.pk}/", {"title": "上线新接口 v2"}, format="json")
        self.assertEqual(edited.status_code, 200, edited.data)
        update_row = self.records(module="tasks", action="update").get()
        self.assertEqual(update_row.changed_fields, ["title"])
        self.assertEqual(update_row.before_data, {"title": "上线新接口"})
        self.assertEqual(update_row.after_data, {"title": "上线新接口 v2"})

        # 直属上级审批通过
        self.auth(self.founder)
        approved = self.client.post(f"/api/v1/app/tasks/{task.pk}/approval/", {
            "decision": "approved", "comment": "同意",
        }, format="json")
        self.assertEqual(approved.status_code, 200, approved.data)
        approve_row = self.records(module="tasks", action="approve").get()
        self.assertEqual(approve_row.actor_id, self.founder.pk)
        self.assertEqual(approve_row.after_data["decision"], "approved")
        self.assertEqual(approve_row.after_data["comment"], "同意")

        # 负责人追加进展
        self.auth(self.leader)
        progressed = self.client.post(f"/api/v1/app/tasks/{task.pk}/updates/", {"content": "已完成 50%"}, format="json")
        self.assertEqual(progressed.status_code, 201, progressed.data)
        progress_row = self.records(module="tasks", action="progress").get()
        self.assertEqual(progress_row.object_type, "task_update")
        self.assertEqual(progress_row.after_data, {"content": "已完成 50%"})

        # 提交完成说明
        submitted = self.client.post(f"/api/v1/app/tasks/{task.pk}/submissions/", {
            "completion_note": "已完成", "materials_note": "材料在共享盘",
        }, format="json")
        self.assertEqual(submitted.status_code, 201, submitted.data)
        submission_row = self.records(module="tasks", action="submit").get()
        self.assertEqual(submission_row.object_type, "task_submission")
        self.assertEqual(submission_row.after_data["completion_note"], "已完成")
        self.assertEqual(submission_row.after_data["materials_note"], "材料在共享盘")

        # 直接派发者验收
        self.auth(self.manager)
        reviewed = self.client.post(
            f"/api/v1/app/tasks/{task.pk}/submissions/{submitted.data['data']['id']}/review/",
            {"decision": "accepted"}, format="json",
        )
        self.assertEqual(reviewed.status_code, 200, reviewed.data)
        accept_row = self.records(module="tasks", action="accept").get()
        self.assertEqual(accept_row.after_data["review_status"], "accepted")
        self.assertEqual(accept_row.before_data["review_status"], "waiting")

        # 取消另一条未完成任务
        cancellable = Task.objects.get(pk=self.create_task(self.manager, self.leader).data["data"]["id"])
        self.auth(self.manager)
        cancelled = self.client.post(f"/api/v1/app/tasks/{cancellable.pk}/cancel/", {"reason": "计划调整"}, format="json")
        self.assertEqual(cancelled.status_code, 200, cancelled.data)
        cancel_row = self.records(module="tasks", action="cancel").get()
        self.assertEqual(cancel_row.before_data, {"lifecycle_status": "active"})
        self.assertEqual(cancel_row.after_data["cancellation_reason"], "计划调整")

    def test_task_return_and_resubmit_are_recorded(self):
        task = Task.objects.get(pk=self.create_task(self.manager, self.leader).data["data"]["id"])
        self.auth(self.founder)
        returned = self.client.post(f"/api/v1/app/tasks/{task.pk}/approval/", {
            "decision": "returned", "comment": "信息不足",
        }, format="json")
        self.assertEqual(returned.status_code, 200, returned.data)
        self.assertEqual(self.records(module="tasks", action="return").count(), 1)

        self.auth(self.manager)
        self.assertEqual(self.client.patch(
            f"/api/v1/app/tasks/{task.pk}/", {"description": "补充说明"}, format="json",
        ).status_code, 200)
        resubmitted = self.client.post(f"/api/v1/app/tasks/{task.pk}/resubmit/", {}, format="json")
        self.assertEqual(resubmitted.status_code, 200, resubmitted.data)
        resubmit_row = self.records(module="tasks", action="resubmit").get()
        self.assertEqual(resubmit_row.after_data["approval_status"], "pending")
        self.assertEqual(resubmit_row.changed_fields, ["approval_status", "approval_round"])

    def test_tag_operations_record_founder_changes_only(self):
        self.auth(self.founder)
        created = self.client.post("/api/v1/app/tags/", {"name": "复盘", "color": PALETTE[0]}, format="json")
        self.assertEqual(created.status_code, 201, created.data)
        tag_id = created.data["data"]["id"]
        self.assertEqual(self.client.patch(
            f"/api/v1/app/tags/{tag_id}/", {"color": PALETTE[1]}, format="json",
        ).status_code, 200)
        self.assertEqual(self.client.delete(f"/api/v1/app/tags/{tag_id}/").status_code, 200)

        rows = list(self.records(module="tags").order_by("id"))
        self.assertEqual([row.action for row in rows], ["create", "update", "deactivate"])
        self.assertEqual(rows[1].changed_fields, ["color"])
        self.assertEqual(rows[1].before_data, {"color": PALETTE[0]})
        self.assertEqual(rows[1].after_data, {"color": PALETTE[1]})
        self.assertEqual(rows[2].after_data, {"is_active": False})

        self.auth(self.employee)
        self.assertEqual(self.client.post(
            "/api/v1/app/tags/", {"name": "越权", "color": PALETTE[0]}, format="json",
        ).status_code, 403)
        self.assertEqual(self.records(module="tags").count(), 3)

    def test_audit_write_failure_rolls_back_business_change(self):
        clock = patch("apps.logs.views.timezone.localdate", return_value=self.today)
        clock.start()
        self.addCleanup(clock.stop)
        self.auth(self.employee)
        with patch("apps.logs.views.record_app_operation", side_effect=RuntimeError("audit unavailable")):
            with self.assertRaises(RuntimeError):
                self.client.put(f"/api/v1/app/logs/mine/{self.today}/", {"completed_work": "不应保存"}, format="json")
        self.assertFalse(DailyLog.objects.exists())
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_schema_exposes_read_only_audit_endpoints(self):
        schema = self.client.get("/api/schema/", HTTP_ACCEPT="application/json").json()
        paths = schema["paths"]
        self.assertIn("/api/v1/admin/app-operation-logs/", paths)
        self.assertIn("/api/v1/admin/app-operation-logs/{id}/", paths)
        self.assertEqual(set(paths["/api/v1/admin/app-operation-logs/"]), {"get"})
        detail = paths["/api/v1/admin/app-operation-logs/{id}/"]
        self.assertEqual(set(detail), {"get"})
        self.assertIn("AppOperationLogDetail", str(detail["get"]["responses"]["200"]))
        summary = paths["/api/v1/admin/app-operation-logs/"]["get"]
        self.assertIn("AppOperationLogList", str(summary["responses"]["200"]))
        self.assertIn("actor_id", [row.get("name") for row in summary.get("parameters", [])])

    def test_admin_list_and_detail_are_read_only_uncached_and_snapshotted(self):
        self.client.credentials()
        self.assertEqual(self.app_login("employee").status_code, 200)
        self.auth(self.employee)
        self.assertEqual(self.client.patch("/api/v1/app/me/", {"display_name": "张三"}, format="json").status_code, 200)

        # 之后改资料和调岗，不影响已保存的快照
        User.objects.filter(pk=self.employee.pk).update(display_name="李四")
        Department.objects.filter(pk=self.department.pk).update(name="研发中心")
        Team.objects.filter(pk=self.team.pk).update(name="后端组")

        self.client.credentials()
        self.assertEqual(self.client.get("/api/v1/admin/app-operation-logs/").status_code, 401)
        self.auth(self.employee)
        self.assertEqual(self.client.get("/api/v1/admin/app-operation-logs/").status_code, 403)

        self.auth(self.admin)
        response = self.client.get("/api/v1/admin/app-operation-logs/")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response["Cache-Control"], "no-store")
        rows = response.data["data"]["results"]
        self.assertEqual(len(rows), 2)
        self.assertEqual([row["id"] for row in rows], sorted([row["id"] for row in rows], reverse=True))
        summary = rows[0]
        self.assertEqual((summary["module"], summary["action"]), ("profile", "update"))
        self.assertEqual(summary["actor_id"], self.employee.pk)
        self.assertEqual(summary["actor_name"], "张三")
        self.assertEqual(summary["actor_department_name"], "研发")
        self.assertEqual(summary["actor_team_name"], "后端")
        self.assertEqual(summary["changed_fields"], ["display_name"])
        self.assertNotIn("before_data", summary)
        self.assertNotIn("after_data", summary)
        self.assertIn("occurred_at", summary)

        filtered = self.client.get(f"/api/v1/admin/app-operation-logs/?actor_id={self.employee.pk}")
        self.assertEqual(filtered.status_code, 200)
        self.assertEqual(filtered.data["data"]["count"], 2)
        for invalid in ("0", "-1", "abc"):
            self.assertEqual(
                self.client.get(f"/api/v1/admin/app-operation-logs/?actor_id={invalid}").status_code, 400,
            )

        detail = self.client.get(f"/api/v1/admin/app-operation-logs/{summary['id']}/")
        self.assertEqual(detail.status_code, 200, detail.data)
        self.assertEqual(detail["Cache-Control"], "no-store")
        self.assertEqual(detail.data["data"]["before_data"], {"display_name": "employee"})
        self.assertEqual(detail.data["data"]["after_data"], {"display_name": "张三"})
        self.assertEqual(detail.data["data"]["actor_name"], "张三")
        self.assertEqual(self.client.get("/api/v1/admin/app-operation-logs/999999/").status_code, 404)
        self.assertEqual(self.client.post("/api/v1/admin/app-operation-logs/", {}, format="json").status_code, 405)
        self.assertEqual(self.client.delete(f"/api/v1/admin/app-operation-logs/{summary['id']}/").status_code, 405)
