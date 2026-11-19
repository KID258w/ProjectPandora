"""王世鑫（WSX24）中期测试：App 登录、组织链校验、个人资料与密码。

Issue #42 分工：
- 第 13 项：App 登录和令牌刷新（AppTokenSerializer / AppRefreshSerializer，TC-WSX-231～238）
- 第 14 项：App 访问及组织链校验（has_complete_supervisor_chain / IsBusinessUser，TC-WSX-251～266）
- 第 15 项：个人资料字段校验（AppMeUpdateSerializer.validate_*，TC-WSX-281～290）
- 第 16 项：个人资料更新（AppMeView.update()，TC-WSX-301～306）
- 第 17 项：修改密码（AppPasswordChangeSerializer，TC-WSX-311～317）
- 第 18 项：修改密码接口（AppPasswordChangeView.post()，TC-WSX-321～325）

需求依据：docs/backend/account-provisioning.md
> App 业务账号必须具有从本人到启用创始人的完整直属上级链；链路中断时 App 登录和
> 令牌刷新返回 401，已登录的 App 业务接口返回 403。
> PATCH app/me/ 允许提交 display_name、username、id_number，任一字段校验失败整次回滚。
> POST app/me/password/ 校验当前密码、两次一致、新旧不同，并运行 Django 密码校验器。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度           | 有效等价类                  | 无效等价类                                 |
| 组织链          | 员工→团队长→经理→创始人齐全启用 | 任一环节停用/空缺/角色错配/跨部门         |
| 账号类型        | business 访问 App           | admin 访问 App；匿名访问                   |
| 手机号          | 非空且未被占用               | 空串；已被他人使用                          |
| 姓名           | 非空白、≤100 字符            | 纯空白；超过 100 字符                       |
| 身份证号        | 规范化后唯一                 | 空串；已被他人使用                          |
| 密码修改        | 当前密码正确、两次一致、新旧不同、满足校验器 | 当前密码错；两次不一致；新旧相同；弱密码 |
| 可修改字段      | display_name/username/id_number | 业务身份、组织、manager_id、is_active、password |

边界值分析
| 边界              | 边界前             | 边界值           | 边界后                    |
| 组织链完整性       | 全部启用 → 可访问    | —               | 任一环节停用 → 登录 401/访问 403 |
| 姓名长度          | 99 字符            | 100 字符（通过）  | 101 字符（拒绝）            |
| 手机号唯一性       | 被他人占用 → 拒绝   | —               | 未占用 → 通过              |
| 新旧密码关系       | 不同 → 通过        | —               | 相同 → 拒绝                |
"""

from types import SimpleNamespace

from django.contrib.auth.models import AnonymousUser
from django.test import TestCase
from rest_framework.exceptions import PermissionDenied
from rest_framework.test import APIClient, APIRequestFactory

from .api_serializers import (
    AppMeUpdateSerializer,
    AppPasswordChangeSerializer,
    AppRefreshSerializer,
    AppTokenSerializer,
)
from .authentication import IsBusinessUser, has_complete_supervisor_chain
from .models import AppOperationAuditLog, Department, Team, User

PASSWORD = "Pandora-test-938!"


class OrgMixin:
    """构建完整的 创始人→经理→团队长→员工 组织链。"""

    def build_org(self, team_matches_department=True):
        self.department = Department.objects.create(name="研发部")
        other = Department.objects.create(name="财务部")
        self.team = Team.objects.create(
            name="后端组", department=self.department if team_matches_department else other,
        )
        self.founder = User.objects.create_user(
            username="founder", password=PASSWORD, display_name="创始人",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        self.manager = User.objects.create_user(
            username="manager", password=PASSWORD, display_name="经理",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.DEPARTMENT_MANAGER,
            department=self.department, manager=self.founder,
        )
        self.leader = User.objects.create_user(
            username="leader", password=PASSWORD, display_name="团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team, manager=self.manager,
        )
        self.employee = User.objects.create_user(
            username="13800000001", password=PASSWORD, display_name="张三",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=self.department, team=self.team, manager=self.leader,
        )


class AppLoginTests(OrgMixin, TestCase):
    """App 登录与刷新（第 13 项）。"""

    def setUp(self):
        self.build_org()

    # ---------- 正常输入 ----------

    def test_app_login_succeeds_with_complete_chain(self):
        """TC-WSX-231 正常：组织链完整的业务账号可登录 App。"""
        serializer = AppTokenSerializer(data={"username": "13800000001", "password": PASSWORD})
        self.assertTrue(serializer.is_valid(), serializer.errors)
        self.assertEqual(sorted(serializer.validated_data.keys()), ["access", "refresh"])

    def test_app_token_carries_business_account_type(self):
        """TC-WSX-232 正常：App 令牌声明 account_type=business。"""
        token = AppTokenSerializer.get_token(self.employee)
        self.assertEqual(token.get("account_type"), User.AccountType.BUSINESS)

    def test_app_refresh_returns_new_access(self):
        """TC-WSX-233 正常：App 用 refresh 换取新 access。"""
        refresh = str(AppTokenSerializer.get_token(self.employee))
        serializer = AppRefreshSerializer(data={"refresh": refresh})
        self.assertTrue(serializer.is_valid(), serializer.errors)
        self.assertEqual(list(serializer.validated_data.keys()), ["access"])

    # ---------- 边界：组织链中断 ----------

    def test_boundary_inactive_founder_blocks_login(self):
        """TC-WSX-234 边界（链断点）：创始人停用后员工无法登录 App。"""
        User.objects.filter(pk=self.founder.pk).update(is_active=False)
        serializer = AppTokenSerializer(data={"username": "13800000001", "password": PASSWORD})
        with self.assertRaises(Exception) as ctx:
            serializer.is_valid()
        self.assertIn("组织负责人暂缺", str(ctx.exception.detail))

    def test_boundary_inactive_founder_blocks_refresh(self):
        """TC-WSX-235 边界（链断点）：创始人停用后已签发 refresh 失效。"""
        refresh = str(AppTokenSerializer.get_token(self.employee))
        User.objects.filter(pk=self.founder.pk).update(is_active=False)
        serializer = AppRefreshSerializer(data={"refresh": refresh})
        with self.assertRaises(Exception) as ctx:
            serializer.is_valid()
        self.assertIn("组织负责人暂缺", str(ctx.exception.detail))

    def test_boundary_inactive_manager_blocks_login(self):
        """TC-WSX-236 边界（链断点）：部门经理停用后员工无法登录。"""
        User.objects.filter(pk=self.manager.pk).update(is_active=False)
        serializer = AppTokenSerializer(data={"username": "13800000001", "password": PASSWORD})
        with self.assertRaises(Exception):
            serializer.is_valid()

    def test_boundary_inactive_leader_blocks_login(self):
        """TC-WSX-237 边界（链断点）：团队长停用后员工无法登录。"""
        User.objects.filter(pk=self.leader.pk).update(is_active=False)
        serializer = AppTokenSerializer(data={"username": "13800000001", "password": PASSWORD})
        with self.assertRaises(Exception):
            serializer.is_valid()

    def test_boundary_inactive_employee_cannot_login(self):
        """TC-WSX-238 边界：员工自身停用后无法登录。"""
        User.objects.filter(pk=self.employee.pk).update(is_active=False)
        serializer = AppTokenSerializer(data={"username": "13800000001", "password": PASSWORD})
        with self.assertRaises(Exception):
            serializer.is_valid()


class SupervisorChainTests(OrgMixin, TestCase):
    """has_complete_supervisor_chain() 单元测试（第 14 项）。"""

    def setUp(self):
        self.build_org()

    # ---------- 正常输入 ----------

    def test_founder_without_organization_has_chain(self):
        """TC-WSX-251 正常：无组织的启用创始人视为链完整。"""
        self.assertTrue(has_complete_supervisor_chain(self.founder))

    def test_employee_with_full_chain(self):
        """TC-WSX-252 正常：员工到创始人的完整链返回 True。"""
        self.assertTrue(has_complete_supervisor_chain(self.employee))

    # ---------- 边界值 ----------

    def test_boundary_inactive_founder_breaks_chain(self):
        """TC-WSX-253 边界（链断点）：创始人停用后整条链失效。"""
        User.objects.filter(pk=self.founder.pk).update(is_active=False)
        self.employee.refresh_from_db()
        self.assertFalse(has_complete_supervisor_chain(self.employee))

    def test_boundary_inactive_department_breaks_chain(self):
        """TC-WSX-254 边界：部门停用后成员链失效。"""
        Department.objects.filter(pk=self.department.pk).update(is_active=False)
        self.employee.refresh_from_db()
        self.assertFalse(has_complete_supervisor_chain(self.employee))

    def test_boundary_inactive_team_breaks_chain(self):
        """TC-WSX-255 边界：团队停用后成员链失效。"""
        Team.objects.filter(pk=self.team.pk).update(is_active=False)
        self.employee.refresh_from_db()
        self.assertFalse(has_complete_supervisor_chain(self.employee))

    def test_boundary_team_not_in_department_breaks_chain(self):
        """TC-WSX-256 边界：团队不属于该部门时链失效。"""
        other = Department.objects.create(name="市场部")
        User.objects.filter(pk=self.employee.pk).update(department=other)
        self.employee.refresh_from_db()
        self.assertFalse(has_complete_supervisor_chain(self.employee))

    def test_boundary_role_mismatch_breaks_chain(self):
        """TC-WSX-257 边界：直属上级业务身份不匹配时链失效。"""
        User.objects.filter(pk=self.employee.pk).update(manager=self.manager)
        self.employee.refresh_from_db()
        self.assertFalse(has_complete_supervisor_chain(self.employee))

    def test_boundary_missing_manager_breaks_chain(self):
        """TC-WSX-258 边界：直属上级空缺时链失效。"""
        User.objects.filter(pk=self.employee.pk).update(manager=None)
        self.employee.refresh_from_db()
        self.assertFalse(has_complete_supervisor_chain(self.employee))

    def test_founder_with_department_breaks_chain(self):
        """TC-WSX-259 异常：创始人被挂上组织时链失效（组织形状非法）。

        说明：数据库约束 valid_account_placement 已阻止创始人带部门/团队/上级落库，
        该分支属于防御性校验，因此使用内存实例验证，不使用持久化数据。
        """
        illegal_founder = User(
            username="illegal-founder", account_type=User.AccountType.BUSINESS,
            business_role=User.Role.FOUNDER, department_id=self.department.pk,
        )
        self.assertFalse(has_complete_supervisor_chain(illegal_founder))

    def test_admin_account_has_no_chain(self):
        """TC-WSX-260 边界：管理员账号不属于业务链，返回 False。"""
        admin = User.objects.create_superuser(username="admin", password=PASSWORD)
        self.assertFalse(has_complete_supervisor_chain(admin))

    def test_inactive_user_has_no_chain(self):
        """TC-WSX-261 边界：停用账号返回 False。"""
        User.objects.filter(pk=self.employee.pk).update(is_active=False)
        self.employee.refresh_from_db()
        self.assertFalse(has_complete_supervisor_chain(self.employee))


class IsBusinessUserTests(OrgMixin, TestCase):
    """IsBusinessUser.has_permission() 单元测试（第 14 项）。"""

    def setUp(self):
        self.build_org()
        self.permission = IsBusinessUser()
        self.factory = APIRequestFactory()
        self.admin = User.objects.create_superuser(username="admin", password=PASSWORD)

    def allowed(self, user):
        request = self.factory.get("/api/v1/app/me/")
        request.user = user
        return self.permission.has_permission(request, None)

    def test_business_user_with_full_chain_allowed(self):
        """TC-WSX-262 正常：组织链完整的业务用户可访问 App。"""
        self.assertTrue(self.allowed(self.employee))

    def test_anonymous_denied(self):
        """TC-WSX-263 边界：匿名用户被拒绝。"""
        self.assertFalse(self.allowed(AnonymousUser()))

    def test_admin_denied_on_app_entry(self):
        """TC-WSX-264 边界：管理员不能访问 App 业务接口。"""
        self.assertFalse(self.allowed(self.admin))

    def test_broken_chain_denied_with_vacancy_message(self):
        """TC-WSX-265 边界（链断点）：链中断时拒绝，并给出负责人暂缺提示。"""
        User.objects.filter(pk=self.founder.pk).update(is_active=False)
        self.employee.refresh_from_db()
        self.assertFalse(self.allowed(self.employee))
        self.assertEqual(self.permission.message, "组织负责人暂缺，App 服务暂不可用。")

    def test_inactive_business_user_denied(self):
        """TC-WSX-266 边界：停用业务用户被拒绝。"""
        User.objects.filter(pk=self.employee.pk).update(is_active=False)
        self.employee.refresh_from_db()
        self.assertFalse(self.allowed(self.employee))


class AppMeFieldValidationTests(OrgMixin, TestCase):
    """AppMeUpdateSerializer.validate_* 单元测试（第 15 项）。"""

    def setUp(self):
        self.build_org()
        self.other = User.objects.create_user(
            username="13800000002", password=PASSWORD, display_name="李四",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=self.department, team=self.team, manager=self.leader,
        )

    def validate(self, **data):
        serializer = AppMeUpdateSerializer(instance=self.employee, data=data, partial=True)
        return serializer

    def assert_blocked(self, **data):
        serializer = self.validate(**data)
        self.assertFalse(serializer.is_valid())
        return str(serializer.errors)

    # ---------- 正常输入 ----------

    def test_username_update_allowed(self):
        """TC-WSX-281 正常：改为未被占用的手机号通过。"""
        serializer = self.validate(username="13900000000")
        self.assertTrue(serializer.is_valid(), serializer.errors)

    def test_display_name_trimmed(self):
        """TC-WSX-284 正常：姓名保存前去除首尾空白。"""
        serializer = self.validate(display_name="  王五  ")
        self.assertTrue(serializer.is_valid(), serializer.errors)
        self.assertEqual(serializer.validated_data["display_name"], "王五")

    def test_id_number_normalized(self):
        """TC-WSX-286 正常：身份证号去空格并转大写。"""
        serializer = self.validate(id_number=" 11010119900101123x ")
        self.assertTrue(serializer.is_valid(), serializer.errors)
        self.assertEqual(serializer.validated_data["id_number"], "11010119900101123X")

    # ---------- 边界值 ----------

    def test_boundary_empty_username_rejected(self):
        """TC-WSX-282 边界：手机号为空被拒绝。"""
        error = self.assert_blocked(username="")
        self.assertIn("username", error)

    def test_boundary_username_taken_by_other_rejected(self):
        """TC-WSX-283 边界：手机号已被他人使用被拒绝。"""
        error = self.assert_blocked(username="13800000002")
        self.assertIn("该手机号已被使用", error)

    def test_boundary_whitespace_display_name_rejected(self):
        """TC-WSX-285 边界：姓名纯空白被拒绝。"""
        error = self.assert_blocked(display_name="   ")
        # 字段级 CharField 会先 trim 再判空，因此报 blank 而非自定义文案
        self.assertIn("display_name", error)

    def test_boundary_display_name_length_100_accepted(self):
        """TC-WSX-288 边界：姓名 100 字符（上界）通过。"""
        serializer = self.validate(display_name="名" * 100)
        self.assertTrue(serializer.is_valid(), serializer.errors)

    def test_boundary_display_name_length_101_rejected(self):
        """TC-WSX-289 边界：姓名 101 字符（上界外）被拒绝。"""
        error = self.assert_blocked(display_name="名" * 101)
        self.assertIn("display_name", error)

    def test_boundary_empty_id_number_rejected(self):
        """TC-WSX-287 边界：身份证号空串被拒绝（allow_blank=False）。"""
        error = self.assert_blocked(id_number="")
        self.assertIn("id_number", error)

    # ---------- 异常输入 ----------

    def test_exception_duplicate_id_number_rejected(self):
        """TC-WSX-290 异常：提交他人已用身份证号被拒绝。"""
        self.other.id_number = "110101199001015555"
        self.other.save(update_fields=["id_number"])
        error = self.assert_blocked(id_number="110101199001015555")
        self.assertIn("该身份证号已被使用", error)

    def test_exception_business_fields_not_editable(self):
        """TC-WSX-291 异常：App 不允许提交业务身份与组织字段。"""
        for field, value in (
            ("business_role", "founder"), ("department_id", 1),
            ("team_id", 1), ("manager_id", 1), ("is_active", False), ("password", "x"),
        ):
            error = self.assert_blocked(**{field: value})
            self.assertIn("不允许传入此字段", error, f"{field} 未被拒绝")


class AppMeUpdateTests(OrgMixin, TestCase):
    """AppMeView.update() 接口集成测试（第 16 项）。"""

    def setUp(self):
        self.build_org()
        self.client = APIClient()
        self.client.force_authenticate(user=self.employee)

    def patch_me(self, **data):
        return self.client.patch("/api/v1/app/me/", data, format="json")

    def test_multi_field_update_succeeds(self):
        """TC-WSX-301 正常：姓名与手机号可一并更新。"""
        response = self.patch_me(display_name="张三丰", username="13900000009")
        self.assertEqual(response.status_code, 200, response.data)
        self.employee.refresh_from_db()
        self.assertEqual(self.employee.display_name, "张三丰")
        self.assertEqual(self.employee.username, "13900000009")

    def test_update_writes_app_operation_audit(self):
        """TC-WSX-302 正常：资料更新写入 App 操作审计并记录变更字段。"""
        self.patch_me(display_name="张三丰")
        log = AppOperationAuditLog.objects.get(module=AppOperationAuditLog.Module.PROFILE)
        self.assertEqual(log.changed_fields, ["display_name"])
        self.assertEqual(log.actor, self.employee)

    def test_id_number_change_audit_excludes_plaintext(self):
        """TC-WSX-303 边界：身份证号变更的审计不写入明文。"""
        self.patch_me(id_number="110101199001017777")
        log = AppOperationAuditLog.objects.get(module=AppOperationAuditLog.Module.PROFILE)
        dumped = str(log.before_data) + str(log.after_data)
        self.assertNotIn("110101199001017777", dumped)

    def test_invalid_field_rolls_back_whole_update(self):
        """TC-WSX-304 异常（原子性）：任一字段失败则整次更新不生效。"""
        original = self.employee.display_name
        response = self.patch_me(display_name="新名字", username="manager")
        self.assertEqual(response.status_code, 400)
        self.employee.refresh_from_db()
        self.assertEqual(self.employee.display_name, original)

    def test_no_change_writes_no_audit(self):
        """TC-WSX-305 边界：提交与现值相同的资料不产生审计记录。"""
        self.patch_me(display_name=self.employee.display_name)
        self.assertEqual(AppOperationAuditLog.objects.count(), 0)

    def test_response_returns_masked_id_number(self):
        """TC-WSX-306 正常：更新响应返回脱敏身份证号，不含明文。"""
        self.patch_me(id_number="110101199001017777")
        response = self.client.get("/api/v1/app/me/")
        self.assertEqual(response.data["data"]["id_number_masked"], "**************7777")
        self.assertNotIn("id_number", response.data["data"])


class AppPasswordChangeSerializerTests(OrgMixin, TestCase):
    """AppPasswordChangeSerializer 单元测试（第 17 项）。"""

    def setUp(self):
        self.build_org()
        self.request = SimpleNamespace(user=self.employee)

    def serializer(self, **data):
        return AppPasswordChangeSerializer(data=data, context={"request": self.request})

    def assert_blocked(self, **data):
        serializer = self.serializer(**data)
        self.assertFalse(serializer.is_valid())
        return str(serializer.errors)

    def test_valid_change_accepted_and_saved(self):
        """TC-WSX-311 正常：当前密码正确且两次一致时改密成功。"""
        serializer = self.serializer(
            current_password=PASSWORD, new_password="NewStrong!42", new_password_confirm="NewStrong!42",
        )
        self.assertTrue(serializer.is_valid(), serializer.errors)
        serializer.save()
        self.employee.refresh_from_db()
        self.assertTrue(self.employee.check_password("NewStrong!42"))

    def test_wrong_current_password_rejected(self):
        """TC-WSX-312 异常：当前密码不正确被拒绝。"""
        error = self.assert_blocked(
            current_password="wrong", new_password="NewStrong!42", new_password_confirm="NewStrong!42",
        )
        self.assertIn("当前密码不正确", error)

    def test_mismatched_confirmation_rejected(self):
        """TC-WSX-313 异常：两次输入的新密码不一致被拒绝。"""
        error = self.assert_blocked(
            current_password=PASSWORD, new_password="NewStrong!42", new_password_confirm="Other!42",
        )
        self.assertIn("两次输入的新密码不一致", error)

    def test_same_as_current_rejected(self):
        """TC-WSX-314 异常：新密码与当前密码相同被拒绝。"""
        error = self.assert_blocked(
            current_password=PASSWORD, new_password=PASSWORD, new_password_confirm=PASSWORD,
        )
        self.assertIn("新密码不能与当前密码相同", error)

    def test_weak_password_rejected_by_validator(self):
        """TC-WSX-315 边界：纯数字弱密码被 Django 校验器拒绝。"""
        error = self.assert_blocked(
            current_password=PASSWORD, new_password="12345678", new_password_confirm="12345678",
        )
        self.assertIn("new_password", error)

    def test_missing_fields_rejected(self):
        """TC-WSX-316 边界：三个字段均为必填。"""
        serializer = self.serializer()
        self.assertFalse(serializer.is_valid())
        for field in ("current_password", "new_password", "new_password_confirm"):
            self.assertEqual(serializer.errors[field][0].code, "required")

    def test_new_password_length_boundary_accepted(self):
        """TC-WSX-317 边界：满足长度与复杂度的新密码通过。"""
        serializer = self.serializer(
            current_password=PASSWORD, new_password="Aa1!aaaa", new_password_confirm="Aa1!aaaa",
        )
        self.assertTrue(serializer.is_valid(), serializer.errors)


class AppPasswordChangeViewTests(OrgMixin, TestCase):
    """AppPasswordChangeView.post() 接口集成测试（第 18 项）。"""

    def setUp(self):
        self.build_org()
        self.client = APIClient()

    def test_change_password_returns_success(self):
        """TC-WSX-321 正常：改密成功返回 password_changed=True。"""
        self.client.force_authenticate(user=self.employee)
        response = self.client.post("/api/v1/app/me/password/", {
            "current_password": PASSWORD, "new_password": "NewStrong!42", "new_password_confirm": "NewStrong!42",
        }, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertTrue(response.data["data"]["password_changed"])

    def test_audit_records_field_name_only(self):
        """TC-WSX-322 正常：改密审计只记录字段名，不保存任何密码值。"""
        self.client.force_authenticate(user=self.employee)
        self.client.post("/api/v1/app/me/password/", {
            "current_password": PASSWORD, "new_password": "NewStrong!42", "new_password_confirm": "NewStrong!42",
        }, format="json")
        log = AppOperationAuditLog.objects.get(module=AppOperationAuditLog.Module.PROFILE)
        self.assertEqual(log.changed_fields, ["password"])
        self.assertNotIn("NewStrong!42", str(log.before_data) + str(log.after_data))

    def test_anonymous_denied(self):
        """TC-WSX-323 异常：未登录调用返回 401。"""
        response = self.client.post("/api/v1/app/me/password/", {}, format="json")
        self.assertEqual(response.status_code, 401)

    def test_admin_denied(self):
        """TC-WSX-324 边界：管理员不能调用 App 改密接口。"""
        admin = User.objects.create_superuser(username="admin", password=PASSWORD)
        self.client.force_authenticate(user=admin)
        response = self.client.post("/api/v1/app/me/password/", {}, format="json")
        self.assertEqual(response.status_code, 403)

    def test_old_password_no_longer_valid_after_change(self):
        """TC-WSX-325 正常：改密后旧密码不再可用（服务端不撤销已签发令牌）。"""
        self.client.force_authenticate(user=self.employee)
        self.client.post("/api/v1/app/me/password/", {
            "current_password": PASSWORD, "new_password": "NewStrong!42", "new_password_confirm": "NewStrong!42",
        }, format="json")
        self.employee.refresh_from_db()
        self.assertFalse(self.employee.check_password(PASSWORD))
        self.assertTrue(self.employee.check_password("NewStrong!42"))
