from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from apps.accounts.models import User

from .models import CompanyImportantItem


class MidtermMsyAppCompanyItemsTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            username="app-user", password="test-password", display_name="员工",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        self.authenticate(self.user)

    def authenticate(self, user):
        token = AccessToken.for_user(user)
        token["account_type"] = user.account_type
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def item(self, title, status, order=0):
        return CompanyImportantItem.objects.create(
            title=title, summary=f"{title}摘要", body=f"{title}正文", status=status, sort_order=order,
        )

    def test_app_item_list_returns_empty_when_no_item_is_published(self):
        self.item("草稿", CompanyImportantItem.Status.DRAFT)
        self.item("已下线", CompanyImportantItem.Status.OFFLINE)
        response = self.client.get("/api/v1/app/company-items/")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["data"]["count"], 0)

    def test_app_item_list_filters_orders_and_caps_published_items_at_ten(self):
        for index in range(12):
            self.item(f"事项{index:02d}", CompanyImportantItem.Status.PUBLISHED, index + 1)
        self.item("草稿", CompanyImportantItem.Status.DRAFT)
        response = self.client.get("/api/v1/app/company-items/?page_size=20")
        self.assertEqual(response.status_code, 200, response.data)
        data = response.data["data"]
        self.assertEqual(data["count"], 10)
        self.assertEqual([row["title"] for row in data["results"]], [f"事项{index:02d}" for index in range(10)])
        self.assertNotIn("body", data["results"][0])
        self.assertNotIn("status", data["results"][0])

    def test_app_item_list_requires_business_user_scope(self):
        self.client.credentials()
        self.assertEqual(self.client.get("/api/v1/app/company-items/").status_code, 401)
        admin = User.objects.create_superuser(username="admin", password="test-password")
        self.authenticate(admin)
        self.assertEqual(self.client.get("/api/v1/app/company-items/").status_code, 403)

    def test_app_item_detail_returns_published_body_without_admin_fields(self):
        item = self.item("公开事项", CompanyImportantItem.Status.PUBLISHED, 1)
        response = self.client.get(f"/api/v1/app/company-items/{item.pk}/")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["data"]["body"], "公开事项正文")
        self.assertNotIn("status", response.data["data"])
        self.assertNotIn("sort_order", response.data["data"])

    def test_app_item_detail_hides_draft_and_offline_items(self):
        draft = self.item("草稿", CompanyImportantItem.Status.DRAFT)
        offline = self.item("下线", CompanyImportantItem.Status.OFFLINE)
        for item in (draft, offline):
            with self.subTest(status=item.status):
                response = self.client.get(f"/api/v1/app/company-items/{item.pk}/")
                self.assertEqual(response.status_code, 404)

    def test_app_item_detail_returns_not_found_for_unknown_id(self):
        self.assertEqual(self.client.get("/api/v1/app/company-items/999999/").status_code, 404)
