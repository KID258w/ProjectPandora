"""王世鑫（WSX24）中期测试：部门创建、编辑停用与详情展示。

Issue #42 分工：
- 第 3 项：部门创建 —— DepartmentListView.perform_create()（TC-WSX-041～050）
- 第 4 项：部门编辑与停用 —— DepartmentUpdateSerializer.validate()（TC-WSX-051～060）
- 第 5 项：部门负责人和成员展示 —— DepartmentDetailSerializer.get_manager() / get_members()（TC-WSX-071～077）

需求依据：docs/backend/account-provisioning.md「部门和团队（仅管理员）」「组织详情与启停」
> 部门名全局唯一，name 最长 100 且不允许空白。
> 部门仍有启用下属团队或成员时，is_active=false 返回 400。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度             | 有效等价类              | 无效等价类                        |
| 部门名称         | 非空白、≤100 字符、未被占用 | 纯空白；超过 100 字符；与已有部门重名 |
| 启用状态切换      | 无启用下属时可停用        | 有启用团队或启用成员时停用            |
| 请求字段         | 名称／说明／启用状态      | 只读字段 is_active（创建时）；未知字段 |
| 负责人展示        | 存在启用部门经理          | 无经理；经理已停用                  |
| 成员展示         | 存在启用成员              | 无成员；成员全部停用                |

边界值分析
| 边界            | 边界前     | 边界值          | 边界后                |
| 部门名称长度     | 99 字符    | 100 字符（通过） | 101 字符（拒绝）      |
| 部门名称内容     | 正常字符串  | 纯空白（拒绝）   | —                    |
| 启用下属团队数   | 0 个       | 1 个（拒绝停用） | 6 个以上（文案折叠）   |
"""



from django.test import TestCase

from .api_serializers import (
    DepartmentDetailSerializer,
    DepartmentSerializer,
    DepartmentUpdateSerializer,
)
from .api_views import DepartmentListView
from .models import AdminAuditLog, Department, Team, User


class DepartmentCreateTests(TestCase):
    """DepartmentListView.perform_create() 单元测试（第 3 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        from types import SimpleNamespace

        self.view = DepartmentListView()
        self.view.request = SimpleNamespace(user=self.admin)

    def create(self, **data):
        serializer = DepartmentSerializer(data=data)
        serializer.is_valid(raise_exception=True)
        self.view.perform_create(serializer)
        return serializer.instance

    # ---------- 正常输入 ----------

    def test_saves_department(self):
        """TC-WSX-041 正常：perform_create 保存部门并返回实例。"""
        department = self.create(name="研发部", description="技术研发")
        self.assertTrue(Department.objects.filter(pk=department.pk, name="研发部").exists())

    def test_writes_department_audit_log(self):
        """TC-WSX-042 正常：创建部门后写入管理端审计，模块与动作正确。"""
        department = self.create(name="研发部")
        log = AdminAuditLog.objects.get(module=AdminAuditLog.Module.DEPARTMENTS, action="create")
        self.assertEqual(log.object_type, "department")
        self.assertEqual(log.object_id, str(department.pk))
        self.assertEqual(log.result, AdminAuditLog.Result.SUCCESS)

    def test_audit_after_snapshot_contains_summary_only(self):
        """TC-WSX-043 正常：审计 after 仅含名称、说明、启用状态，不含敏感字段。"""
        self.create(name="研发部", description="技术研发")
        log = AdminAuditLog.objects.get(action="create")
        self.assertEqual(
            sorted(log.after_data.keys()), ["description", "is_active", "name"],
        )
        self.assertTrue(log.after_data["is_active"])

    def test_audit_actor_is_current_admin(self):
        """TC-WSX-044 正常：审计记录的操作者取自当前请求用户。"""
        self.create(name="研发部")
        self.assertEqual(AdminAuditLog.objects.get(action="create").actor, self.admin)

    def test_description_defaults_to_empty(self):
        """TC-WSX-045 正常：说明字段可省略，默认空字符串。"""
        department = self.create(name="研发部")
        self.assertEqual(department.description, "")

    # ---------- 边界值 ----------

    def test_boundary_name_length_100_accepted(self):
        """TC-WSX-046 边界：部门名称 100 字符（上界）通过。"""
        department = self.create(name="部" * 100)
        self.assertEqual(len(department.name), 100)

    def test_boundary_name_length_101_rejected(self):
        """TC-WSX-047 边界：部门名称 101 字符（上界外）被拒绝。"""
        serializer = DepartmentSerializer(data={"name": "部" * 101})
        self.assertFalse(serializer.is_valid())
        self.assertIn("name", serializer.errors)

    def test_boundary_whitespace_only_name_rejected(self):
        """TC-WSX-048 边界：部门名称纯空白被拒绝（去空白后为空）。"""
        serializer = DepartmentSerializer(data={"name": "   "})
        self.assertFalse(serializer.is_valid())
        self.assertIn("name", serializer.errors)

    def test_new_department_is_active_by_default(self):
        """TC-WSX-049 边界：新建部门默认启用。"""
        self.assertTrue(self.create(name="研发部").is_active)

    # ---------- 异常输入 ----------

    def test_read_only_field_is_active_rejected(self):
        """TC-WSX-050 异常：创建时传入只读字段 is_active，请求被拒绝。"""
        serializer = DepartmentSerializer(data={"name": "研发部", "is_active": False})
        self.assertFalse(serializer.is_valid())
        self.assertIn("is_active", serializer.errors)


class DepartmentUpdateTests(TestCase):
    """DepartmentUpdateSerializer.validate() 单元测试（第 4 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.admin = User.objects.create_superuser(username="admin", password=self.password)

    def validate(self, department, **data):
        serializer = DepartmentUpdateSerializer(instance=department, data=data, partial=True)
        serializer.is_valid(raise_exception=True)
        return serializer.validated_data

    def assert_blocked(self, department, **data):
        serializer = DepartmentUpdateSerializer(instance=department, data=data, partial=True)
        self.assertFalse(serializer.is_valid())
        return str(serializer.errors)

    # ---------- 正常输入 ----------

    def test_description_update_allowed(self):
        """TC-WSX-051 正常：仅修改说明时通过校验。"""
        self.assertEqual(self.validate(self.department, description="新说明")["description"], "新说明")

    def test_deactivate_department_without_active_subordinates(self):
        """TC-WSX-052 正常：无启用下属时允许停用部门。"""
        empty = Department.objects.create(name="财务部")
        self.assertFalse(self.validate(empty, is_active=False)["is_active"])

    def test_reactivate_department_allowed(self):
        """TC-WSX-053 正常：重新启用部门通过校验（不检查下属）。"""
        Department.objects.filter(pk=self.department.pk).update(is_active=False)
        self.department.refresh_from_db()
        self.assertTrue(self.validate(self.department, is_active=True)["is_active"])

    # ---------- 边界值 ----------

    def test_boundary_one_active_team_blocks_deactivation(self):
        """TC-WSX-054 边界：存在 1 个启用团队（下界外）时禁止停用，文案指出团队数量。"""
        error = self.assert_blocked(self.department, is_active=False)
        self.assertIn("1个启用下属团队", error)

    def test_boundary_one_active_member_blocks_deactivation(self):
        """TC-WSX-055 边界：存在 1 名启用成员时禁止停用，文案指出成员数量。"""
        User.objects.create_user(
            username="manager", password=self.password,
            account_type=User.AccountType.BUSINESS, business_role=User.Role.DEPARTMENT_MANAGER,
            department=self.department,
        )
        error = self.assert_blocked(self.department, is_active=False)
        self.assertIn("1名启用成员", error)

    def test_boundary_both_teams_and_members_reported(self):
        """TC-WSX-056 边界：同时存在启用团队与成员时，两类阻塞项都出现在文案里。"""
        User.objects.create_user(
            username="leader", password=self.password,
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team,
        )
        error = self.assert_blocked(self.department, is_active=False)
        self.assertIn("启用下属团队", error)
        self.assertIn("启用成员", error)

    def test_boundary_many_teams_summary_is_folded(self):
        """TC-WSX-057 边界：启用团队超过 5 个时，文案折叠为“另有 N 个”。"""
        for index in range(6):
            Team.objects.create(name=f"组{index}", department=self.department)
        error = self.assert_blocked(self.department, is_active=False)
        self.assertIn("另有", error)

    def test_all_subordinates_inactive_allows_deactivation(self):
        """TC-WSX-058 边界：团队与成员全部停用后允许停用部门。"""
        Team.objects.filter(pk=self.team.pk).update(is_active=False)
        self.assertFalse(self.validate(self.department, is_active=False)["is_active"])

    # ---------- 异常输入 ----------

    def test_unknown_field_rejected(self):
        """TC-WSX-059 异常：传入未知字段被拒绝。"""
        error = self.assert_blocked(self.department, unknown_field="x")
        self.assertIn("不允许传入此字段", error)

    def test_rename_to_existing_department_rejected(self):
        """TC-WSX-060 异常：部门改名与已有名称冲突时被拒绝。

        部门名全局唯一（需求：部门名全局唯一）。虽然 DepartmentUpdateSerializer 在
        Meta 中声明了 validators = []，但 DRF 在字段级仍会为 unique 字段附上
        UniqueValidator，因此重名在序列化层即被拦截。
        """
        other = Department.objects.create(name="财务部")
        error = self.assert_blocked(other, name=self.department.name)
        self.assertIn("已存在", error)


class DepartmentDetailDisplayTests(TestCase):
    """DepartmentDetailSerializer 展示方法单元测试（第 5 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.manager = User.objects.create_user(
            username="manager", password=self.password, display_name="部门经理",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.DEPARTMENT_MANAGER,
            department=self.department,
        )
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.leader = User.objects.create_user(
            username="leader", password=self.password, display_name="团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team, manager=self.manager,
        )
        self.serializer = DepartmentDetailSerializer()

    # ---------- 正常输入 ----------

    def test_manager_returns_active_manager(self):
        """TC-WSX-071 正常：get_manager 返回启用部门经理的 id 与姓名。"""
        self.assertEqual(
            self.serializer.get_manager(self.department),
            {"id": self.manager.pk, "display_name": "部门经理"},
        )

    def test_members_list_active_users_only(self):
        """TC-WSX-074 正常：get_members 只返回启用成员，含经理与团队长。"""
        rows = self.serializer.get_members(self.department)
        self.assertEqual({row["id"] for row in rows}, {self.manager.pk, self.leader.pk})
        self.assertEqual(sorted(rows[0].keys()), ["business_role", "display_name", "id"])

    # ---------- 边界值 ----------

    def test_boundary_no_manager_returns_null(self):
        """TC-WSX-072 边界：部门无经理时 get_manager 返回 None。"""
        empty = Department.objects.create(name="财务部")
        self.assertIsNone(DepartmentDetailSerializer().get_manager(empty))

    def test_boundary_inactive_manager_returns_null(self):
        """TC-WSX-073 边界：经理停用后 get_manager 返回 None（状态切换点）。"""
        User.objects.filter(pk=self.manager.pk).update(is_active=False)
        self.assertIsNone(DepartmentDetailSerializer().get_manager(self.department))

    def test_boundary_empty_members_returns_empty_list(self):
        """TC-WSX-075 边界：部门无成员时 get_members 返回空列表。"""
        empty = Department.objects.create(name="财务部")
        self.assertEqual(DepartmentDetailSerializer().get_members(empty), [])

    def test_boundary_inactive_member_excluded(self):
        """TC-WSX-076 边界：停用成员不出现在 get_members 结果中。"""
        User.objects.filter(pk=self.leader.pk).update(is_active=False)
        rows = DepartmentDetailSerializer().get_members(self.department)
        self.assertEqual({row["id"] for row in rows}, {self.manager.pk})

    def test_boundary_teams_include_inactive_units(self):
        """TC-WSX-077 边界：get_teams 返回全部团队（含已停用），便于管理端看到历史组织。"""
        Team.objects.filter(pk=self.team.pk).update(is_active=False)
        rows = self.serializer.get_teams(self.department)
        self.assertEqual([row["is_active"] for row in rows], [False])
