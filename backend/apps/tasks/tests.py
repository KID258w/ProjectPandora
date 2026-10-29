from datetime import timedelta

from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import AccessToken

from apps.accounts.models import Department, Team, User
from apps.tags.models import Tag

from .models import Task, TaskApprovalRecord, TaskChangeHistory, TaskNotification, TaskSubmission


@override_settings(PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"])
class TaskApiTests(APITestCase):
    def setUp(self):
        self.dep = Department.objects.create(name="研发")
        self.team = Team.objects.create(name="后端", department=self.dep)
        self.other_dep = Department.objects.create(name="产品")
        self.other_team = Team.objects.create(name="产品组", department=self.other_dep)
        self.founder = self.user("founder", User.Role.FOUNDER)
        self.manager = self.user("manager", User.Role.DEPARTMENT_MANAGER, self.dep, manager=self.founder)
        self.leader = self.user("leader", User.Role.TEAM_LEADER, self.dep, self.team, self.manager)
        self.employee = self.user("employee", User.Role.EMPLOYEE, self.dep, self.team, self.leader)
        self.other_manager = self.user("other-manager", User.Role.DEPARTMENT_MANAGER, self.other_dep, manager=self.founder)
        self.other_leader = self.user("other-leader", User.Role.TEAM_LEADER, self.other_dep, self.other_team, self.other_manager)
        self.other_employee = self.user("other-employee", User.Role.EMPLOYEE, self.other_dep, self.other_team, self.other_leader)
        self.tag = Tag.objects.create(name="紧急", color="#4C9A8A")
        self.due = (timezone.now() + timedelta(days=5)).isoformat()

    def user(self, username, role, department=None, team=None, manager=None):
        return User.objects.create_user(username=username, password="password", display_name=username,
            account_type=User.AccountType.BUSINESS, business_role=role,
            department=department, team=team, manager=manager)

    def auth(self, user):
        token = AccessToken.for_user(user)
        token["account_type"] = user.account_type
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def create(self, creator, assignee, **extra):
        self.auth(creator)
        return self.client.post("/api/v1/app/tasks/", {
            "assignee_id": assignee.pk, "title": "实现接口", "description": "完成需求功能",
            "due_at": self.due, **extra,
        }, format="json")

    @staticmethod
    def path(task, suffix=""):
        return f"/api/v1/app/tasks/{task.pk}/{suffix}"

    def approve(self, task, approver):
        self.auth(approver)
        return self.client.post(self.path(task, "approval/"), {"decision": "approved"}, format="json")

    def test_assignment_matrix_pending_visibility_and_notifications(self):
        for creator, allowed, denied in [
            (self.founder, self.other_employee, None),
            (self.manager, self.other_manager, self.other_employee),
            (self.leader, self.employee, self.other_employee),
            (self.employee, self.employee, self.leader),
        ]:
            self.auth(creator)
            candidates = self.client.get("/api/v1/app/organization/assignable-users/").data["data"]["results"]
            self.assertIn(allowed.pk, [u["id"] for u in candidates])
            if denied:
                self.assertNotIn(denied.pk, [u["id"] for u in candidates])
                self.assertEqual(self.create(creator, denied).status_code, 400)
            response = self.create(creator, allowed, tag_ids=[self.tag.pk])
            self.assertEqual(response.status_code, 201, response.data)
            task = Task.objects.get(pk=response.data["data"]["id"])
            self.assertEqual(task.approval_status, "approved" if creator == self.founder else "pending")
            self.assertEqual([u["id"] for u in response.data["data"]["tags"]], [self.tag.pk])
            self.assertEqual(TaskChangeHistory.objects.filter(task=task).count(), 1)
            self.auth(allowed)
            visible = self.client.get("/api/v1/app/tasks/?scope=mine").data["data"]["results"]
            self.assertEqual(any(row["id"] == task.pk for row in visible), creator == self.founder)
            if creator != self.founder and creator != allowed:
                self.assertEqual(self.client.get(self.path(task)).status_code, 404)
            if creator == self.founder and creator != allowed:
                self.assertEqual(TaskNotification.objects.filter(task=task, recipient=allowed,
                    notification_type="assignment_received").count(), 1)
            elif creator != self.founder:
                self.assertEqual(TaskNotification.objects.filter(task=task, recipient=creator.manager,
                    notification_type="approval_required").count(), 1)

    def test_approval_return_resubmit_reapproval_and_tag_only_edit(self):
        response = self.create(self.manager, self.leader)
        task = Task.objects.get(pk=response.data["data"]["id"])
        self.auth(self.founder)
        self.assertEqual(self.client.get("/api/v1/app/task-approvals/inbox/").data["data"]["count"], 1)
        self.assertEqual(self.client.post(self.path(task, "approval/"), {"decision": "returned"}, format="json").status_code, 400)
        returned = self.client.post(self.path(task, "approval/"), {"decision": "returned", "comment": "信息不全"}, format="json")
        self.assertEqual(returned.status_code, 200, returned.data)
        self.auth(self.manager)
        self.assertEqual(self.client.post(self.path(task, "resubmit/"), {}, format="json").status_code, 400)
        self.assertEqual(self.client.patch(self.path(task), {"description": "补充了需求"}, format="json").status_code, 200)
        resubmit = self.client.post(self.path(task, "resubmit/"), {}, format="json")
        self.assertEqual(resubmit.data["data"]["approval_round"], 2)
        self.assertEqual(self.approve(task, self.founder).status_code, 200)
        self.auth(self.manager)
        tag_only = self.client.patch(self.path(task), {"tag_ids": [self.tag.pk]}, format="json")
        self.assertEqual(tag_only.status_code, 200, tag_only.data)
        self.assertEqual(Task.objects.get(pk=task.pk).approval_status, "approved")
        self.assertEqual(TaskApprovalRecord.objects.filter(task=task).count(), 2)
        key_change = self.client.patch(self.path(task), {"due_at": (timezone.now() + timedelta(days=7)).isoformat()}, format="json")
        self.assertEqual(key_change.status_code, 200, key_change.data)
        self.assertEqual(Task.objects.get(pk=task.pk).approval_status, "pending")
        self.assertEqual(TaskApprovalRecord.objects.filter(task=task).count(), 3)
        self.auth(self.leader)
        self.assertEqual(self.client.get(self.path(task)).status_code, 404)
        self.assertEqual(self.approve(task, self.founder).status_code, 200)
        self.assertEqual(TaskNotification.objects.filter(task=task, recipient=self.leader,
            notification_type="assignment_received").count(), 2)
        self.auth(self.manager)
        history = self.client.get(self.path(task, "history/"))
        self.assertEqual(history.status_code, 200)
        self.assertGreaterEqual(len(history.data["data"]["results"]), 4)

    def test_execution_acceptance_return_self_completion_and_cancel(self):
        task = Task.objects.get(pk=self.create(self.manager, self.leader, required_materials="评审文档").data["data"]["id"])
        self.approve(task, self.founder)
        self.auth(self.leader)
        self.assertEqual(self.client.post(self.path(task, "updates/"), {"content": "进展", "percent": 50}, format="json").status_code, 400)
        self.assertEqual(self.client.post(self.path(task, "updates/"), {"content": "开始实现"}, format="json").status_code, 201)
        self.assertEqual(self.client.post(self.path(task, "submissions/"), {"completion_note": "完成"}, format="json").status_code, 400)
        submitted = self.client.post(self.path(task, "submissions/"), {"completion_note": "完成", "materials_note": "链接"}, format="json")
        self.assertEqual(submitted.status_code, 201, submitted.data)
        submission_id = submitted.data["data"]["id"]
        self.assertEqual(self.client.post(self.path(task, "submissions/"), {"completion_note": "重复", "materials_note": "链接"}, format="json").status_code, 400)
        self.auth(self.founder)
        self.assertEqual(self.client.post(self.path(task, f"submissions/{submission_id}/review/"), {"decision": "accepted"}, format="json").status_code, 403)
        self.auth(self.manager)
        self.assertEqual(self.client.post(self.path(task, f"submissions/{submission_id}/review/"), {"decision": "returned"}, format="json").status_code, 400)
        self.assertEqual(self.client.post(self.path(task, f"submissions/{submission_id}/review/"), {"decision": "returned", "comment": "请完善"}, format="json").status_code, 200)
        self.auth(self.leader)
        again = self.client.post(self.path(task, "submissions/"), {"completion_note": "重做完成", "materials_note": "新链接"}, format="json")
        self.assertEqual(again.status_code, 201)
        self.auth(self.manager)
        accepted = self.client.post(self.path(task, f"submissions/{again.data['data']['id']}/review/"), {"decision": "accepted"}, format="json")
        self.assertEqual(accepted.status_code, 200)
        self.assertEqual(Task.objects.get(pk=task.pk).completion_status, "completed")
        self.assertEqual(self.client.post(self.path(task, "cancel/"), {"reason": "不需要"}, format="json").status_code, 400)
        personal = Task.objects.get(pk=self.create(self.employee, self.employee).data["data"]["id"])
        self.approve(personal, self.leader)
        self.auth(self.employee)
        self.assertEqual(self.client.post(self.path(personal, "submissions/"), {"completion_note": "自行完成"}, format="json").data["data"]["review_status"], "self_completed")
        self.assertEqual(Task.objects.get(pk=personal.pk).completion_status, "completed")
        cancellable = Task.objects.get(pk=self.create(self.manager, self.leader).data["data"]["id"])
        self.auth(self.manager)
        self.assertEqual(self.client.post(self.path(cancellable, "cancel/"), {}, format="json").status_code, 400)
        self.assertEqual(self.client.post(self.path(cancellable, "cancel/"), {"reason": "计划调整"}, format="json").status_code, 200)
        self.assertEqual(Task.objects.get(pk=cancellable.pk).lifecycle_status, "cancelled")
        self.assertEqual(Task.objects.get(pk=task.pk).completion_status, "completed")

    def test_subordinate_scope_cross_department_and_notification_read_range(self):
        task = Task.objects.get(pk=self.create(self.manager, self.other_manager).data["data"]["id"])
        self.auth(self.manager)
        self.assertEqual(self.client.get("/api/v1/app/organization/visible-subordinates/").data["data"]["count"], 2)
        self.assertEqual(self.client.get(f"/api/v1/app/tasks/subordinates/?user_id={self.other_manager.pk}").status_code, 403)
        self.assertEqual(self.client.get("/api/v1/app/tasks/?scope=created").data["data"]["count"], 0)
        self.approve(task, self.founder)
        self.auth(self.other_manager)
        self.assertEqual(self.client.get("/api/v1/app/tasks/?scope=mine").data["data"]["count"], 1)
        self.auth(self.manager)
        self.assertEqual(self.client.get(self.path(task)).status_code, 200)
        self.assertEqual(self.client.get("/api/v1/app/tasks/subordinates/?task_scope=created").data["data"]["count"], 0)
        self.auth(self.founder)
        self.assertEqual(self.client.get(f"/api/v1/app/tasks/subordinates/?task_scope=created&user_id={self.manager.pk}").data["data"]["count"], 1)
        self.assertEqual(self.client.get(f"/api/v1/app/tasks/subordinates/?task_scope=assigned&user_id={self.other_manager.pk}").data["data"]["count"], 1)
        self.assertEqual(self.client.get("/api/v1/app/notifications/unread-count/").data["data"]["unread_count"], 1)
        notification = self.client.get("/api/v1/app/notifications/?unread_only=true").data["data"]["results"][0]
        self.assertEqual(self.client.get("/api/v1/app/notifications/unread-count/").data["data"]["unread_count"], 1)
        extra = TaskNotification.objects.create(recipient=self.founder, task=task,
            notification_type=TaskNotification.Type.APPROVAL_REQUIRED, event_key="test-extra")
        self.assertEqual(self.client.post("/api/v1/app/notifications/mark-read/", {"notification_ids": [notification["id"]]}, format="json").data["data"]["marked_read_count"], 1)
        self.assertFalse(TaskNotification.objects.get(pk=extra.pk).is_read)
        self.auth(self.manager)
        self.assertEqual(self.client.post("/api/v1/app/notifications/mark-read/", {"notification_ids": [extra.pk]}, format="json").data["data"]["marked_read_count"], 0)
        self.assertEqual(self.client.get("/api/v1/app/notifications/unread-count/").data["data"]["unread_count"], 0)

    def test_end_to_end_hierarchy_and_cross_department_isolation(self):
        chain = [
            (self.founder, self.manager, None),
            (self.manager, self.leader, self.founder),
            (self.leader, self.employee, self.manager),
        ]
        for creator, assignee, approver in chain:
            response = self.create(creator, assignee)
            self.assertEqual(response.status_code, 201, response.data)
            task = Task.objects.get(pk=response.data["data"]["id"])
            if approver:
                self.assertEqual(self.approve(task, approver).status_code, 200)
            self.auth(assignee)
            submitted = self.client.post(self.path(task, "submissions/"), {"completion_note": "完成"}, format="json")
            self.assertEqual(submitted.status_code, 201, submitted.data)
            self.auth(creator)
            self.assertEqual(self.client.post(self.path(task, f"submissions/{submitted.data['data']['id']}/review/"),
                {"decision": "accepted"}, format="json").status_code, 200)
            self.assertEqual(Task.objects.get(pk=task.pk).completion_status, "completed")

        cross = Task.objects.get(pk=self.create(self.manager, self.other_manager).data["data"]["id"])
        self.approve(cross, self.founder)
        internal = Task.objects.get(pk=self.create(self.other_manager, self.other_leader).data["data"]["id"])
        self.approve(internal, self.founder)
        self.auth(self.manager)
        self.assertEqual(self.client.get(self.path(cross)).status_code, 200)
        self.assertEqual(self.client.get(self.path(internal)).status_code, 404)
        self.assertEqual(self.client.get("/api/v1/app/tasks/subordinates/?task_scope=created").data["data"]["count"], 1)

    def test_due_date_filter_and_admin_scope(self):
        task = Task.objects.get(pk=self.create(self.founder, self.employee).data["data"]["id"])
        self.auth(self.employee)
        self.assertEqual(self.client.get("/api/v1/app/tasks/?due_from=2099-01-01").data["data"]["count"], 0)
        self.assertEqual(self.client.get("/api/v1/app/tasks/?due_to=2099-01-01").data["data"]["count"], 1)
        self.assertEqual(self.client.get("/api/v1/app/tasks/?scope=bad").status_code, 400)
        admin = User.objects.create_superuser(username="admin", password="password")
        self.auth(admin)
        for path in (self.path(task), "/api/v1/app/tasks/", "/api/v1/app/notifications/"):
            self.assertEqual(self.client.get(path).status_code, 403)

    def test_task_tag_history_atomic_validation_and_all_scope_deduplication(self):
        task = Task.objects.get(pk=self.create(self.founder, self.employee, tag_ids=[self.tag.pk]).data["data"]["id"])
        self.auth(self.founder)
        self.assertEqual(self.client.delete(f"/api/v1/app/tags/{self.tag.pk}/").status_code, 200)
        self.assertEqual(self.client.get(self.path(task)).data["data"]["tags"][0]["id"], self.tag.pk)
        self.assertEqual(self.client.patch(self.path(task), {"tag_ids": [self.tag.pk]}, format="json").status_code, 200)
        invalid = self.client.patch(self.path(task), {"title": "不能写入", "tag_ids": [self.tag.pk, 99999]}, format="json")
        self.assertEqual(invalid.status_code, 400)
        self.assertEqual(Task.objects.get(pk=task.pk).title, "实现接口")
        self.assertEqual(self.client.patch(self.path(task), {"tag_ids": []}, format="json").status_code, 200)
        self.assertEqual(self.client.patch(self.path(task), {"tag_ids": [self.tag.pk]}, format="json").status_code, 400)

        personal = Task.objects.get(pk=self.create(self.employee, self.employee).data["data"]["id"])
        self.approve(personal, self.leader)
        self.auth(self.employee)
        results = self.client.get("/api/v1/app/tasks/?scope=all").data["data"]["results"]
        self.assertEqual([row["id"] for row in results].count(personal.pk), 1)
