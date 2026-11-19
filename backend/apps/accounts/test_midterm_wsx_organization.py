"""王世鑫（WSX24）中期测试：组织关系核心函数。

Issue #42 分工：账号认证、部门团队与员工管理、个人资料与密码、头像、组织关系。
本文件覆盖 resolve_placement()。

需求依据：
- docs/backend/account-provisioning.md「业务用户（仅管理员）」
- docs/backend/account-provisioning.md「Issue2：员工账号后续维护契约」

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度             | 有效等价类                                   | 无效等价类                                   |
| 角色→组织形状     | 创始人无组织；经理有部门无团队；团队长/员工有部门+团队 | 创始人带部门/团队；经理带团队；团队长/员工缺团队 |
| 组织状态          | 部门、团队均启用                              | 部门停用；团队停用                            |
| 团队归属          | 团队属于所选部门                              | 团队属于其他部门                              |
| 负责人名额        | 该岗位 0 个启用负责人                          | 该岗位已有 1 个启用负责人                      |
| 启用状态          | is_active=True 需有效上级                     | is_active=False 不需要上级                    |

边界值分析
| 边界                 | 下界内 | 边界 | 上界外              |
| 启用创始人数量        | 0     | 1    | 2（第 2 个应拒绝）    |
| 每部门启用经理        | 0     | 1    | 2（同部门第 2 个应拒绝）|
| 每团队启用团队长      | 0     | 1    | 2（同团队第 2 个应拒绝）|
| 停用负责人是否占名额   | —     | 停用不占 | 启用占用          |

用例编号按 Issue #42 的“功能编号”分段分配，完整对照表见
`docs/test/wsx-midterm-tests.md` 第 2 节。本文件属第 11 项（员工身份、组织与启用
状态调整），区间 TC-WSX-171～220；resolve_placement() 占 171～192。
"""

from django.test import TestCase

from .api_serializers import resolve_placement
from .models import Department, Team, User


def detail_text(error):
    """把 DRF ValidationError.detail 拉平为可断言的字符串。

    detail 可能是 str、list[ErrorDetail] 或 dict[str, list[ErrorDetail]]。
    """
    detail = error.detail
    if isinstance(detail, dict):
        return " ".join(str(item) for value in detail.values() for item in (value if isinstance(value, (list, tuple)) else [value]))
    if isinstance(detail, (list, tuple)):
        return " ".join(str(item) for item in detail)
    return str(detail)


class ResolvePlacementTests(TestCase):
    """resolve_placement() 单元测试。

    直接调用函数验证业务规则，不经过 HTTP 层；接口层行为由 tests.py 的集成测试覆盖。
    """

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.founder = self.user("founder", User.Role.FOUNDER)
        self.manager = self.user("manager", User.Role.DEPARTMENT_MANAGER, self.department, manager=self.founder)
        self.leader = self.user("leader", User.Role.TEAM_LEADER, self.department, self.team, self.manager)

    def user(self, name, role, department=None, team=None, manager=None, is_active=True):
        return User.objects.create_user(
            username=name,
            password=self.password,
            display_name=name,
            account_type=User.AccountType.BUSINESS,
            business_role=role,
            department=department,
            team=team,
            manager=manager,
            is_active=is_active,
        )

    def assertRejected(self, role, department, team, is_active=True, **kwargs):
        with self.assertRaises(Exception) as ctx:
            resolve_placement(role, department, team, is_active, **kwargs)
        return ctx.exception

    # ---------- 正常输入 ----------

    def test_founder_without_organization_returns_empty_placement(self):
        """TC-WSX-171 正常：创始人无组织，返回 (None, None, None)。"""
        # 先把占地用的创始人停用，避免唯一性冲突干扰本用例
        User.objects.filter(pk=self.founder.pk).update(is_active=False)
        manager, department, team = resolve_placement(User.Role.FOUNDER, None, None, is_active=True)
        self.assertIsNone(manager)
        self.assertIsNone(department)
        self.assertIsNone(team)

    def test_department_manager_binds_active_founder_as_manager(self):
        """TC-WSX-172 正常：部门经理自动关联启用创始人为直属上级。"""
        other = Department.objects.create(name="财务部")
        manager, department, team = resolve_placement(
            User.Role.DEPARTMENT_MANAGER, other, None, is_active=True,
        )
        self.assertEqual(manager, self.founder)
        self.assertEqual(department, other)
        self.assertIsNone(team)

    def test_team_leader_binds_department_manager_as_manager(self):
        """TC-WSX-173 正常：团队长自动关联本部门启用经理为直属上级。"""
        other_team = Team.objects.create(name="前端组", department=self.department)
        manager, department, team = resolve_placement(
            User.Role.TEAM_LEADER, self.department, other_team, is_active=True,
        )
        self.assertEqual(manager, self.manager)
        self.assertEqual(department, self.department)
        self.assertEqual(team, other_team)

    def test_employee_binds_team_leader_as_manager(self):
        """TC-WSX-174 正常：员工自动关联本团队启用团队长为直属上级。"""
        manager, department, team = resolve_placement(
            User.Role.EMPLOYEE, self.department, self.team, is_active=True,
        )
        self.assertEqual(manager, self.leader)
        self.assertEqual(department, self.department)
        self.assertEqual(team, self.team)

    # ---------- 边界值 ----------

    def test_boundary_second_active_founder_rejected(self):
        """TC-WSX-175 边界：已有 1 个启用创始人（上界），第 2 个应拒绝。"""
        error = self.assertRejected(User.Role.FOUNDER, None, None, is_active=True)
        self.assertIn("已存在创始人", detail_text(error))

    def test_boundary_inactive_founder_does_not_occupy_slot(self):
        """TC-WSX-176 边界：停用账号不占负责人名额，可再建启用创始人。"""
        User.objects.filter(pk=self.founder.pk).update(is_active=False)
        manager, department, team = resolve_placement(User.Role.FOUNDER, None, None, is_active=True)
        self.assertEqual((manager, department, team), (None, None, None))

    def test_boundary_exclude_pk_allows_self_update(self):
        """TC-WSX-177 边界：exclude_pk 排除自身，更新自己不被唯一性拦截。"""
        manager, department, team = resolve_placement(
            User.Role.FOUNDER, None, None, is_active=True, exclude_pk=self.founder.pk,
        )
        self.assertEqual((manager, department, team), (None, None, None))

    def test_boundary_inactive_account_skips_manager_requirement(self):
        """TC-WSX-178 边界：is_active=False 时不需要有效上级，返回 (None, 部门, 团队)。"""
        other_team = Team.objects.create(name="前端组", department=self.department)
        manager, department, team = resolve_placement(
            User.Role.EMPLOYEE, self.department, other_team, is_active=False,
        )
        self.assertIsNone(manager)
        self.assertEqual(department, self.department)
        self.assertEqual(team, other_team)

    def test_boundary_allow_missing_manager_returns_null_manager(self):
        """TC-WSX-179 边界：allow_missing_manager=True 且上级空缺时返回 None 而非报错。"""
        other_team = Team.objects.create(name="前端组", department=self.department)
        manager, department, team = resolve_placement(
            User.Role.EMPLOYEE, self.department, other_team, is_active=True, allow_missing_manager=True,
        )
        self.assertIsNone(manager)
        self.assertEqual(team, other_team)

    def test_boundary_second_manager_in_same_department_rejected(self):
        """TC-WSX-180 边界：同部门已有 1 名启用经理（上界），第 2 名应拒绝。"""
        error = self.assertRejected(
            User.Role.DEPARTMENT_MANAGER, self.department, None, is_active=True,
        )
        self.assertIn("该部门已存在经理", detail_text(error))

    def test_boundary_different_departments_each_allow_one_manager(self):
        """TC-WSX-181 边界：名额作用域是部门，不同部门可各有一名经理。"""
        other = Department.objects.create(name="财务部")
        manager, department, _ = resolve_placement(
            User.Role.DEPARTMENT_MANAGER, other, None, is_active=True,
        )
        self.assertEqual(manager, self.founder)

    def test_boundary_second_leader_in_same_team_rejected(self):
        """TC-WSX-182 边界：同团队已有 1 名启用团队长（上界），第 2 名应拒绝。"""
        error = self.assertRejected(
            User.Role.TEAM_LEADER, self.department, self.team, is_active=True,
        )
        self.assertIn("该团队已存在团队长", detail_text(error))

    # ---------- 异常输入 ----------

    def test_exception_founder_with_department_rejected(self):
        """TC-WSX-183 异常：创始人不能指定部门。"""
        error = self.assertRejected(User.Role.FOUNDER, self.department, None)
        self.assertIn("创始人不能指定部门或团队", detail_text(error))

    def test_exception_founder_with_team_rejected(self):
        """TC-WSX-184 异常：创始人不能指定团队。"""
        error = self.assertRejected(User.Role.FOUNDER, self.department, self.team)
        self.assertIn("创始人不能指定部门或团队", detail_text(error))

    def test_exception_department_manager_without_department(self):
        """TC-WSX-185 异常：非创始人必须选择部门，缺部门应拒绝。"""
        error = self.assertRejected(User.Role.DEPARTMENT_MANAGER, None, None)
        self.assertIn("该身份必须选择部门", detail_text(error))

    def test_exception_inactive_department_rejected(self):
        """TC-WSX-186 异常：停用部门不能作为归属。"""
        inactive = Department.objects.create(name="撤销部", is_active=False)
        error = self.assertRejected(User.Role.DEPARTMENT_MANAGER, inactive, None)
        self.assertIn("该部门已停用", detail_text(error))

    def test_exception_department_manager_with_team_rejected(self):
        """TC-WSX-187 异常：部门经理不能指定团队。"""
        other = Department.objects.create(name="财务部")
        error = self.assertRejected(User.Role.DEPARTMENT_MANAGER, other, self.team)
        self.assertIn("部门经理不能指定团队", detail_text(error))

    def test_exception_team_leader_without_team(self):
        """TC-WSX-188 异常：团队长必须选择团队，缺团队应拒绝。"""
        error = self.assertRejected(User.Role.TEAM_LEADER, self.department, None)
        self.assertIn("请选择所选部门下的团队", detail_text(error))

    def test_exception_team_not_belonging_to_department(self):
        """TC-WSX-189 异常：团队必须属于所选部门，跨部门组合应拒绝。"""
        other = Department.objects.create(name="财务部")
        error = self.assertRejected(User.Role.EMPLOYEE, other, self.team)
        self.assertIn("请选择所选部门下的团队", detail_text(error))

    def test_exception_inactive_team_rejected(self):
        """TC-WSX-190 异常：停用团队不能作为归属。"""
        inactive_team = Team.objects.create(name="撤销组", department=self.department, is_active=False)
        error = self.assertRejected(User.Role.EMPLOYEE, self.department, inactive_team)
        self.assertIn("该团队已停用", detail_text(error))

    def test_exception_manager_without_active_founder(self):
        """TC-WSX-191 异常：无启用创始人时录入经理，应提示先录入直属上级。"""
        User.objects.filter(pk=self.founder.pk).update(is_active=False)
        other = Department.objects.create(name="财务部")
        error = self.assertRejected(User.Role.DEPARTMENT_MANAGER, other, None, is_active=True)
        self.assertIn("请先录入启用的直属上级", detail_text(error))

    def test_exception_employee_without_active_team_leader(self):
        """TC-WSX-192 异常：团队无启用团队长时录入员工，应提示先录入直属上级。"""
        other_team = Team.objects.create(name="前端组", department=self.department)
        error = self.assertRejected(
            User.Role.EMPLOYEE, self.department, other_team, is_active=True,
        )
        self.assertIn("请先录入启用的直属上级", detail_text(error))
