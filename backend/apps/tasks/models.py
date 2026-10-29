from django.conf import settings
from django.db import models
from django.db.models import Q


class Task(models.Model):
    class Approval(models.TextChoices):
        PENDING = "pending", "待审批"
        RETURNED = "returned", "已退回"
        APPROVED = "approved", "已通过"

    class Completion(models.TextChoices):
        UNFINISHED = "unfinished", "未完成"
        COMPLETED = "completed", "已完成"

    class Lifecycle(models.TextChoices):
        ACTIVE = "active", "进行中"
        CANCELLED = "cancelled", "已取消"

    creator = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="created_tasks")
    assignee = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="assigned_tasks")
    title = models.CharField(max_length=200)
    description = models.TextField()
    required_materials = models.TextField(blank=True)
    association_note = models.TextField(blank=True)
    creator_department = models.ForeignKey("accounts.Department", null=True, blank=True, on_delete=models.PROTECT, related_name="creator_tasks")
    assignee_department = models.ForeignKey("accounts.Department", null=True, blank=True, on_delete=models.PROTECT, related_name="assignee_tasks")
    due_at = models.DateTimeField()
    approval_status = models.CharField(max_length=10, choices=Approval.choices)
    completion_status = models.CharField(max_length=12, choices=Completion.choices, default=Completion.UNFINISHED)
    lifecycle_status = models.CharField(max_length=10, choices=Lifecycle.choices, default=Lifecycle.ACTIVE)
    completed_at = models.DateTimeField(null=True, blank=True)
    cancelled_at = models.DateTimeField(null=True, blank=True)
    cancellation_reason = models.TextField(blank=True)
    returned_edit_required = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-created_at", "-id"]
        indexes = [
            models.Index(fields=["assignee", "approval_status", "completion_status", "lifecycle_status", "due_at"]),
            models.Index(fields=["creator", "approval_status", "created_at"]),
        ]


class TaskApprovalRecord(models.Model):
    class Decision(models.TextChoices):
        PENDING = "pending", "待审批"
        APPROVED = "approved", "已通过"
        RETURNED = "returned", "已退回"

    task = models.ForeignKey(Task, on_delete=models.PROTECT, related_name="approval_records")
    round = models.PositiveIntegerField()
    approver = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    decision = models.CharField(max_length=10, choices=Decision.choices, default=Decision.PENDING)
    comment = models.TextField(blank=True)
    decided_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["round"]
        constraints = [models.UniqueConstraint(fields=["task", "round"], name="unique_task_approval_round")]


class TaskUpdate(models.Model):
    task = models.ForeignKey(Task, on_delete=models.PROTECT, related_name="updates")
    author = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["created_at", "id"]


class TaskSubmission(models.Model):
    class Review(models.TextChoices):
        WAITING = "waiting", "待验收"
        RETURNED = "returned", "已退回"
        ACCEPTED = "accepted", "已验收"
        SELF_COMPLETED = "self_completed", "自行完成"

    task = models.ForeignKey(Task, on_delete=models.PROTECT, related_name="submissions")
    submitted_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="task_submissions")
    completion_note = models.TextField()
    materials_note = models.TextField(blank=True)
    review_status = models.CharField(max_length=16, choices=Review.choices)
    reviewed_by = models.ForeignKey(settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.PROTECT, related_name="task_reviews")
    review_comment = models.TextField(blank=True)
    submitted_at = models.DateTimeField(auto_now_add=True)
    reviewed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["submitted_at", "id"]


class TaskChangeHistory(models.Model):
    class Change(models.TextChoices):
        CREATED = "created", "创建"
        UPDATED = "updated", "修改"

    task = models.ForeignKey(Task, on_delete=models.PROTECT, related_name="changes")
    actor = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    change_type = models.CharField(max_length=8, choices=Change.choices)
    changes = models.JSONField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["created_at", "id"]


class TaskNotification(models.Model):
    class Type(models.TextChoices):
        APPROVAL_REQUIRED = "approval_required", "待审批"
        ASSIGNMENT_RECEIVED = "assignment_received", "新任务"
        TASK_CANCELLED = "task_cancelled", "任务已取消"
        SUBMISSION_RECEIVED = "submission_received", "待验收"
        SUBMISSION_ACCEPTED = "submission_accepted", "验收通过"
        SUBMISSION_RETURNED = "submission_returned", "结果退回"

    recipient = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="task_notifications")
    task = models.ForeignKey(Task, on_delete=models.PROTECT, related_name="notifications")
    submission = models.ForeignKey(TaskSubmission, null=True, blank=True, on_delete=models.PROTECT,
                                   related_name="notifications")
    notification_type = models.CharField(max_length=24, choices=Type.choices)
    event_key = models.CharField(max_length=100, unique=True)
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    read_at = models.DateTimeField(null=True, blank=True)
    resolved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at", "-id"]
        indexes = [models.Index(fields=["recipient", "is_read", "created_at"])]
