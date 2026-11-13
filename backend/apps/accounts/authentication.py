from rest_framework.exceptions import AuthenticationFailed
from rest_framework.permissions import BasePermission
from rest_framework_simplejwt.authentication import JWTAuthentication
from drf_spectacular.contrib.rest_framework_simplejwt import SimpleJWTScheme

from .models import User


def has_complete_supervisor_chain(user):
    """Return whether an active business user has a valid chain to an active founder."""
    if not user.is_active or user.account_type != User.AccountType.BUSINESS:
        return False

    seen = {user.pk}
    current = user
    while True:
        if not current.is_active or current.account_type != User.AccountType.BUSINESS:
            return False

        role = current.business_role
        if role == User.Role.FOUNDER:
            return current.department_id is None and current.team_id is None and current.manager_id is None

        if current.department_id is None or not current.department.is_active:
            return False

        if role == User.Role.DEPARTMENT_MANAGER:
            expected_role = User.Role.FOUNDER
        elif role == User.Role.TEAM_LEADER:
            if current.team_id is None or current.team.department_id != current.department_id or not current.team.is_active:
                return False
            expected_role = User.Role.DEPARTMENT_MANAGER
        elif role == User.Role.EMPLOYEE:
            if current.team_id is None or current.team.department_id != current.department_id or not current.team.is_active:
                return False
            expected_role = User.Role.TEAM_LEADER
        else:
            return False

        manager = current.manager
        if manager is None or manager.pk in seen or manager.business_role != expected_role:
            return False
        if manager.business_role == User.Role.DEPARTMENT_MANAGER and manager.department_id != current.department_id:
            return False
        if manager.business_role == User.Role.TEAM_LEADER and (
            manager.department_id != current.department_id or manager.team_id != current.team_id
        ):
            return False

        seen.add(manager.pk)
        current = manager

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
        user = request.user
        if not (user.is_authenticated and user.is_active and user.account_type == User.AccountType.BUSINESS):
            return False
        if not has_complete_supervisor_chain(user):
            self.message = "组织负责人暂缺，App 服务暂不可用。"
            return False
        return True
