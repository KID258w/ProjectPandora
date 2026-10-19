from rest_framework.exceptions import AuthenticationFailed
from rest_framework.permissions import BasePermission
from rest_framework_simplejwt.authentication import JWTAuthentication
from drf_spectacular.contrib.rest_framework_simplejwt import SimpleJWTScheme


class ScopedJWTAuthentication(JWTAuthentication):
    def authenticate(self, request):
        result = super().authenticate(request)
        if result is None:
            return None
        user, token = result
        if token.get("account_type") != user.account_type:
            raise AuthenticationFailed("令牌账号类型已失效。")
        return user, token


class ScopedJWTScheme(SimpleJWTScheme):
    target_class = ScopedJWTAuthentication
    name = "scopedJWTAuth"


class IsAdministrator(BasePermission):
    message = "仅 Web 管理员可访问。"

    def has_permission(self, request, view):
        return bool(request.user.is_authenticated and request.user.is_active and request.user.account_type == "admin")


class IsBusinessUser(BasePermission):
    message = "仅 App 业务用户可访问。"

    def has_permission(self, request, view):
        return bool(request.user.is_authenticated and request.user.is_active and request.user.account_type == "business")
