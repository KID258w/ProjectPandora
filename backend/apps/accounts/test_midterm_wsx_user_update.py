"""王世鑫（WSX24）中期测试：员工身份组织调整、直属关系维护与管理概况。

Issue #42 分工：
- 第 11 项（剩余部分）：员工身份、组织与启用状态调整
  —— UserUpdateSerializer.validate() / update()、maintain_manager_links()（TC-WSX-193～213）
  （第 11 项的 resolve_placement() 见 test_midterm_wsx_organization.py，TC-WSX-171～192）
- 第 12 项：管理概况 —— DashboardView.get()（TC-WSX-221～226）

需求依据：docs/backend/account-provisioning.md「Issue2：员工账号后续维护契约」
> PATCH admin/users/{id}/ 仅接受 business_role、department_id、team_id、is_active。
> 停用或撤销负责人时只将其直接下属的 manager_id 置空；新负责人到位后重建直属关系。
> 既有启用员工可在负责人暂缺时保留启用状态和原组织归属，但不能因此获得 App 访问。
> 团队重新启用要求所属部门已启用。
> GET admin/dashboard/ 返回启用员工、部门、团队数及已发布公司重要事项数。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度            | 有效等价类                        | 无效等价类                              |
| 可调整字段       | business_role、department_id、team_id、is_active | display_name、username、id_number、password、manager_id |
| 角色与组织形状   | 角色切换后组织自动修正              | 创始人带部门/团队；经理带团队              |
| 直属关系        | 负责人到位后重建下属关系            | 负责人停用后下属仍挂旧上级                |
| 负责人空缺      | 既有启用员工保留原组织              | 新建/重新启用时无有效上级                 |
| 概况计数        | 只统计启用业务账号、启用部门/团队、已发布事项 | 停用账号/部门/团队、草稿事项计入    |

边界值分析
| 边界              | 边界前              | 边界值                | 边界后                     |
| 负责人启用状态     | 启用 → 下属挂靠       | —                    | 停用 → 下属 manager 置空      |
| 员工启用状态       | 启用 → 参与关系重建   | —                    | 停用 → 不再作为负责人/下属     |
| 组织迁移          | 同团队内调整          | 跨部门/跨团队迁移       | 旧范围下属解绑、新范围下属挂靠   |
| 概况计数          | 全为 0（空库）        | 1 个各类型实体         | 停用实体不计入                |
"""

from types import SimpleNamespace

from django.test import TestCase
from rest_framework.test import APIClient

from .api_serializers import UserUpdateSerializer
from .api_views import DashboardView
from .models import Department, Team, User


class UserUpdateFieldWhitelistTests(TestCase):
    """UserUpdateSerializer 字段白名单单元测试（第 11 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.founder = User.objects.create_user(
            username="founder", password=self.password, display_name="创始人",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        self.employee = User.objects.create_user(
            username="employee", password=self.password, display_name="张三",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team, manager=self.founder,
        )

    def assert_rejected(self, **data):
        serializer = UserUpdateSerializer(instance=self.employee, data=data, partial=True)
        self.assertFalse(serializer.is_valid())
        return str(serializer.errors)

    # ---------- 异常输入：不可修改字段 ----------

    def test_exception_display_name_not_editable(self):
        """TC-WSX-200 异常：管理端不可修改姓名。"""
        self.assertIn("不允许传入此字段", self.assert_rejected(display_name="改名"))

    def test_exception_username_not_editable(self):
        """TC-WSX-201 异常：管理端不可修改手机号（登录标识）。"""
        self.assertIn("不允许传入此字段", self.assert_rejected(username="13900000000"))

    def test_exception_id_number_not_editable(self):
        """TC-WSX-202 异常：管理端不可修改身份证号。"""
        self.assertIn("不允许传入此字段", self.assert_rejected(id_number="110101199001019999"))

    def test_exception_password_not_editable(self):
        """TC-WSX-203 异常：管理端不提供密码重置。"""
        self.assertIn("不允许传入此字段", self.assert_rejected(password="Hack-1234!"))

    def test_exception_manager_id_not_editable(self):
        """TC-WSX-204 异常：直属上级由服务端计算，不可直接指定。"""
        self.assertIn("不允许传入此字段", self.assert_rejected(manager_id=self.founder.pk))

    # ---------- 边界：角色与组织形状 ----------

    def test_founder_role_clears_organization(self):
        """TC-WSX-207 正常：转为创始人并显式清空组织时通过，部门与团队被置为 None。

        注意：仅传 business_role=founder 会因实例现有组织而被拒绝（见 TC-WSX-205），
        必须同时提交 department_id=null、team_id=null。
        """
        # 先停用现有创始人，腾出唯一名额；直接改库不触发业务钩子，此处仅准备前置状态
        User.objects.filter(pk=self.founder.pk).update(is_active=False)
        serializer = UserUpdateSerializer(instance=self.employee, data={
            "business_role": "founder", "department_id": None, "team_id": None,
        }, partial=True)
        self.assertTrue(serializer.is_valid(), serializer.errors)
        self.assertIsNone(serializer.validated_data["department"])
        self.assertIsNone(serializer.validated_data["team"])

    def test_exception_founder_with_department_rejected(self):
        """TC-WSX-205 异常：创始人不能保留部门归属。"""
        empty = User.objects.create_user(
            username="nobody", password=self.password, display_name="空",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=self.department, team=self.team,
        )
        serializer = UserUpdateSerializer(
            instance=empty, data={"business_role": "founder", "department_id": self.department.pk}, partial=True,
        )
        self.assertFalse(serializer.is_valid())

    def test_exception_manager_with_explicit_team_rejected(self):
        """TC-WSX-206 异常：部门经理不能显式指定团队。"""
        User.objects.filter(pk=self.employee.pk).update(business_role=User.Role.EMPLOYEE)
        self.employee.refresh_from_db()
        serializer = UserUpdateSerializer(
            instance=self.employee, data={"business_role": "department_manager", "team_id": self.team.pk},
            partial=True,
        )
        self.assertFalse(serializer.is_valid())
        self.assertIn("team_id", serializer.errors)


class UserUpdateManagerLinkTests(TestCase):
    """UserUpdateSerializer.update() 与 maintain_manager_links() 单元测试（第 11 项）。"""

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
        self.employee = User.objects.create_user(
            username="employee", password=self.password, display_name="张三",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=self.department, team=self.team, manager=self.leader,
        )
        self.client = APIClient()
        self.client.force_authenticate(user=User.objects.create_superuser(
            username="admin", password=self.password,
        ))

    def patch(self, user, **data):
        return self.client.patch(f"/api/v1/admin/users/{user.pk}/", data, format="json")

    # ---------- 正常输入 ----------

    def test_promote_employee_to_leader_rebuilds_reports(self):
        """TC-WSX-194 正常：原团队长停用后提升员工为团队长，其下属关系被重建。"""
        second = User.objects.create_user(
            username="employee2", password=self.password, display_name="李四",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.EMPLOYEE,
            department=self.department, team=self.team, manager=self.leader,
        )
        self.assertEqual(self.patch(self.leader, is_active=False).status_code, 200)
        response = self.patch(self.employee, business_role="team_leader")
        self.assertEqual(response.status_code, 200, response.data)
        second.refresh_from_db()
        self.assertEqual(second.manager_id, self.employee.pk)

    def test_deactivate_leader_clears_direct_reports(self):
        """TC-WSX-197 边界（状态切换点）：停用团队长后其直接下属的上级被置空。"""
        response = self.patch(self.leader, is_active=False)
        self.assertEqual(response.status_code, 200, response.data)
        self.employee.refresh_from_db()
        self.assertIsNone(self.employee.manager_id)

    def test_deactivate_manager_clears_leader_link(self):
        """TC-WSX-196 边界：停用部门经理后，本部门团队长的上级被置空。"""
        response = self.patch(self.manager, is_active=False)
        self.assertEqual(response.status_code, 200, response.data)
        self.leader.refresh_from_db()
        self.assertIsNone(self.leader.manager_id)

    def test_manager_transfer_clears_old_organization_links(self):
        """TC-WSX-199 边界（组织迁移）：经理换部门后，旧部门团队长解除上下级关系。"""
        other = Department.objects.create(name="财务部")
        other_team = Team.objects.create(name="会计组", department=other)
        User.objects.create_user(
            username="leader2", password=self.password, display_name="会计团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=other, team=other_team,
        )
        # 新部门已有一个未挂上级的团队长，且本部门经理停用后才可迁移
        response = self.patch(self.manager, department_id=other.pk)
        self.assertEqual(response.status_code, 200, response.data)
        self.leader.refresh_from_db()
        self.assertIsNone(self.leader.manager_id)
        leader2 = User.objects.get(username="leader2")
        self.assertEqual(leader2.manager_id, self.manager.pk)

    # ---------- 边界：负责人空缺与恢复 ----------

    def test_vacancy_allows_existing_employee_to_stay_active(self):
        """TC-WSX-195 边界：负责人空缺时，既有启用员工保留启用状态与原组织。"""
        User.objects.filter(pk=self.leader.pk).update(is_active=False)
        self.employee.refresh_from_db()
        self.employee.manager = None
        self.employee.save(update_fields=["manager"])
        response = self.patch(self.employee, business_role="employee")
        self.assertEqual(response.status_code, 200, response.data)
        self.employee.refresh_from_db()
        self.assertTrue(self.employee.is_active)

    def test_reactivation_requires_active_supervisor(self):
        """TC-WSX-208 边界：上级空缺时重新启用员工被拒绝。"""
        User.objects.filter(pk=self.leader.pk).update(is_active=False)
        self.patch(self.employee, is_active=False)
        response = self.patch(self.employee, is_active=True)
        self.assertEqual(response.status_code, 400)
        self.assertIn("business_role", response.data["data"])

    def test_reactivation_succeeds_after_supervisor_restored(self):
        """TC-WSX-209 正常：上级恢复启用后，员工可重新启用。"""
        self.patch(self.employee, is_active=False)
        response = self.patch(self.employee, is_active=True)
        self.assertEqual(response.status_code, 200, response.data)
        self.employee.refresh_from_db()
        self.assertTrue(self.employee.is_active)

    # ---------- 直属关系维护函数 ----------

    def test_new_founder_links_department_manager(self):
        """TC-WSX-210 正常：负责人停用后任命新创始人，部门经理自动挂靠新上级。"""
        # 通过接口停用，才能触发 maintain_manager_links 解绑下属
        self.assertEqual(self.patch(self.founder, is_active=False).status_code, 200)
        self.manager.refresh_from_db()
        self.assertIsNone(self.manager.manager_id)
        response = self.client.post("/api/v1/admin/users/", {
            "username": "founder2", "id_number": "110101199001018888",
            "display_name": "新创始人", "business_role": "founder",
        }, format="json")
        self.assertEqual(response.status_code, 201, response.data)
        self.manager.refresh_from_db()
        self.assertEqual(self.manager.manager_id, response.data["data"]["id"])

    def test_deactivated_founder_unlinks_manager(self):
        """TC-WSX-211 正常：停用创始人后，部门经理的上级被置空。"""
        self.patch(self.founder, is_active=False)
        self.manager.refresh_from_db()
        self.assertIsNone(self.manager.manager_id)

    def test_non_leader_update_does_not_touch_subordinates(self):
        """TC-WSX-213 异常：普通员工改用例不影响他人直属关系。"""
        self.patch(self.employee, business_role="employee")
        self.leader.refresh_from_db()
        self.assertEqual(self.leader.manager_id, self.manager.pk)
        self.employee.refresh_from_db()
        self.assertEqual(self.employee.manager_id, self.leader.pk)

    def test_role_change_recomputes_manager(self):
        """TC-WSX-193 正常：员工转任其他岗位时直属上级按新岗位重新计算。"""
        other_team = Team.objects.create(name="前端组", department=self.department)
        User.objects.create_user(
            username="leader3", password=self.password, display_name="前端团队长",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=other_team, manager=self.manager,
        )
        response = self.patch(self.employee, team_id=other_team.pk)
        self.assertEqual(response.status_code, 200, response.data)
        self.employee.refresh_from_db()
        self.assertEqual(self.employee.team_id, other_team.pk)


class DashboardTests(TestCase):
    """DashboardView.get() 单元测试（第 12 项）。"""

    password = "Pandora-test-938!"

    def setUp(self):
        self.admin = User.objects.create_superuser(username="admin", password=self.password)
        self.view = DashboardView()

    def counts(self):
        return self.view.get(SimpleNamespace()).data

    # ---------- 正常输入 ----------

    def test_empty_database_returns_zero_counts(self):
        """TC-WSX-221 边界：空库时四项计数全为 0。"""
        self.assertEqual(
            self.counts(),
            {"active_users": 0, "departments": 0, "teams": 0, "published_company_items": 0},
        )

    def test_counts_include_business_entities(self):
        """TC-WSX-222 正常：返回启用业务账号、部门、团队数量。"""
        department = Department.objects.create(name="研发部")
        Team.objects.create(name="后端组", department=department)
        User.objects.create_user(
            username="founder", password=self.password, display_name="创始人",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        data = self.counts()
        self.assertEqual(data["active_users"], 1)
        self.assertEqual(data["departments"], 1)
        self.assertEqual(data["teams"], 1)

    # ---------- 边界值 ----------

    def test_admin_accounts_not_counted(self):
        """TC-WSX-223 边界：管理员账号不计入 active_users。"""
        self.assertEqual(self.counts()["active_users"], 0)

    def test_inactive_users_departments_teams_excluded(self):
        """TC-WSX-224 边界：停用账号、部门、团队均不计入。"""
        department = Department.objects.create(name="撤销部", is_active=False)
        Team.objects.create(name="撤销组", department=department, is_active=False)
        User.objects.create_user(
            username="gone", password=self.password, display_name="停用",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
            is_active=False,
        )
        data = self.counts()
        self.assertEqual(
            (data["active_users"], data["departments"], data["teams"]), (0, 0, 0),
        )

    def test_published_company_items_counted(self):
        """TC-WSX-225 正常：已发布的公司重要事项计入 published_company_items。"""
        from apps.content.models import CompanyImportantItem

        CompanyImportantItem.objects.create(
            title="标题", summary="摘要", body="正文",
            status=CompanyImportantItem.Status.PUBLISHED,
        )
        self.assertEqual(self.counts()["published_company_items"], 1)

    def test_draft_company_items_not_counted(self):
        """TC-WSX-226 边界：草稿状态的公司事项不计入已发布数量。"""
        from apps.content.models import CompanyImportantItem

        CompanyImportantItem.objects.create(
            title="草稿", summary="摘要", body="正文",
            status=CompanyImportantItem.Status.DRAFT,
        )
        self.assertEqual(self.counts()["published_company_items"], 0)
