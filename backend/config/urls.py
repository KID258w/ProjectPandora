from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.urls import include, path
from rest_framework.permissions import AllowAny
from drf_spectacular.views import (  # noqa: E402
    SpectacularAPIView,
    SpectacularRedocView,
    SpectacularSwaggerView,
)

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/health/", include("apps.core.urls")),
    path("api/v1/", include("apps.accounts.urls")),
    path("api/v1/", include("apps.content.urls")),
    path("api/v1/app/", include("apps.tags.urls")),
    path("api/v1/app/", include("apps.logs.urls")),
    path("api/v1/app/", include("apps.tasks.urls")),
    path("api/v1/app/", include("apps.content.app_urls")),
    path("api/v1/app/", include("apps.home.urls")),
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

if settings.DEBUG:
    # 开发期由 Django 直接提供附件文件；正式环境由 Nginx/对象存储提供。
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
