from django.contrib.auth import get_user_model
from django.urls import reverse
from rest_framework.test import APITestCase


class HealthCheckTests(APITestCase):
    def test_health_check_is_public_and_returns_ok(self):
        response = self.client.get(reverse("health-check"))

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok", "service": "pandora-backend"})

    def test_openapi_and_swagger_pages_are_public(self):
        schema_response = self.client.get("/api/schema/", HTTP_ACCEPT="application/json")
        docs_response = self.client.get("/api/docs/")

        self.assertEqual(schema_response.status_code, 200)
        self.assertEqual(docs_response.status_code, 200)
        schema = schema_response.json()
        self.assertEqual(schema["info"]["title"], "Project Pandora 后端接口文档")
        self.assertEqual(
            schema["paths"]["/api/health/"]["get"]["summary"],
            "健康检查",
        )

    def test_jwt_token_can_be_obtained_with_username_and_password(self):
        get_user_model().objects.create_user(username="demo", password="local-test-pass-123")

        response = self.client.post(
            "/api/auth/token/",
            {"username": "demo", "password": "local-test-pass-123"},
            format="json",
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("access", response.json())
        self.assertIn("refresh", response.json())
