"""王世鑫（WSX24）中期测试：员工录入、创建结果筛选与身份证脱敏。

Issue #42 分工：
- 第 8 项：员工录入和初始密码 —— BusinessUserCreateSerializer.validate() / create()（TC-WSX-120～140）
  （第 8 项的 normalize_id_number() 支撑函数见 test_midterm_wsx_id_number.py，TC-WSX-111～119）
- 第 9 项：员工创建结果及筛选 —— UserListView.create() / get_queryset()（TC-WSX-141～155）
- 第 10 项：员工身份证脱敏 —— UserSerializer.get_id_number_masked()（TC-WSX-161～170）

需求依据：docs/backend/account-provisioning.md「业务用户（仅管理员）」
> 身份证号必填，最长 64 个字符；去除首尾空格并将字母统一为大写后再保存及检查全局唯一性。
> 姓名必填且最长 100；手机号存入 username，遵循 Django 字段规则且最长 150。
> 客户端不得提交 password、is_active 或 manager_id。
> 服务端自动启用，生成恰好 16 个 URL-safe 字符的初始密码；数据库只保存密码哈希。
> 创建响应设置 Cache-Control/ Pragma/ Expires；后续读取不再返回初始密码或完整身份证号。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度           | 有效等价类                 | 无效等价类                                   |
| 身份证号        | 未占用、≤64 字符            | 缺失；重复（含大小写差异）；超过 64 字符        |
| 姓名           | 非空白、≤100 字符           | 缺失；纯空白；超过 100 字符                    |
| 业务身份        | founder/manager/leader/employee | 非法枚举值                              |
| 客户端提交字段   | username/id_number/display_name/business_role/组织 | password、is_active、manager_id、account_type |
| 列表筛选        | keyword/business_role/department_id/team_id/is_active | 非法取值（静默忽略）              |
| 脱敏长度        | 长度 > 4                    | 长度 ≤ 4；空值                                 |

边界值分析
| 边界              | 边界前      | 边界值           | 边界后                |
| 身份证号长度       | 63 字符     | 64 字符（通过）   | 65 字符（拒绝）        |
| 姓名长度          | 99 字符     | 100 字符（通过）  | 101 字符（拒绝）       |
| 脱敏保留位数       | 长度 4（全掩码） | 长度 5（露 4 位） | 长度 18（露 4 位）    |
| 初始密码长度       | 15 字符     | 16 字符（契约值）  | 17 字符               |
"""

from types import SimpleNamespace

from django.test import TestCase
from rest_framework.test import APIClient

from .api_serializers import BusinessUserCreateSerializer, UserSerializer
from .api_views import UserListView
from .models import AdminAuditLog, Department, Team, User


class BusinessUserCreateTests(TestCase):
    """BusinessUserCreateSerializer 单元测试（第 8 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.founder = User.objects.create_user(
            username="founder", password=self.password, display_name="创始人",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        self.manager = User.objects.create_user(
            username="manager", password=self.password, display_name="经理",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.DEPARTMENT_MANAGER,
            department=self.department, manager=self.founder,
        )
        self.leader = User.objects.create_user(
            username="leader", password=self.password, display_name="团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team, manager=self.manager,
        )

    def employee_payload(self, **overrides):
        payload = {
            "username": "13800000001",
            "id_number": "110101199001011234",
            "display_name": "张三",
            "business_role": User.Role.EMPLOYEE,
            "department_id": self.department.pk,
            "team_id": self.team.pk,
        }
        payload.update(overrides)
        return payload

    def save_user(self, **overrides):
        serializer = BusinessUserCreateSerializer(data=self.employee_payload(**overrides))
        serializer.is_valid(raise_exception=True)
        return serializer, serializer.save()

    def assert_rejected(self, **overrides):
        serializer = BusinessUserCreateSerializer(data=self.employee_payload(**overrides))
        self.assertFalse(serializer.is_valid())
        return str(serializer.errors)

    # ---------- 正常输入 ----------

    def test_creates_employee_and_links_manager(self):
        """TC-WSX-120 正常：录入员工后自动关联本团队团队长为直属上级。"""
        _, user = self.save_user()
        self.assertEqual(user.manager, self.leader)
        self.assertEqual(user.account_type, User.AccountType.BUSINESS)

    def test_initial_password_is_exactly_16_chars(self):
        """TC-WSX-121 正常：初始密码恰好 16 个 URL-safe 字符。"""
        serializer, _ = self.save_user()
        self.assertEqual(len(serializer.initial_password), 16)

    def test_password_stored_as_hash_only(self):
        """TC-WSX-122 正常：数据库只保存密码哈希，明文仅用于本次返回。"""
        serializer, user = self.save_user()
        user.refresh_from_db()
        self.assertNotEqual(user.password, serializer.initial_password)
        self.assertTrue(user.check_password(serializer.initial_password))

    def test_account_created_active(self):
        """TC-WSX-123 正常：新账号由服务端自动启用。"""
        _, user = self.save_user()
        self.assertTrue(user.is_active)

    def test_id_number_normalized_on_create(self):
        """TC-WSX-124 正常：身份证号保存前去首尾空格并转大写。"""
        _, user = self.save_user(id_number=" 11010119900101123x ")
        self.assertEqual(user.id_number, "11010119900101123X")

    def test_initial_password_differs_between_accounts(self):
        """TC-WSX-140 正常：两次录入生成的初始密码互不相同。"""
        first, _ = self.save_user()
        second, _ = self.save_user(username="13800000002", id_number="110101199001011235")
        self.assertNotEqual(first.initial_password, second.initial_password)

    # ---------- 边界值 ----------

    def test_boundary_id_number_length_64_accepted(self):
        """TC-WSX-126 边界：身份证号 64 字符（字段上界）通过。"""
        _, user = self.save_user(id_number="9" * 64)
        self.assertEqual(len(user.id_number), 64)

    def test_boundary_id_number_length_65_rejected(self):
        """TC-WSX-127 边界：身份证号 65 字符（上界外）被拒绝。"""
        error = self.assert_rejected(id_number="9" * 65)
        self.assertIn("id_number", error)

    def test_boundary_display_name_length_100_accepted(self):
        """TC-WSX-128 边界：姓名 100 字符（上界）通过。"""
        _, user = self.save_user(display_name="名" * 100)
        self.assertEqual(len(user.display_name), 100)

    def test_boundary_display_name_length_101_rejected(self):
        """TC-WSX-129 边界：姓名 101 字符（上界外）被拒绝。"""
        error = self.assert_rejected(display_name="名" * 101)
        self.assertIn("display_name", error)

    def test_boundary_whitespace_display_name_rejected(self):
        """TC-WSX-137 边界：姓名纯空白被拒绝。"""
        error = self.assert_rejected(display_name="   ")
        self.assertIn("display_name", error)

    # ---------- 异常输入 ----------

    def test_exception_missing_id_number(self):
        """TC-WSX-130 异常：身份证号必填，缺失时拒绝。"""
        payload = self.employee_payload()
        payload.pop("id_number")
        serializer = BusinessUserCreateSerializer(data=payload)
        self.assertFalse(serializer.is_valid())
        self.assertIn("id_number", serializer.errors)

    def test_exception_duplicate_id_number(self):
        """TC-WSX-131 异常：身份证号已存在时拒绝录入。"""
        self.save_user()
        error = self.assert_rejected(username="13800000009")
        self.assertIn("该身份证号已被使用", error)

    def test_exception_duplicate_id_number_case_insensitive(self):
        """TC-WSX-132 异常：大小写不同的同一身份证号仍视为重复。"""
        self.save_user(id_number="11010119900101123X")
        error = self.assert_rejected(
            username="13800000009", id_number="11010119900101123x",
        )
        self.assertIn("该身份证号已被使用", error)

    def test_exception_password_field_rejected(self):
        """TC-WSX-133 异常：客户端不得提交 password。"""
        error = self.assert_rejected(password="Hack-1234!")
        self.assertIn("不允许传入此字段", error)

    def test_exception_is_active_field_rejected(self):
        """TC-WSX-134 异常：客户端不得提交 is_active。"""
        error = self.assert_rejected(is_active=False)
        self.assertIn("不允许传入此字段", error)

    def test_exception_manager_id_field_rejected(self):
        """TC-WSX-135 异常：客户端不得指定 manager_id（由服务端计算）。"""
        error = self.assert_rejected(manager_id=self.founder.pk)
        self.assertIn("不允许传入此字段", error)

    def test_exception_missing_display_name(self):
        """TC-WSX-136 异常：姓名必填，缺失时拒绝。"""
        payload = self.employee_payload()
        payload.pop("display_name")
        serializer = BusinessUserCreateSerializer(data=payload)
        self.assertFalse(serializer.is_valid())
        self.assertIn("display_name", serializer.errors)

    def test_exception_invalid_business_role(self):
        """TC-WSX-138 异常：业务身份不在枚举范围时拒绝。"""
        error = self.assert_rejected(business_role="ceo")
        self.assertIn("business_role", error)

    def test_appointing_manager_rebuilds_direct_reports(self):
        """TC-WSX-139 正常：任命新部门经理后，本部门团队长的直属上级被重建。"""
        serializer = BusinessUserCreateSerializer(data={
            "username": "manager2", "id_number": "110101199001019999", "display_name": "新经理",
            "business_role": User.Role.DEPARTMENT_MANAGER, "department_id": self.department.pk,
        })
        # 本部门已有启用经理，应先停用再任命，验证唯一性约束下的重建路径
        User.objects.filter(pk=self.manager.pk).update(is_active=False)
        serializer = BusinessUserCreateSerializer(data={
            "username": "manager2", "id_number": "110101199001019999", "display_name": "新经理",
            "business_role": User.Role.DEPARTMENT_MANAGER, "department_id": self.department.pk,
        })
        serializer.is_valid(raise_exception=True)
        new_manager = serializer.save()
        self.leader.refresh_from_db()
        self.assertEqual(self.leader.manager, new_manager)


class UserCreateResponseTests(TestCase):
    """UserListView.create() 接口集成测试（第 9 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.founder = User.objects.create_user(
            username="founder", password=self.password, display_name="创始人",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        self.leader = User.objects.create_user(
            username="leader", password=self.password, display_name="团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team, manager=self.founder,
        )
        self.client = APIClient()
        self.client.force_authenticate(user=self.admin)

    def post_user(self, **overrides):
        payload = {
            "username": "13800000001", "id_number": "110101199001011234",
            "display_name": "张三", "business_role": User.Role.EMPLOYEE,
            "department_id": self.department.pk, "team_id": self.team.pk,
        }
        payload.update(overrides)
        return self.client.post("/api/v1/admin/users/", payload, format="json")

    def test_create_returns_201_with_initial_password(self):
        """TC-WSX-141 正常：创建成功返回 201 与一次性初始密码。"""
        response = self.post_user()
        self.assertEqual(response.status_code, 201, response.data)
        self.assertEqual(len(response.data["data"]["initial_password"]), 16)

    def test_create_sets_no_store_headers(self):
        """TC-WSX-142 正常：创建响应禁止缓存（含明文初始密码）。"""
        response = self.post_user()
        self.assertEqual(response["Cache-Control"], "no-store, no-cache, max-age=0, private")
        self.assertEqual(response["Pragma"], "no-cache")
        self.assertEqual(response["Expires"], "0")

    def test_create_response_only_returns_masked_id_number(self):
        """TC-WSX-143 正常：创建响应只返回脱敏身份证号，不含明文。"""
        response = self.post_user()
        data = response.data["data"]
        self.assertNotIn("id_number", data)
        self.assertEqual(data["id_number_masked"], "**************1234")

    def test_create_writes_audit_log(self):
        """TC-WSX-144 正常：创建员工写入管理端审计，且不含身份证明文。"""
        self.post_user()
        log = AdminAuditLog.objects.get(module=AdminAuditLog.Module.USERS, action="create")
        self.assertNotIn("id_number", str(log.after_data))

    def test_list_returns_masked_only_and_no_password(self):
        """TC-WSX-145 正常：列表不返回初始密码与完整身份证号。"""
        self.post_user()
        response = self.client.get("/api/v1/admin/users/")
        row = response.data["data"]["results"][0]
        self.assertNotIn("initial_password", row)
        self.assertNotIn("password", row)
        self.assertNotIn("id_number", row)

    def test_duplicate_id_number_returns_400(self):
        """TC-WSX-151 异常：身份证号重复时返回 400，不创建账号。"""
        self.post_user()
        response = self.post_user(username="13800000002")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(User.objects.filter(username="13800000002").exists(), False)


class UserListQuerySetTests(TestCase):
    """UserListView.get_queryset() 单元测试（第 9 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.founder = User.objects.create_user(
            username="13800000001", password=self.password, display_name="创始人",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        self.leader = User.objects.create_user(
            username="13800000002", password=self.password, display_name="团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team, manager=self.founder,
        )
        self.employee = User.objects.create_user(
            username="13800000003", password=self.password, display_name="张三",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=self.department, team=self.team, manager=self.leader,
        )

    def queryset(self, **params):
        view = UserListView()
        view.request = SimpleNamespace(query_params=params)
        return view.get_queryset()

    def test_excludes_admin_accounts(self):
        """TC-WSX-146 正常：管理列表只含业务账号，不含管理员。"""
        usernames = set(self.queryset().values_list("username", flat=True))
        self.assertNotIn("admin", usernames)
        self.assertEqual(len(usernames), 3)

    def test_keyword_filters_by_display_name(self):
        """TC-WSX-147 正常：keyword 按姓名模糊匹配。"""
        self.assertEqual([u.pk for u in self.queryset(keyword="张")], [self.employee.pk])

    def test_keyword_filters_by_phone(self):
        """TC-WSX-148 正常：keyword 按手机号模糊匹配。"""
        self.assertEqual([u.pk for u in self.queryset(keyword="13800000002")], [self.leader.pk])

    def test_filters_by_business_role(self):
        """TC-WSX-149 正常：按业务身份过滤。"""
        self.assertEqual([u.pk for u in self.queryset(business_role="employee")], [self.employee.pk])

    def test_filters_by_department_and_team(self):
        """TC-WSX-150 正常：按部门与团队过滤。"""
        self.assertEqual(len(self.queryset(department_id=self.department.pk)), 2)
        self.assertEqual(len(self.queryset(team_id=self.team.pk)), 2)

    def test_boundary_is_active_false_filters_inactive(self):
        """TC-WSX-152 边界：is_active=false 只返回停用账号。"""
        User.objects.filter(pk=self.employee.pk).update(is_active=False)
        self.assertEqual([u.pk for u in self.queryset(is_active="false")], [self.employee.pk])

    def test_boundary_invalid_is_active_value_is_ignored(self):
        """TC-WSX-153 边界：is_active 非 true/false 时不过滤（返回全部）。"""
        self.assertEqual(len(self.queryset(is_active="yes")), 3)

    def test_boundary_keyword_without_match_returns_empty(self):
        """TC-WSX-154 边界：keyword 无匹配时返回空结果。"""
        self.assertEqual(list(self.queryset(keyword="不存在的人")), [])

    def test_missing_filter_returns_all(self):
        """TC-WSX-155 正常：不传筛选条件时返回全部业务账号。"""
        self.assertEqual(len(self.queryset()), 3)


class IdNumberMaskTests(TestCase):
    """UserSerializer.get_id_number_masked() 单元测试（第 10 项）。"""

    serializer = UserSerializer()

    def mask(self, value):
        return self.serializer.get_id_number_masked(User(id_number=value))

    # ---------- 正常输入 ----------

    def test_masks_all_but_last_four_digits(self):
        """TC-WSX-161 正常：18 位身份证号保留后 4 位，其余掩码。"""
        self.assertEqual(self.mask("110101199001011234"), "**************1234")

    def test_keeps_trailing_letter_x(self):
        """TC-WSX-162 正常：末位为字母 X 时同样保留。"""
        self.assertEqual(self.mask("11010119900101123X"), "**************123X")

    # ---------- 边界值 ----------

    def test_boundary_length_four_masks_all(self):
        """TC-WSX-163 边界：长度 4（下界）全部掩码，不保留任何字符。"""
        self.assertEqual(self.mask("1234"), "****")

    def test_boundary_length_five_keeps_last_four(self):
        """TC-WSX-164 边界：长度 5（越过掩码分界）保留后 4 位。"""
        self.assertEqual(self.mask("12345"), "*2345")

    def test_boundary_length_one_masks_all(self):
        """TC-WSX-165 边界：长度 1 掩码为单个星号。"""
        self.assertEqual(self.mask("1"), "*")

    def test_boundary_length_64_keeps_last_four(self):
        """TC-WSX-166 边界：长度 64（字段上界）掩码 60 位、保留后 4 位。"""
        masked = self.mask("9" * 64)
        self.assertEqual(len(masked), 64)
        self.assertEqual(masked, "*" * 60 + "9999")

    def test_boundary_none_returns_none(self):
        """TC-WSX-167 边界：无身份证号时返回 None（未录入账号）。"""
        self.assertIsNone(self.mask(None))

    def test_boundary_empty_string_returns_none(self):
        """TC-WSX-168 边界：空串视为未录入，返回 None。"""
        self.assertIsNone(self.mask(""))

    # ---------- 异常输入 ----------

    def test_masked_value_never_contains_full_plaintext(self):
        """TC-WSX-169 异常：脱敏结果不得包含完整明文身份证号。"""
        plaintext = "110101199001011234"
        self.assertNotIn(plaintext, self.mask(plaintext))

    def test_mask_of_length_three(self):
        """TC-WSX-170 边界：长度 3 全部掩码为 3 个星号。"""
        self.assertEqual(self.mask("123"), "***")
