"""王世鑫（WSX24）中期测试：团队创建编辑校验与详情展示。

Issue #42 分工：
- 第 6 项：团队创建与编辑校验 —— TeamSerializer.validate()、TeamUpdateSerializer.validate()（TC-WSX-081～094）
- 第 7 项：团队负责人和成员展示 —— TeamDetailSerializer.get_leader() / get_members()（TC-WSX-101～107）

需求依据：docs/backend/account-provisioning.md「部门和团队（仅管理员）」「组织详情与启停」
> 团队名部门内唯一；团队必须关联已有部门；团队重新启用要求所属部门已启用。
> 团队仍有启用成员时，is_active=false 返回 400；PATCH 不接受 department_id。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度          | 有效等价类                  | 无效等价类                          |
| 团队名称唯一性 | 同部门内未使用；不同部门可同名   | 同部门内已存在同名                   |
| 所属部门      | 启用且存在                  | 不存在；已停用；未提供                |
| 启用状态切换   | 无启用成员时可停用            | 有启用成员时停用；部门停用时启用      |
| 请求字段      | 名称／说明／启用状态           | department_id（编辑时）；只读字段；未知字段 |
| 负责人展示    | 存在启用团队长               | 无团队长；团队长已停用                |

边界值分析
| 边界            | 边界前     | 边界值          | 边界后                |
| 团队名称长度     | 99 字符    | 100 字符（通过） | 101 字符（拒绝）      |
| 同部门启用成员数 | 0 名       | 1 名（拒绝停用）  | —                    |
| 改名与自身重名   | 其他团队同名 | 与自身原名相同（通过） | —                |
"""

from django.test import TestCase

from .api_serializers import TeamDetailSerializer, TeamSerializer, TeamUpdateSerializer
from .models import Department, Team, User


class TeamCreateValidationTests(TestCase):
    """TeamSerializer.validate() 单元测试（第 6 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.existing = Team.objects.create(name="后端组", department=self.department)

    def validate(self, **data):
        serializer = TeamSerializer(data=data)
        if serializer.is_valid():
            return serializer
        self.fail(f"预期校验通过，实际报错：{dict(serializer.errors)}")

    def assert_blocked(self, **data):
        serializer = TeamSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        return str(serializer.errors)

    # ---------- 正常输入 ----------

    def test_creates_team_in_department(self):
        """TC-WSX-081 正常：在同部门内用未占用的名称创建团队通过校验。"""
        serializer = self.validate(name="前端组", department_id=self.department.pk)
        self.assertEqual(serializer.validated_data["department"], self.department)

    def test_same_name_allowed_in_different_department(self):
        """TC-WSX-082 正常：不同部门允许同名团队（唯一性作用域为部门内）。"""
        other = Department.objects.create(name="财务部")
        serializer = self.validate(name="后端组", department_id=other.pk)
        self.assertEqual(serializer.validated_data["department"], other)

    def test_description_optional(self):
        """TC-WSX-083 正常：说明字段可省略。"""
        serializer = self.validate(name="前端组", department_id=self.department.pk)
        self.assertNotIn("description", serializer.validated_data)

    # ---------- 边界值 ----------

    def test_boundary_duplicate_name_in_same_department_rejected(self):
        """TC-WSX-084 边界：同部门已存在同名团队时拒绝。"""
        error = self.assert_blocked(name="后端组", department_id=self.department.pk)
        self.assertIn("该部门已存在同名团队", error)

    def test_boundary_name_length_100_accepted(self):
        """TC-WSX-085 边界：团队名称 100 字符（上界）通过。"""
        self.validate(name="组" * 100, department_id=self.department.pk)

    def test_boundary_name_length_101_rejected(self):
        """TC-WSX-086 边界：团队名称 101 字符（上界外）被拒绝。"""
        error = self.assert_blocked(name="组" * 101, department_id=self.department.pk)
        self.assertIn("name", error)

    def test_boundary_whitespace_only_name_rejected(self):
        """TC-WSX-087 边界：团队名称纯空白被拒绝。"""
        error = self.assert_blocked(name="   ", department_id=self.department.pk)
        self.assertIn("name", error)

    # ---------- 异常输入 ----------

    def test_exception_missing_department(self):
        """TC-WSX-088 异常：未提供所属部门时拒绝创建。"""
        error = self.assert_blocked(name="前端组")
        self.assertIn("department_id", error)

    def test_exception_nonexistent_department(self):
        """TC-WSX-089 异常：所属部门不存在时拒绝。"""
        error = self.assert_blocked(name="前端组", department_id=999999)
        self.assertIn("department_id", error)

    def test_exception_inactive_department_rejected(self):
        """TC-WSX-090 异常：所属部门已停用时拒绝创建团队。"""
        inactive = Department.objects.create(name="撤销部", is_active=False)
        error = self.assert_blocked(name="前端组", department_id=inactive.pk)
        self.assertIn("department_id", error)

    def test_exception_read_only_is_active_rejected(self):
        """TC-WSX-091 异常：创建时传入只读字段 is_active 被拒绝。"""
        error = self.assert_blocked(name="前端组", department_id=self.department.pk, is_active=False)
        self.assertIn("is_active", error)


class TeamUpdateValidationTests(TestCase):
    """TeamUpdateSerializer.validate() 单元测试（第 6 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.other = Team.objects.create(name="前端组", department=self.department)

    def assert_blocked(self, team, **data):
        serializer = TeamUpdateSerializer(instance=team, data=data, partial=True)
        self.assertFalse(serializer.is_valid())
        return str(serializer.errors)

    # ---------- 正常输入 ----------

    def test_rename_to_unused_name_allowed(self):
        """TC-WSX-092 正常：改成部门内未使用的名称通过。"""
        serializer = TeamUpdateSerializer(instance=self.team, data={"name": "中台组"}, partial=True)
        self.assertTrue(serializer.is_valid(), serializer.errors)

    def test_rename_to_own_name_allowed(self):
        """TC-WSX-093 正常：保持原名（与自身重名）通过，唯一性排除自身。"""
        serializer = TeamUpdateSerializer(instance=self.team, data={"name": "后端组"}, partial=True)
        self.assertTrue(serializer.is_valid(), serializer.errors)

    def test_deactivate_team_without_members_allowed(self):
        """TC-WSX-094 正常：无启用成员时允许停用团队。"""
        serializer = TeamUpdateSerializer(instance=self.team, data={"is_active": False}, partial=True)
        self.assertTrue(serializer.is_valid(), serializer.errors)


class TeamUpdateBoundaryTests(TestCase):
    """TeamUpdateSerializer.validate() 边界与异常分支（第 6 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.other = Team.objects.create(name="前端组", department=self.department)

    def assert_blocked(self, team, **data):
        serializer = TeamUpdateSerializer(instance=team, data=data, partial=True)
        self.assertFalse(serializer.is_valid())
        return str(serializer.errors)

    def test_boundary_rename_to_sibling_team_name_rejected(self):
        """TC-WSX-095 边界：改名为同部门其他团队名称时拒绝。"""
        error = self.assert_blocked(self.team, name=self.other.name)
        self.assertIn("该部门已存在同名团队", error)

    def test_boundary_one_active_member_blocks_deactivation(self):
        """TC-WSX-096 边界：存在 1 名启用成员时禁止停用团队。"""
        User.objects.create_user(
            username="leader", password="x", display_name="团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team,
        )
        error = self.assert_blocked(self.team, is_active=False)
        self.assertIn("名启用成员", error)

    def test_activation_requires_active_department(self):
        """TC-WSX-097 边界（状态切换点）：所属部门停用后不能启用团队。"""
        Department.objects.filter(pk=self.department.pk).update(is_active=False)
        self.team.refresh_from_db()
        error = self.assert_blocked(self.team, is_active=True)
        self.assertIn("所属部门已停用", error)

    def test_department_id_not_accepted_on_update(self):
        """TC-WSX-098 异常：编辑团队不接受 department_id（不允许跨部门搬移）。"""
        other_department = Department.objects.create(name="财务部")
        error = self.assert_blocked(self.team, department_id=other_department.pk)
        self.assertIn("不允许传入此字段", error)

    def test_unknown_field_rejected(self):
        """TC-WSX-099 异常：传入未知字段被拒绝。"""
        error = self.assert_blocked(self.team, unknown="x")
        self.assertIn("不允许传入此字段", error)

    def test_inactive_member_does_not_block_deactivation(self):
        """TC-WSX-100 边界：成员全部停用后允许停用团队。"""
        leader = User.objects.create_user(
            username="leader", password="x", display_name="团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team, is_active=False,
        )
        self.assertFalse(leader.is_active)
        serializer = TeamUpdateSerializer(instance=self.team, data={"is_active": False}, partial=True)
        self.assertTrue(serializer.is_valid(), serializer.errors)


class TeamDetailDisplayTests(TestCase):
    """TeamDetailSerializer 展示方法单元测试（第 7 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.leader = User.objects.create_user(
            username="leader", password=self.password, display_name="团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team,
        )
        self.employee = User.objects.create_user(
            username="employee", password=self.password, display_name="员工",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=self.department, team=self.team, manager=self.leader,
        )
        self.serializer = TeamDetailSerializer()

    # ---------- 正常输入 ----------

    def test_leader_returns_active_team_leader(self):
        """TC-WSX-101 正常：get_leader 返回启用团队长的 id 与姓名。"""
        self.assertEqual(
            self.serializer.get_leader(self.team),
            {"id": self.leader.pk, "display_name": "团队长"},
        )

    def test_members_include_leader_and_employee(self):
        """TC-WSX-104 正常：get_members 返回启用成员（团队长与员工）。"""
        rows = self.serializer.get_members(self.team)
        self.assertEqual({row["id"] for row in rows}, {self.leader.pk, self.employee.pk})

    # ---------- 边界值 ----------

    def test_boundary_no_leader_returns_null(self):
        """TC-WSX-102 边界：团队无团队长时 get_leader 返回 None。"""
        empty_team = Team.objects.create(name="前端组", department=self.department)
        self.assertIsNone(TeamDetailSerializer().get_leader(empty_team))

    def test_boundary_inactive_leader_returns_null(self):
        """TC-WSX-103 边界（状态切换点）：团队长停用后 get_leader 返回 None。"""
        User.objects.filter(pk=self.leader.pk).update(is_active=False)
        self.assertIsNone(TeamDetailSerializer().get_leader(self.team))

    def test_boundary_empty_members_returns_empty_list(self):
        """TC-WSX-105 边界：团队无成员时 get_members 返回空列表。"""
        empty_team = Team.objects.create(name="前端组", department=self.department)
        self.assertEqual(TeamDetailSerializer().get_members(empty_team), [])

    def test_boundary_inactive_member_excluded(self):
        """TC-WSX-106 边界：停用成员不出现在 get_members 结果中。"""
        User.objects.filter(pk=self.employee.pk).update(is_active=False)
        rows = TeamDetailSerializer().get_members(self.team)
        self.assertEqual({row["id"] for row in rows}, {self.leader.pk})

    def test_department_name_exposed_read_only(self):
        """TC-WSX-107 正常：详情返回所属部门 id 与名称，便于管理端展示。"""
        data = self.serializer.to_representation(self.team)
        self.assertEqual(data["department_id"], self.department.pk)
        self.assertEqual(data["department_name"], "研发部")
