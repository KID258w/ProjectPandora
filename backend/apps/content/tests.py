import shutil
import tempfile

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from apps.accounts.models import AdminAuditLog, Department, Team, User

from .models import CompanyImportantItem, CompanyItemAttachment


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class CompanyItemApiTests(APITestCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.media_root = tempfile.mkdtemp(prefix="pandora-media-")
        cls.addClassCleanup(shutil.rmtree, cls.media_root, ignore_errors=True)
        cls.media_override = override_settings(MEDIA_ROOT=cls.media_root)
        cls.media_override.enable()
        cls.addClassCleanup(cls.media_override.disable)

    def setUp(self):
        self.admin = User.objects.create_superuser(username="admin", password="Pandora-test-938!")
        self.department = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="后端", department=self.department)
        self.founder = self.business_user("founder", User.Role.FOUNDER)
        self.manager = self.business_user("manager", User.Role.DEPARTMENT_MANAGER, self.department, manager=self.founder)
        self.leader = self.business_user("leader", User.Role.TEAM_LEADER, self.department, self.team, self.manager)
        self.employee = self.business_user("employee", User.Role.EMPLOYEE, self.department, self.team, self.leader)
        self.auth(self.admin)

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

    def create_item(self, **extra):
        payload = {"title": "国庆值班安排", "summary": "查看值班表", "body": "各部门值班安排详见附件。", **extra}
        return self.client.post("/api/v1/admin/company-items/", payload, format="json")

    def publish(self, item_id, expected=200, **extra):
        return self.client.post(f"/api/v1/admin/company-items/{item_id}/publish/", extra, format="json")

    def published_item(self, title="已发布事项", sort_order=None):
        item = CompanyImportantItem.objects.create(
            title=title, summary="摘要", body="正文",
            status=CompanyImportantItem.Status.PUBLISHED, sort_order=sort_order or 0,
        )
        return item

    # ---- 管理端 CRUD 与权限 ----

    def test_admin_creates_draft_and_business_user_cannot_see_it(self):
        created = self.create_item()
        self.assertEqual(created.status_code, 201, created.data)
        item_id = created.data["data"]["id"]
        self.assertEqual(created.data["data"]["status"], "draft")

        listed = self.client.get("/api/v1/admin/company-items/").data["data"]
        self.assertEqual(listed["count"], 1)

        self.auth(self.employee)
        self.assertEqual(self.client.get("/api/v1/app/company-items/").data["data"]["count"], 0)
        self.assertEqual(self.client.get(f"/api/v1/app/company-items/{item_id}/").status_code, 404)

    def test_business_user_cannot_manage_items_and_admin_cannot_use_app_api(self):
        self.auth(self.employee)
        self.assertEqual(self.create_item().status_code, 403)
        self.assertEqual(self.client.get("/api/v1/admin/company-items/").status_code, 403)
        self.auth(self.admin)
        self.assertEqual(self.client.get("/api/v1/app/company-items/").status_code, 403)

    def test_patch_edits_fields_and_records_audit(self):
        item_id = self.create_item().data["data"]["id"]
        updated = self.client.patch(
            f"/api/v1/admin/company-items/{item_id}/", {"summary": "新的摘要"}, format="json",
        )
        self.assertEqual(updated.status_code, 200, updated.data)
        self.assertEqual(updated.data["data"]["summary"], "新的摘要")
        self.assertEqual(updated.data["data"]["title"], "国庆值班安排")
        self.assertEqual(self.client.patch(
            f"/api/v1/admin/company-items/{item_id}/", {}, format="json",
        ).status_code, 400)

        actions = list(AdminAuditLog.objects.filter(module=AdminAuditLog.Module.COMPANY_ITEMS)
                       .values_list("action", flat=True))
        self.assertIn("create", actions)
        self.assertIn("update", actions)

    def test_unknown_field_and_missing_item(self):
        self.assertEqual(self.create_item(status="published").status_code, 400)
        self.assertEqual(self.client.get("/api/v1/admin/company-items/999999/").status_code, 404)

    # ---- 发布/下线规则 ----

    def test_publish_requires_required_fields(self):
        item = CompanyImportantItem.objects.create(
            title="", summary="摘要", body="正文", status=CompanyImportantItem.Status.DRAFT,
        )
        response = self.publish(item.pk, expected=400)
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["data"]["title"], "发布前必须填写该字段。")
        self.assertEqual(CompanyImportantItem.objects.get(pk=item.pk).status, "draft")

    def test_publish_caps_published_items_at_ten(self):
        for index in range(10):
            published = self.published_item(title=f"事项{index}", sort_order=index + 1)
            self.assertEqual(published.status, "published")
        eleventh = CompanyImportantItem.objects.create(
            title="第十一条", summary="摘要", body="正文",
        )
        response = self.publish(eleventh.pk, expected=400)
        self.assertEqual(response.status_code, 400)
        self.assertIn("先下线", str(response.data["data"]))
        self.assertEqual(CompanyImportantItem.objects.filter(status="published").count(), 10)

    def test_publish_sets_metadata_and_audit(self):
        item_id = self.create_item().data["data"]["id"]
        response = self.publish(item_id, sort_order=3)
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["data"]["status"], "published")
        self.assertEqual(response.data["data"]["sort_order"], 3)
        item = CompanyImportantItem.objects.get(pk=item_id)
        self.assertEqual(item.publisher, self.admin)
        self.assertIsNotNone(item.published_at)
        self.assertTrue(AdminAuditLog.objects.filter(action="publish", object_id=str(item_id)).exists())
        self.assertEqual(self.publish(item_id).status_code, 400)

    def test_offline_hides_item_and_keeps_record_for_republish(self):
        item_id = self.create_item().data["data"]["id"]
        self.publish(item_id)
        response = self.client.post(
            f"/api/v1/admin/company-items/{item_id}/offline/", {"reason": "内容已过期"}, format="json",
        )
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["data"]["status"], "offline")
        self.assertEqual(response.data["data"]["offline_reason"], "内容已过期")
        stored = CompanyImportantItem.objects.get(pk=item_id)
        self.assertIsNotNone(stored.offline_at)
        self.assertEqual(stored.offline_reason, "内容已过期")
        detail = self.client.get(f"/api/v1/admin/company-items/{item_id}/").data["data"]
        self.assertEqual(detail["offline_reason"], "内容已过期")

        self.auth(self.employee)
        self.assertEqual(self.client.get("/api/v1/app/company-items/").data["data"]["count"], 0)

        self.auth(self.admin)
        self.assertEqual(self.client.post(f"/api/v1/admin/company-items/{item_id}/offline/", {}, format="json").status_code, 400)
        self.assertEqual(self.publish(item_id).status_code, 200)
        republished = CompanyImportantItem.objects.get(pk=item_id)
        self.assertEqual(republished.offline_reason, "")
        self.assertIsNone(republished.offline_at)

    # ---- 排序 ----

    def test_order_endpoint_renumbers_and_drives_app_list_order(self):
        first = self.published_item(title="A", sort_order=1)
        second = self.published_item(title="B", sort_order=2)
        third = CompanyImportantItem.objects.create(title="C", summary="摘要", body="正文")

        response = self.client.put(
            "/api/v1/admin/company-items/order/",
            {"item_ids": [second.pk, first.pk]}, format="json",
        )
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(
            [(row["id"], row["sort_order"]) for row in response.data["data"]["items"]],
            [(second.pk, 1), (first.pk, 2)],
        )
        third.refresh_from_db()
        self.assertEqual(third.sort_order, 3)

        self.auth(self.employee)
        titles = [row["title"] for row in self.client.get("/api/v1/app/company-items/").data["data"]["results"]]
        self.assertEqual(titles, ["B", "A"])

    def test_order_rejects_duplicates_and_unknown_ids(self):
        item = self.published_item()
        self.assertEqual(self.client.put(
            "/api/v1/admin/company-items/order/", {"item_ids": [item.pk, item.pk]}, format="json",
        ).status_code, 400)
        self.assertEqual(self.client.put(
            "/api/v1/admin/company-items/order/", {"item_ids": [999999]}, format="json",
        ).status_code, 400)
        self.assertEqual(self.client.put(
            "/api/v1/admin/company-items/order/", {"item_ids": []}, format="json",
        ).status_code, 400)

    # ---- App 只读 ----

    def test_app_list_is_common_limited_and_ordered(self):
        for index in range(12):
            self.published_item(title=f"事项{index:02d}", sort_order=index + 1)
        CompanyImportantItem.objects.create(title="草稿", summary="摘要", body="正文")

        for user in (self.founder, self.manager, self.employee):
            self.auth(user)
            data = self.client.get("/api/v1/app/company-items/?page_size=20").data["data"]
            self.assertEqual(data["count"], 10)
            self.assertEqual([row["title"] for row in data["results"]], [f"事项{index:02d}" for index in range(10)])
            self.assertNotIn("body", data["results"][0])
            self.assertNotIn("status", data["results"][0])

        first = CompanyImportantItem.objects.filter(status="published").order_by("sort_order").first()
        detail = self.client.get(f"/api/v1/app/company-items/{first.pk}/").data["data"]
        self.assertEqual(detail["body"], "正文")
        self.assertNotIn("status", detail)

    # ---- 附件 ----

    def test_attachment_upload_and_delete(self):
        item_id = self.create_item().data["data"]["id"]
        upload = SimpleUploadedFile("值班表.pdf", b"%PDF-1.4 test", content_type="application/pdf")
        response = self.client.post(
            f"/api/v1/admin/company-items/{item_id}/attachments/", {"file": upload}, format="multipart",
        )
        self.assertEqual(response.status_code, 201, response.data)
        attachment_id = response.data["data"]["id"]
        self.assertEqual(response.data["data"]["original_name"], "值班表.pdf")
        self.assertTrue(response.data["data"]["url"].startswith("/media/company-items/"))

        detail = self.client.get(f"/api/v1/admin/company-items/{item_id}/").data["data"]
        self.assertEqual(len(detail["attachments"]), 1)

        deleted = self.client.delete(f"/api/v1/admin/company-items/{item_id}/attachments/{attachment_id}/")
        self.assertEqual(deleted.status_code, 200, deleted.data)
        self.assertTrue(deleted.data["data"]["deleted"])
        self.assertFalse(CompanyItemAttachment.objects.filter(pk=attachment_id).exists())
        self.assertEqual(self.client.delete(
            f"/api/v1/admin/company-items/{item_id}/attachments/{attachment_id}/",
        ).status_code, 404)

    def test_attachment_rejects_oversized_and_wrong_type(self):
        item_id = self.create_item().data["data"]["id"]
        payload = SimpleUploadedFile("install.exe", b"MZ", content_type="application/x-msdownload")
        self.assertEqual(self.client.post(
            f"/api/v1/admin/company-items/{item_id}/attachments/", {"file": payload}, format="multipart",
        ).status_code, 400)

        with override_settings(COMPANY_ITEM_ATTACHMENT_MAX_BYTES=4):
            big = SimpleUploadedFile("a.pdf", b"12345", content_type="application/pdf")
            self.assertEqual(self.client.post(
                f"/api/v1/admin/company-items/{item_id}/attachments/", {"file": big}, format="multipart",
            ).status_code, 400)

        self.assertEqual(self.client.post(
            f"/api/v1/admin/company-items/{item_id}/attachments/", {}, format="multipart",
        ).status_code, 400)

    def test_attachment_of_other_item_cannot_be_deleted(self):
        item_a = self.create_item(title="A").data["data"]["id"]
        item_b = self.create_item(title="B").data["data"]["id"]
        upload = SimpleUploadedFile("a.pdf", b"%PDF", content_type="application/pdf")
        attachment_id = self.client.post(
            f"/api/v1/admin/company-items/{item_a}/attachments/", {"file": upload}, format="multipart",
        ).data["data"]["id"]
        self.assertEqual(self.client.delete(
            f"/api/v1/admin/company-items/{item_b}/attachments/{attachment_id}/",
        ).status_code, 404)
        self.assertTrue(CompanyItemAttachment.objects.filter(pk=attachment_id).exists())
