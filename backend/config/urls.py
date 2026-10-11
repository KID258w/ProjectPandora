from django.contrib import admin
from django.urls import include, path
from rest_framework.permissions import AllowAny
from drf_spectacular.views import (  # noqa: E402
    SpectacularAPIView,
    SpectacularRedocView,
    SpectacularSwaggerView,
)
from apps.accounts.views import PandoraTokenObtainPairView, PandoraTokenRefreshView

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/health/", include("apps.core.urls")),
    path("api/auth/token/", PandoraTokenObtainPairView.as_view(), name="token_obtain_pair"),
    path("api/auth/token/refresh/", PandoraTokenRefreshView.as_view(), name="token_refresh"),
    path(
        "api/schema/",
        SpectacularAPIView.as_view(permission_classes=[AllowAny]),
        name="schema",
    ),
    path(
        "api/docs/",
        SpectacularSwaggerView.as_view(url_name="schema", permission_classes=[AllowAny]),
        name="swagger-ui",
    ),
    path(
        "api/redoc/",
        SpectacularRedocView.as_view(url_name="schema", permission_classes=[AllowAny]),
        name="redoc",
    ),
]
