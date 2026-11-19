"""王世鑫（WSX24）中期测试：公司组织树、成员资料与下属范围。

Issue #42 分工：
- 第 21 项：公司组织树和人员搜索 —— _build_organization_tree()、OrganizationTreeView.get()（TC-WSX-351～363）
- 第 22 项：成员资料和头像读取 —— OrganizationMemberProfileView.get()、OrganizationMemberAvatarView.get()（TC-WSX-371～377）
- 第 23 项：下属范围 —— descendants()（TC-WSX-381～385）
- 第 24 项：有效下属范围 —— subordinate_ids()（TC-WSX-391～393）
- 第 25 项：下属任务查询 —— SubordinateTaskListView.get()（TC-WSX-401～405）

需求依据：docs/requirements/08-organization-relationships.md
> 所有 App 用户可浏览公司组织关系；成员资料展示头像、姓名、手机号、职位、部门和团队，
> 不展示身份证号；不得提供编辑、启停、改密或代改资料操作。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度          | 有效等价类                       | 无效等价类                        |
| 树结构范围     | company（全公司）/ department / team | 关键词无匹配（返回空 roots）      |
| 关键词搜索     | 姓名、手机号、职级中文标签、枚举值     | 空关键词（返回完整树）            |
| 成员资料可见性  | 启用业务成员                     | 不存在；管理员账号；停用成员        |
| 下属范围       | 直接与间接下属                   | 自身；停用下属；链断裂的下属        |
| 下属任务       | assigned 或 created 范围         | 非法 task_scope；越权 user_id      |

边界值分析
| 边界            | 边界前           | 边界值          | 边界后                  |
| 下属层级         | 无下属（空集）    | 1 级下属        | 4 级链（创始人→员工）     |
| 关键词匹配       | 无匹配（空 roots） | 1 个匹配        | 多个匹配                |
| 任务范围参数     | assigned 合法     | —              | created 合法；其他拒绝    |
"""

from datetime import timedelta

from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from apps.accounts.models import User
from apps.accounts.authentication import has_complete_supervisor_chain
from apps.logs.views import descendants
from apps.tasks.models import Task
from apps.tasks.services import subordinate_ids
from apps.tasks.views import _build_organization_tree


class OrgTreeMixin:
    def build_org(self):
        from apps.accounts.models import Department, Team

        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.founder = self.user("创始人", "founder", User.Role.FOUNDER)
        self.manager = self.user("部门经理", "manager", User.Role.DEPARTMENT_MANAGER,
                                 self.department, manager=self.founder)
        self.leader = self.user("团队长", "leader", User.Role.TEAM_LEADER,
                                self.department, self.team, self.manager)
        self.employee = self.user("张三", "13800000001", User.Role.EMPLOYEE,
                                  self.department, self.team, self.leader)

    def user(self, display_name, username, role, department=None, team=None, manager=None):
        return User.objects.create_user(
            username=username, password="Pandora-test-938!", display_name=display_name,
            account_type=User.AccountType.BUSINESS, business_role=role,
            department=department, team=team, manager=manager,
        )

    def people(self):
        return list(User.objects.filter(
            account_type=User.AccountType.BUSINESS, is_active=True,
        ).select_related("department", "team").order_by("id"))


class OrganizationTreeBuildTests(OrgTreeMixin, TestCase):
    """_build_organization_tree() 单元测试（第 21 项）。"""

    def setUp(self):
        self.build_org()

    def tree(self, user=None, scope="company", keyword=""):
        return _build_organization_tree(user or self.employee, scope, self.people(), keyword)

    # ---------- 正常输入 ----------

    def test_company_scope_roots_start_with_founder(self):
        """TC-WSX-351 正常：全公司视图的根节点首先是创始人。"""
        tree = self.tree()
        self.assertEqual(tree["scope"], "company")
        self.assertEqual(tree["roots"][0]["display_name"], "创始人")

    def test_department_node_contains_manager_and_team(self):
        """TC-WSX-352 正常：部门节点下含经理与本部门团队。"""
        tree = self.tree()
        department_node = next(node for node in tree["roots"] if node["node_type"] == "department")
        self.assertEqual(department_node["name"], "研发部")
        self.assertEqual(department_node["children"][0]["display_name"], "部门经理")
        self.assertEqual(department_node["children"][1]["node_type"], "team")

    def test_team_node_contains_leader_and_employee(self):
        """TC-WSX-353 正常：团队节点下含团队长与员工。"""
        tree = self.tree()
        department_node = next(node for node in tree["roots"] if node["node_type"] == "department")
        team_node = next(node for node in department_node["children"] if node["node_type"] == "team")
        self.assertEqual([child["display_name"] for child in team_node["children"]], ["团队长", "张三"])

    def test_keyword_matches_display_name(self):
        """TC-WSX-354 正常：关键词按姓名匹配，返回平铺结果。"""
        tree = self.tree(keyword="张三")
        self.assertEqual([node["display_name"] for node in tree["roots"]], ["张三"])

    def test_keyword_matches_role_label(self):
        """TC-WSX-355 正常：关键词可匹配职级中文标签（如“团队长”）。"""
        tree = self.tree(keyword="团队长")
        self.assertEqual([node["display_name"] for node in tree["roots"]], ["团队长"])

    def test_keyword_matches_role_enum_value(self):
        """TC-WSX-356 正常：关键词可匹配职级枚举值（如 employee）。"""
        tree = self.tree(keyword="employee")
        self.assertEqual([node["display_name"] for node in tree["roots"]], ["张三"])

    # ---------- 边界值 ----------

    def test_boundary_keyword_without_match_returns_empty_roots(self):
        """TC-WSX-357 边界：关键词无匹配时 roots 为空列表。"""
        self.assertEqual(self.tree(keyword="不存在")["roots"], [])

    def test_boundary_keyword_is_case_insensitive(self):
        """TC-WSX-358 边界：关键词大小写不敏感。"""
        self.assertEqual(len(self.tree(keyword="EMPLOYEE")["roots"]), 1)

    def test_boundary_empty_keyword_returns_full_tree(self):
        """TC-WSX-359 边界：空关键词返回完整树而非平铺结果。"""
        tree = self.tree(keyword="")
        self.assertTrue(any(node["node_type"] == "department" for node in tree["roots"]))

    def test_boundary_department_without_members_has_empty_children(self):
        """TC-WSX-360 边界：无成员且无团队的部门 children 为空。"""
        from apps.accounts.models import Department

        Department.objects.create(name="空部门")
        tree = self.tree()
        empty = next(node for node in tree["roots"] if node.get("name") == "空部门")
        self.assertEqual(empty["children"], [])

    def test_scope_department_returns_single_department(self):
        """TC-WSX-361 正常：部门范围只返回本人部门节点。"""
        tree = self.tree(user=self.manager, scope="department")
        self.assertEqual([node["name"] for node in tree["roots"]], ["研发部"])

    def test_scope_team_returns_single_team(self):
        """TC-WSX-362 正常：团队范围只返回本人团队节点。"""
        tree = self.tree(user=self.leader, scope="team")
        self.assertEqual([node["name"] for node in tree["roots"]], ["后端组"])


class OrganizationTreeViewTests(OrgTreeMixin, TestCase):
    """OrganizationTreeView.get() 接口集成测试（第 21 项）。"""

    def setUp(self):
        self.build_org()
        self.client = APIClient()
        self.client.force_authenticate(user=self.employee)

    def test_all_roles_can_browse_full_company(self):
        """TC-WSX-363 正常：任一业务用户都能浏览全公司组织树（scope 固定为 company）。"""
        response = self.client.get("/api/v1/app/organization/tree/")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["data"]["scope"], "company")
        self.assertEqual(len(response.data["data"]["roots"]), 2)


class OrganizationMemberTests(OrgTreeMixin, TestCase):
    """成员资料与头像读取（第 22 项）。"""

    def setUp(self):
        self.build_org()
        self.client = APIClient()
        self.client.force_authenticate(user=self.employee)

    def test_profile_returns_phone_and_hides_id_number(self):
        """TC-WSX-371 正常：成员资料返回手机号，不返回身份证号。"""
        response = self.client.get(f"/api/v1/app/organization/members/{self.leader.pk}/")
        self.assertEqual(response.status_code, 200, response.data)
        data = response.data["data"]
        self.assertEqual(data["phone"], "leader")
        self.assertNotIn("id_number", data)

    def test_profile_flags_self(self):
        """TC-WSX-372 正常：查看本人时标记 is_self，并为他人标记 False。"""
        own = self.client.get(f"/api/v1/app/organization/members/{self.employee.pk}/")
        other = self.client.get(f"/api/v1/app/organization/members/{self.leader.pk}/")
        self.assertTrue(own.data["data"]["is_self"])
        self.assertFalse(other.data["data"]["is_self"])

    def test_profile_permission_flags_follow_subordinate_scope(self):
        """TC-WSX-373 正常：可查看任务/日志的标记依据下属关系计算。"""
        response = self.client.get(f"/api/v1/app/organization/members/{self.leader.pk}/")
        data = response.data["data"]
        self.assertFalse(data["can_view_tasks"])
        self.assertFalse(data["can_view_logs"])
        self.assertFalse(data["can_view_work_views"])

    def test_missing_member_returns_404(self):
        """TC-WSX-374 边界：成员不存在时返回 404。"""
        self.assertEqual(
            self.client.get("/api/v1/app/organization/members/999999/").status_code, 404,
        )

    def test_admin_account_not_queryable_as_member(self):
        """TC-WSX-375 边界：管理员账号不能作为组织成员被查询。"""
        admin = User.objects.create_superuser(username="admin", password="Pandora-test-938!")
        self.assertEqual(
            self.client.get(f"/api/v1/app/organization/members/{admin.pk}/").status_code, 404,
        )

    def test_inactive_member_returns_404(self):
        """TC-WSX-376 边界：停用成员不可查询。"""
        User.objects.filter(pk=self.employee.pk).update(is_active=False)
        self.assertEqual(
            self.client.get(f"/api/v1/app/organization/members/{self.employee.pk}/").status_code, 404,
        )

    def test_member_avatar_returns_404_when_absent(self):
        """TC-WSX-377 边界：成员未设置头像时返回 404。"""
        self.assertEqual(
            self.client.get(f"/api/v1/app/organization/members/{self.leader.pk}/avatar/").status_code, 404,
        )


class DescendantsTests(OrgTreeMixin, TestCase):
    """descendants() 单元测试（第 23 项）。"""

    def setUp(self):
        self.build_org()

    def test_returns_direct_and_indirect_reports(self):
        """TC-WSX-381 正常：返回全部直接与间接下属。"""
        self.assertEqual({u.pk for u in descendants(self.manager)}, {self.leader.pk, self.employee.pk})

    def test_boundary_no_subordinates_returns_empty(self):
        """TC-WSX-382 边界：无下属时返回空列表。"""
        self.assertEqual(descendants(self.employee), [])

    def test_boundary_inactive_subordinate_excluded(self):
        """TC-WSX-383 边界：停用中间层负责人时，其整条下级链都不再返回。

        实测行为：descendants() 按 manager_id 逐层向下遍历，并以 is_active=True 过滤，
        因此停用团队长后，遍历在该层终止，其下属（即使仍启用）也不再出现在结果中。
        该行为与 has_complete_supervisor_chain() 的链完整性判定一致。
        """
        User.objects.filter(pk=self.leader.pk).update(is_active=False)
        self.assertEqual([u.pk for u in descendants(self.manager)], [])

    def test_result_excludes_self(self):
        """TC-WSX-384 异常：结果不包含用户自身，避免自环。"""
        self.assertNotIn(self.manager.pk, {u.pk for u in descendants(self.manager)})

    def test_full_chain_length(self):
        """TC-WSX-385 正常：创始人到员工共 3 级下属全部返回。"""
        self.assertEqual(len(descendants(self.founder)), 3)


class SubordinateIdsTests(OrgTreeMixin, TestCase):
    """subordinate_ids() 单元测试（第 24 项）。"""

    def setUp(self):
        self.build_org()

    def test_returns_ids_of_valid_subordinates(self):
        """TC-WSX-391 正常：返回组织链完整的下属 ID 集合。"""
        self.assertEqual(subordinate_ids(self.manager), {self.leader.pk, self.employee.pk})

    def test_boundary_broken_chain_subordinate_excluded(self):
        """TC-WSX-392 边界：链断裂的下属被排除（不可查看任务）。"""
        User.objects.filter(pk=self.manager.pk).update(is_active=False)
        self.manager.refresh_from_db()
        self.assertEqual(subordinate_ids(self.manager), set())

    def test_boundary_no_subordinates_returns_empty_set(self):
        """TC-WSX-393 边界：无下属时返回空集合。"""
        self.assertEqual(subordinate_ids(self.employee), set())


class SubordinateTaskListTests(OrgTreeMixin, TestCase):
    """SubordinateTaskListView.get() 接口集成测试（第 25 项）。"""

    def setUp(self):
        self.build_org()
        self.client = APIClient()
        self.client.force_authenticate(user=self.manager)
        self.task = Task.objects.create(
            creator=self.leader, assignee=self.employee, title="联调接口",
            description="说明", due_at=timezone.now() + timedelta(days=3),
            approval_status=Task.Approval.APPROVED,
        )

    def test_returns_subordinate_assigned_tasks(self):
        """TC-WSX-401 正常：返回下属承担的任务。"""
        response = self.client.get("/api/v1/app/tasks/subordinates/")
        self.assertEqual(response.status_code, 200, response.data)
        titles = [row["title"] for row in response.data["data"]["results"]]
        self.assertIn("联调接口", titles)

    def test_created_scope_returns_tasks_created_by_subordinates(self):
        """TC-WSX-402 正常：created 范围返回下属创建的任务。"""
        response = self.client.get("/api/v1/app/tasks/subordinates/", {"task_scope": "created"})
        self.assertEqual(response.status_code, 200, response.data)
        titles = [row["title"] for row in response.data["data"]["results"]]
        self.assertIn("联调接口", titles)

    def test_invalid_scope_returns_400(self):
        """TC-WSX-403 异常：task_scope 非 assigned/created 时返回 400。"""
        response = self.client.get("/api/v1/app/tasks/subordinates/", {"task_scope": "all"})
        self.assertEqual(response.status_code, 400)
        self.assertIn("task_scope", response.data["data"])

    def test_cross_scope_user_id_returns_403(self):
        """TC-WSX-404 异常：指定非下属人员时返回 403（越权保护）。"""
        response = self.client.get("/api/v1/app/tasks/subordinates/", {"user_id": self.founder.pk})
        self.assertEqual(response.status_code, 403)

    def test_non_numeric_user_id_returns_400(self):
        """TC-WSX-405 边界：user_id 非数字时返回 400。"""
        response = self.client.get("/api/v1/app/tasks/subordinates/", {"user_id": "abc"})
        self.assertEqual(response.status_code, 400)
        self.assertIn("user_id", response.data["data"])
