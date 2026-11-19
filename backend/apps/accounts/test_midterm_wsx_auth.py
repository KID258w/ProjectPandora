"""王世鑫（WSX24）中期测试：账号认证（登录与令牌刷新）。

Issue #42 分工：
- 第 1 项：管理员登录和令牌刷新
- 第 2 项：管理端访问权限

本文件覆盖：
- ScopedTokenSerializer.validate()（经 AdminTokenSerializer / AppTokenSerializer）
- ScopedRefreshSerializer.validate()（经 AdminRefreshSerializer / AppRefreshSerializer）
- ScopedTokenSerializer.get_token()
- IsAdministrator.has_permission()

App 端登录的正常场景在 Issue 第 13 项补充（编号 231～250），本文件只覆盖 Web 管理端
正常流程与两端隔离的边界。

需求依据：docs/backend/account-provisioning.md「认证与当前用户」
> 两端均拒绝另一种账号登录、访问和刷新；JWT 含 account_type。刷新时重新校验账号存在、
> 启用且类型相符。Simple JWT 默认 access 有效期 5 分钟、refresh 1 天，不轮换。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度           | 有效等价类                       | 无效等价类                                 |
| 账号类型×入口   | admin→admin 入口；business→app 入口 | admin→app 入口；business→admin 入口（应拒绝） |
| 凭证           | 用户名+正确密码                   | 密码错误；用户名不存在；大小写不符           |
| 账号状态       | is_active=True                  | is_active=False（应拒绝）                  |
| 令牌类型       | refresh 令牌用于刷新              | access 令牌用于刷新；非法字符串             |
| 令牌归属声明    | account_type 与入口一致           | account_type 缺失或与入口不一致             |
| 令牌时效       | 未过期                          | 已过期                                    |
| 必填字段       | 字段齐全且非空白                  | 缺字段（required）；空串（blank）           |

边界值分析
| 边界                  | 边界前            | 边界值                  | 边界后                    |
| 账号启用状态           | is_active=True    | —                      | is_active=False（登录/刷新均拒绝）|
| 必填字段              | 有值              | 缺字段（required）       | 空串（blank）              |
| refresh 重复使用       | 第 1 次成功        | 第 2 次成功（不轮换）     | 过期后拒绝                 |
| 令牌类型切换           | refresh 令牌      | —                      | access 令牌（令牌类型错误）  |
"""

from django.contrib.auth.models import AnonymousUser
from django.test import TestCase
from rest_framework.exceptions import AuthenticationFailed
from rest_framework.test import APIRequestFactory
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import AccessToken, RefreshToken

from .api_serializers import (
    AdminRefreshSerializer,
    AdminTokenSerializer,
    AppRefreshSerializer,
    AppTokenSerializer,
)
from .authentication import IsAdministrator
from .models import Department, Team, User


class TokenSerializerTests(TestCase):
    """登录与刷新序列化器的单元测试（不经过 HTTP 视图）。

    说明：AuthenticationFailed 继承自 APIException 而非 ValidationError，
    因此 is_valid() 不会捕获它，而是向上抛出；令牌类错误抛 TokenError。
    """

    password = "Pandora-test-938!"

    def setUp(self):
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        self.business = User.objects.create_user(
            username="founder", password=self.password,
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )

    def admin_refresh_token(self):
        return AdminTokenSerializer.get_token(self.admin)

    def business_refresh_token(self):
        token = RefreshToken.for_user(self.business)
        token["account_type"] = User.AccountType.BUSINESS
        return token

    # ---------- 正常输入 ----------

    def test_admin_login_returns_access_and_refresh(self):
        """TC-WSX-001 正常：管理员正确凭证登录，返回 access 与 refresh。"""
        serializer = AdminTokenSerializer(data={"username": "admin", "password": self.password})
        self.assertTrue(serializer.is_valid())
        self.assertEqual(sorted(serializer.validated_data.keys()), ["access", "refresh"])

    def test_get_token_embeds_account_type(self):
        """TC-WSX-002 正常：get_token 在 JWT 中写入 account_type，用于两端隔离。"""
        self.assertEqual(self.admin_refresh_token().get("account_type"), User.AccountType.ADMIN)

    def test_admin_refresh_returns_new_access(self):
        """TC-WSX-003 正常：管理员用 refresh 令牌换取新 access。"""
        serializer = AdminRefreshSerializer(data={"refresh": str(self.admin_refresh_token())})
        self.assertTrue(serializer.is_valid())
        self.assertEqual(list(serializer.validated_data.keys()), ["access"])

    def test_refreshed_access_keeps_account_type_and_user(self):
        """TC-WSX-004 正常：刷新得到的 access 仍指向同一账号且类型不变。"""
        serializer = AdminRefreshSerializer(data={"refresh": str(self.admin_refresh_token())})
        serializer.is_valid()
        access = AccessToken(str(serializer.validated_data["access"]))
        self.assertEqual(access.get("account_type"), User.AccountType.ADMIN)
        # JWT 规范下 user_id 以字符串形式存储，需转换后比较
        self.assertEqual(int(access.get("user_id")), self.admin.pk)

    # ---------- 边界值 ----------

    def test_boundary_refresh_token_not_rotated(self):
        """TC-WSX-005 边界：同一 refresh 令牌可重复使用（默认不轮换）。"""
        refresh = str(self.admin_refresh_token())
        first = AdminRefreshSerializer(data={"refresh": refresh})
        second = AdminRefreshSerializer(data={"refresh": refresh})
        self.assertTrue(first.is_valid())
        self.assertTrue(second.is_valid())

    def test_boundary_missing_all_fields_reports_required(self):
        """TC-WSX-006 边界：登录缺全部字段，逐字段报告 required。"""
        serializer = AdminTokenSerializer(data={})
        self.assertFalse(serializer.is_valid())
        self.assertEqual(serializer.errors["username"][0].code, "required")
        self.assertEqual(serializer.errors["password"][0].code, "required")

    def test_boundary_blank_username_rejected(self):
        """TC-WSX-007 边界：用户名为空串，字段级校验拦截（blank）。"""
        serializer = AdminTokenSerializer(data={"username": "", "password": self.password})
        self.assertFalse(serializer.is_valid())
        self.assertEqual(serializer.errors["username"][0].code, "blank")

    def test_boundary_blank_password_rejected(self):
        """TC-WSX-008 边界：密码为空串，字段级校验拦截（blank）。"""
        serializer = AdminTokenSerializer(data={"username": "admin", "password": ""})
        self.assertFalse(serializer.is_valid())
        self.assertEqual(serializer.errors["password"][0].code, "blank")

    def test_boundary_missing_refresh_field(self):
        """TC-WSX-009 边界：刷新接口缺 refresh 字段，报告 required。"""
        serializer = AdminRefreshSerializer(data={})
        self.assertFalse(serializer.is_valid())
        self.assertEqual(serializer.errors["refresh"][0].code, "required")

    def test_boundary_blank_refresh_field(self):
        """TC-WSX-010 边界：refresh 为空串，字段级校验拦截（blank）。"""
        serializer = AdminRefreshSerializer(data={"refresh": ""})
        self.assertFalse(serializer.is_valid())
        self.assertEqual(serializer.errors["refresh"][0].code, "blank")

    def test_boundary_inactive_admin_cannot_login(self):
        """TC-WSX-011 边界（状态切换点）：管理员停用后立即无法登录。"""
        User.objects.filter(pk=self.admin.pk).update(is_active=False)
        with self.assertRaises(AuthenticationFailed):
            AdminTokenSerializer(data={"username": "admin", "password": self.password}).is_valid()

    def test_boundary_inactive_admin_cannot_refresh(self):
        """TC-WSX-012 边界（状态切换点）：管理员停用后已签发的 refresh 失效。"""
        refresh = str(self.admin_refresh_token())
        User.objects.filter(pk=self.admin.pk).update(is_active=False)
        with self.assertRaises(AuthenticationFailed) as ctx:
            AdminRefreshSerializer(data={"refresh": refresh}).is_valid()
        self.assertIn("账号已停用或令牌已失效", str(ctx.exception.detail))

    # ---------- 异常输入：两端隔离 ----------

    def test_business_account_cannot_login_admin_entry(self):
        """TC-WSX-013 异常：业务账号走管理端入口，拒绝登录。"""
        with self.assertRaises(AuthenticationFailed) as ctx:
            AdminTokenSerializer(data={"username": "founder", "password": self.password}).is_valid()
        self.assertIn("账号不可用于此入口", str(ctx.exception.detail))

    def test_admin_account_cannot_login_app_entry(self):
        """TC-WSX-014 异常：管理员账号走 App 入口，拒绝登录。"""
        with self.assertRaises(AuthenticationFailed) as ctx:
            AppTokenSerializer(data={"username": "admin", "password": self.password}).is_valid()
        self.assertIn("账号不可用于此入口", str(ctx.exception.detail))

    def test_admin_refresh_rejected_by_app_entry(self):
        """TC-WSX-015 异常：管理端 refresh 令牌在 App 刷新入口被拒绝。"""
        with self.assertRaises(AuthenticationFailed) as ctx:
            AppRefreshSerializer(data={"refresh": str(self.admin_refresh_token())}).is_valid()
        self.assertIn("刷新令牌不属于此入口", str(ctx.exception.detail))

    def test_business_refresh_rejected_by_admin_entry(self):
        """TC-WSX-016 异常：App refresh 令牌在管理端刷新入口被拒绝。"""
        with self.assertRaises(AuthenticationFailed) as ctx:
            AdminRefreshSerializer(data={"refresh": str(self.business_refresh_token())}).is_valid()
        self.assertIn("刷新令牌不属于此入口", str(ctx.exception.detail))

    # ---------- 异常输入：凭证 ----------

    def test_exception_wrong_password(self):
        """TC-WSX-017 异常：密码错误，拒绝登录。"""
        with self.assertRaises(AuthenticationFailed):
            AdminTokenSerializer(data={"username": "admin", "password": "wrong-password"}).is_valid()

    def test_exception_nonexistent_username(self):
        """TC-WSX-018 异常：用户名不存在，拒绝登录。"""
        with self.assertRaises(AuthenticationFailed):
            AdminTokenSerializer(data={"username": "nobody", "password": self.password}).is_valid()

    def test_exception_username_case_mismatch(self):
        """TC-WSX-019 异常：用户名大小写不符（账号名区分大小写），拒绝登录。"""
        with self.assertRaises(AuthenticationFailed):
            AdminTokenSerializer(data={"username": "ADMIN", "password": self.password}).is_valid()

    # ---------- 异常输入：令牌 ----------

    def test_exception_access_token_used_as_refresh(self):
        """TC-WSX-020 异常：把 access 令牌当 refresh 提交，报告令牌类型错误。"""
        access = str(AccessToken.for_user(self.admin))
        with self.assertRaises(TokenError):
            AdminRefreshSerializer(data={"refresh": access}).is_valid()

    def test_exception_malformed_refresh_string(self):
        """TC-WSX-021 异常：非法字符串作为 refresh，报告令牌无效。"""
        with self.assertRaises(TokenError):
            AdminRefreshSerializer(data={"refresh": "not-a-real-token"}).is_valid()

    def test_exception_expired_refresh_token(self):
        """TC-WSX-022 异常：refresh 令牌已过期，报告令牌过期。"""
        token = self.admin_refresh_token()
        token["exp"] = 1
        with self.assertRaises(TokenError):
            AdminRefreshSerializer(data={"refresh": str(token)}).is_valid()

    def test_exception_refresh_without_account_type_claim(self):
        """TC-WSX-023 异常：refresh 令牌缺少 account_type 声明，视为不属于该入口。"""
        token = RefreshToken.for_user(self.admin)
        with self.assertRaises(AuthenticationFailed) as ctx:
            AdminRefreshSerializer(data={"refresh": str(token)}).is_valid()
        self.assertIn("刷新令牌不属于此入口", str(ctx.exception.detail))

    def test_exception_refresh_for_removed_user(self):
        """TC-WSX-024 异常：refresh 指向已不存在的账号，拒绝刷新。"""
        token = RefreshToken()
        token["user_id"] = 999999
        token["account_type"] = User.AccountType.ADMIN
        with self.assertRaises(AuthenticationFailed) as ctx:
            AdminRefreshSerializer(data={"refresh": str(token)}).is_valid()
        self.assertIn("账号已停用或令牌已失效", str(ctx.exception.detail))


class IsAdministratorPermissionTests(TestCase):
    """IsAdministrator.has_permission() 单元测试（第 2 项）。

    需求依据：docs/backend/account-provisioning.md
    > API 管理权限由 account_type 决定，不依赖 is_staff。

    等价类划分
    | 维度           | 有效等价类            | 无效等价类                          |
    | 认证状态        | 已认证                | 未认证（AnonymousUser）             |
    | 账号启用状态    | is_active=True        | is_active=False                     |
    | 账号类型        | account_type=admin    | account_type=business（含各业务身份）|
    | Django 运维标记 | 与判定无关            | is_staff/is_superuser 不应影响结果    |

    边界值分析
    | 边界                | 边界前                  | 边界值        | 边界后                    |
    | 账号启用状态         | is_active=True → True   | —            | is_active=False → False    |
    | 账号类型切换         | admin → True            | —            | business → False           |
    | is_staff 切换       | is_staff=False 仍 True  | —            | is_staff=True 仍 False（业务账号）|
    """

    password = "Pandora-test-938!"

    def setUp(self):
        self.permission = IsAdministrator()
        self.factory = APIRequestFactory()
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        self.founder = User.objects.create_user(
            username="founder", password=self.password,
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )

    def allowed(self, user):
        request = self.factory.get("/api/v1/admin/users/")
        request.user = user
        return self.permission.has_permission(request, None)

    # ---------- 正常输入 ----------

    def test_active_admin_allowed(self):
        """TC-WSX-031 正常：启用中的管理员可访问管理端。"""
        self.assertTrue(self.allowed(self.admin))

    def test_permission_ignores_is_staff_flag(self):
        """TC-WSX-032 正常：权限只认 account_type，管理员去掉 is_staff 仍可访问（需求明文）。"""
        User.objects.filter(pk=self.admin.pk).update(is_staff=False)
        self.admin.refresh_from_db()
        self.assertFalse(self.admin.is_staff)
        self.assertTrue(self.allowed(self.admin))

    def test_permission_message_text(self):
        """TC-WSX-039 正常：拒绝时的提示文案与需求一致。"""
        self.assertEqual(self.permission.message, "仅 Web 管理员可访问。")

    # ---------- 边界值 ----------

    def test_boundary_inactive_admin_denied(self):
        """TC-WSX-033 边界（状态切换点）：管理员停用后立即失去管理端权限。"""
        User.objects.filter(pk=self.admin.pk).update(is_active=False)
        self.admin.refresh_from_db()
        self.assertFalse(self.allowed(self.admin))

    def test_boundary_reactivated_admin_allowed_again(self):
        """TC-WSX-034 边界（状态切换点）：停用后重新启用，权限恢复。"""
        User.objects.filter(pk=self.admin.pk).update(is_active=False)
        User.objects.filter(pk=self.admin.pk).update(is_active=True)
        self.admin.refresh_from_db()
        self.assertTrue(self.allowed(self.admin))

    def test_boundary_business_founder_denied(self):
        """TC-WSX-035 边界（权限范围边缘）：业务账号即使为最高职级创始人也被拒绝。"""
        self.assertFalse(self.allowed(self.founder))

    def test_boundary_business_superuser_flag_does_not_grant_access(self):
        """TC-WSX-036 边界：业务账号即使带 is_staff/is_superuser 标记仍被拒绝（需求明文）。"""
        User.objects.filter(pk=self.founder.pk).update(is_staff=True, is_superuser=True)
        self.founder.refresh_from_db()
        self.assertFalse(self.allowed(self.founder))

    def test_boundary_anonymous_user_denied(self):
        """TC-WSX-037 边界：未认证用户被拒绝。"""
        self.assertFalse(self.allowed(AnonymousUser()))

    # ---------- 特殊输入 ----------

    def test_anonymous_user_does_not_raise_attribute_error(self):
        """TC-WSX-038 异常：匿名用户缺少 account_type 属性时短路返回 False，不抛异常。"""
        self.assertFalse(hasattr(AnonymousUser(), "account_type"))
        self.assertFalse(self.allowed(AnonymousUser()))

    def test_business_employee_denied(self):
        """TC-WSX-040 边界：业务员工（最低职级）被拒绝，与职级高低无关。"""
        department = Department.objects.create(name="研发部")
        team = Team.objects.create(name="后端组", department=department)
        employee = User.objects.create_user(
            username="emp", password=self.password,
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=department, team=team,
        )
        self.assertFalse(self.allowed(employee))
