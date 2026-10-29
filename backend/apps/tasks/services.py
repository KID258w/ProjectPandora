from datetime import datetime

from django.db.models import Q
from django.utils import timezone
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError

from apps.accounts.authentication import has_complete_supervisor_chain
from apps.accounts.models import User
from apps.logs.views import descendants
from apps.tags.models import TaskTag
from apps.tags.services import validate_tag_ids

from .models import Task, TaskApprovalRecord, TaskNotification


def assignable_users(user):
    base = User.objects.filter(account_type=User.AccountType.BUSINESS, is_active=True)
    if user.business_role == User.Role.FOUNDER:
        queryset = base
    elif user.business_role == User.Role.DEPARTMENT_MANAGER:
        queryset = base.filter(
            Q(pk=user.pk) |
            Q(business_role=User.Role.TEAM_LEADER, department_id=user.department_id) |
            Q(business_role=User.Role.DEPARTMENT_MANAGER) & ~Q(department_id=user.department_id)
        )
    elif user.business_role == User.Role.TEAM_LEADER:
        queryset = base.filter(Q(pk=user.pk) | Q(business_role=User.Role.EMPLOYEE, team_id=user.team_id))
    else:
        queryset = base.filter(pk=user.pk)
    return [candidate for candidate in queryset.select_related("department", "team", "manager").order_by("id")
            if has_complete_supervisor_chain(candidate)]


def validate_assignee(creator, assignee_id):
    for candidate in assignable_users(creator):
        if candidate.pk == assignee_id:
            return candidate
    raise ValidationError({"assignee_id": "负责人不在可指派范围内或已停用。"})


def subordinate_ids(user):
    return {u.pk for u in descendants(user) if has_complete_supervisor_chain(u)}


def visible_task(request, pk):
    task = Task.objects.select_related("creator", "assignee").filter(pk=pk).first()
    if task is None:
        raise NotFound("任务不存在。")
    user = request.user
    if task.creator_id == user.pk:
        return task
    if task.approval_status == Task.Approval.PENDING:
        if task.approval_records.filter(decision=TaskApprovalRecord.Decision.PENDING, approver=user).exists():
            return task
    elif task.approval_status == Task.Approval.APPROVED:
        if task.assignee_id == user.pk or task.creator_id in subordinate_ids(user) or task.assignee_id in subordinate_ids(user):
            return task
    raise NotFound("任务不存在。")


def require_creator(task, user):
    if task.creator_id != user.pk:
        raise PermissionDenied("仅直接派发者可执行此操作。")


def require_assignee(task, user):
    if task.assignee_id != user.pk:
        raise PermissionDenied("仅任务负责人可执行此操作。")


def require_executable(task, user):
    require_assignee(task, user)
    if task.approval_status != Task.Approval.APPROVED or task.lifecycle_status != Task.Lifecycle.ACTIVE or task.completion_status != Task.Completion.UNFINISHED:
        raise ValidationError("任务尚不可执行、已完成或已取消。")


def create_approval_round(task):
    approver = task.creator.manager
    if approver is None or not has_complete_supervisor_chain(approver):
        raise ValidationError("创建人直属上级不可用，无法送审。")
    last = task.approval_records.order_by("-round").first()
    record = TaskApprovalRecord.objects.create(task=task, round=(last.round + 1 if last else 1), approver=approver)
    TaskNotification.objects.create(recipient=approver, task=task,
        notification_type=TaskNotification.Type.APPROVAL_REQUIRED,
        event_key=f"approval:{task.pk}:{record.round}")
    return record


def create_task_notification(task, recipient, notification_type, event_key, submission=None):
    if recipient is None:
        return None
    notification, _ = TaskNotification.objects.get_or_create(event_key=event_key, defaults={
        "recipient": recipient,
        "task": task,
        "submission": submission,
        "notification_type": notification_type,
    })
    return notification


def resolve_task_notifications(task, notification_types=None, submission=None):
    rows = TaskNotification.objects.filter(task=task, resolved_at__isnull=True)
    if notification_types is not None:
        rows = rows.filter(notification_type__in=notification_types)
    if submission is not None:
        rows = rows.filter(submission=submission)
    return rows.update(resolved_at=timezone.now())


def send_assignment(task, approval_id=0):
    if task.assignee_id != task.creator_id:
        create_task_notification(task, task.assignee, TaskNotification.Type.ASSIGNMENT_RECEIVED,
                                 f"assignment:{task.pk}:{approval_id}")


def set_task_tags(task, tags):
    if tags is None:
        return
    current = set(task.tag_links.values_list("tag_id", flat=True))
    wanted = {tag.pk for tag in tags}
    task.tag_links.exclude(tag_id__in=wanted).delete()
    for tag in tags:
        if tag.pk not in current:
            TaskTag.objects.create(task=task, tag=tag)


def validated_task_tags(task, tag_ids):
    current = set(task.tag_links.values_list("tag_id", flat=True)) if task else set()
    return validate_tag_ids(tag_ids, current)


def user_brief(user):
    return {"id": user.pk, "display_name": user.display_name or user.username}


def task_tags(task):
    return [{"id": link.tag_id, "name": link.tag.name, "color": link.tag.color}
            for link in task.tag_links.select_related("tag").order_by("added_at", "id")]


def task_summary(task):
    return {"id": task.pk, "title": task.title, "creator_id": task.creator_id,
            "assignee_id": task.assignee_id, "creator_name": task.creator.display_name or task.creator.username,
            "assignee_name": task.assignee.display_name or task.assignee.username,
            "approval_status": task.approval_status, "completion_status": task.completion_status,
            "lifecycle_status": task.lifecycle_status, "due_at": task.due_at}


def task_detail(task):
    data = task_summary(task)
    data.update(creator=user_brief(task.creator), assignee=user_brief(task.assignee),
        description=task.description, required_materials=task.required_materials,
        association_note=task.association_note, creator_department_id=task.creator_department_id,
        assignee_department_id=task.assignee_department_id,
        completed_at=task.completed_at, cancelled_at=task.cancelled_at,
        cancellation_reason=task.cancellation_reason, created_at=task.created_at, updated_at=task.updated_at,
        tags=task_tags(task),
        approval_history=[{"id": r.pk, "round": r.round, "approver": user_brief(r.approver),
                           "decision": r.decision, "comment": r.comment,
                           "submitted_at": r.created_at, "decided_at": r.decided_at}
                          for r in task.approval_records.select_related("approver")],
        updates=[{"id": row.pk, "author": user_brief(row.author), "content": row.content,
                  "created_at": row.created_at} for row in task.updates.select_related("author")],
        submissions=[{"id": row.pk, "submitted_by": user_brief(row.submitted_by),
                      "completion_note": row.completion_note, "materials_note": row.materials_note,
                      "review_status": row.review_status, "reviewed_by": user_brief(row.reviewed_by) if row.reviewed_by else None,
                      "review_comment": row.review_comment, "submitted_at": row.submitted_at, "reviewed_at": row.reviewed_at}
                     for row in task.submissions.select_related("submitted_by", "reviewed_by")])
    return data


def json_value(value):
    if isinstance(value, datetime):
        return value.isoformat()
    return value
