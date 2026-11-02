"""首页四面板的聚合规则与个人十大重点工作的排序维护。

四个面板都只读权威模块数据：公司事项来自 content、任务来自 tasks、日志来自 logs。
`HomePersonalFocus*` 只保存用户自选顺序与首次初始化标记，不复制任务字段。
"""

from django.db import transaction
from django.db.models import Case, F, IntegerField, Value, When
from django.utils import timezone
from rest_framework.exceptions import ValidationError

from apps.content.services import HOME_PREVIEW_ITEMS, published_items
from apps.logs.services import recent_submitted_logs
from apps.tasks.models import Task, TaskNotification

from .models import HomePersonalFocusState, HomePersonalFocusTask

COMPANY_TOP_LIMIT = 10
PERSONAL_FOCUS_LIMIT = 10
LOG_SUMMARY_CHARS = 50


def candidate_tasks(user):
    """本人承担、已审批、未完成、未取消的任务——公司十大与个人重点的唯一候选源。

    仅按 `assignee` 过滤，因此“只派发给下属”的任务不会进入结果。
    """
    return Task.objects.filter(
        assignee=user,
        approval_status=Task.Approval.APPROVED,
        completion_status=Task.Completion.UNFINISHED,
        lifecycle_status=Task.Lifecycle.ACTIVE,
    )


def ordered_candidates(user):
    """候选任务统一排序：先按来源（他人派发 → 本人自建），再按截止时间、创建时间。"""
    return candidate_tasks(user).annotate(
        source_rank=Case(
            When(creator=F("assignee"), then=Value(1)),
            default=Value(0),
            output_field=IntegerField(),
        )
    ).order_by("source_rank", "due_at", "created_at", "id")


def company_top_tasks(user, limit=COMPANY_TOP_LIMIT):
    """公司十大重要任务：候选任务前 10 条。"""
    return ordered_candidates(user)[:limit]


def task_panel_row(task):
    return {
        "id": task.pk,
        "title": task.title,
        "completion_status": task.completion_status,
        "due_at": task.due_at,
    }


def log_summary(log):
    """首页日志摘要：只取已完成工作的前若干字符，不搬运全文。"""
    text = " ".join((log.completed_work or "").split())
    return text[:LOG_SUMMARY_CHARS]


def unread_notification_count(user):
    """与 Issue 4 通知接口同口径：已被取代（resolved）的通知不计入未读。"""
    return TaskNotification.objects.filter(
        recipient=user, is_read=False, resolved_at__isnull=True,
    ).count()


def _focus_entries(user):
    return HomePersonalFocusTask.objects.filter(user=user).select_related("task").order_by("sort_order", "id")


def _eligible(task, user):
    return bool(
        task is not None
        and task.assignee_id == user.pk
        and task.approval_status == Task.Approval.APPROVED
        and task.completion_status == Task.Completion.UNFINISHED
        and task.lifecycle_status == Task.Lifecycle.ACTIVE
    )


def _cleanup(user):
    """移除已完成/已取消/已转派的任务，压缩剩余顺序并保留相对次序。

    只做“向下”重排（1..n 且 n 不大于原序号），因此不会与
    `unique(user, sort_order)` 冲突。
    """
    entries = list(_focus_entries(user))
    kept = [entry for entry in entries if _eligible(entry.task, user)][:PERSONAL_FOCUS_LIMIT]
    if [entry.pk for entry in kept] == [entry.pk for entry in entries]:
        return kept
    kept_pks = [entry.pk for entry in kept]
    HomePersonalFocusTask.objects.filter(user=user).exclude(pk__in=kept_pks).delete()
    for index, entry in enumerate(kept, start=1):
        if entry.sort_order != index:
            entry.sort_order = index
            entry.save(update_fields=["sort_order", "updated_at"])
    return kept


def ensure_initialized(user):
    """首次读取或首次保存时，用当时的公司十大在事务内预填一次。"""
    with transaction.atomic():
        state, created = HomePersonalFocusState.objects.select_for_update().get_or_create(user=user)
        if state.initialized_at is None:
            tasks = list(company_top_tasks(user, PERSONAL_FOCUS_LIMIT))
            HomePersonalFocusTask.objects.bulk_create(
                [
                    HomePersonalFocusTask(user=user, task=task, sort_order=index)
                    for index, task in enumerate(tasks, start=1)
                ],
                ignore_conflicts=True,
            )
            state.initialized_at = timezone.now()
            state.save(update_fields=["initialized_at", "updated_at"])
    return state


def personal_focus(user):
    """读取个人重点：初始化 → 清理失效项 → 返回当前有效顺序。"""
    ensure_initialized(user)
    return _cleanup(user)


def save_personal_focus(user, task_ids):
    ids = list(task_ids)
    if len(set(ids)) != len(ids):
        raise ValidationError({"task_ids": "同一任务不能重复选择。"})
    if len(ids) > PERSONAL_FOCUS_LIMIT:
        raise ValidationError({"task_ids": f"个人重点工作最多 {PERSONAL_FOCUS_LIMIT} 条。"})
    with transaction.atomic():
        state, created = HomePersonalFocusState.objects.select_for_update().get_or_create(user=user)
        eligible_pks = set(candidate_tasks(user).filter(pk__in=ids).values_list("pk", flat=True))
        missing = [pk for pk in ids if pk not in eligible_pks]
        if missing:
            raise ValidationError({
                "task_ids": f"以下任务不可选（已完成、已取消或不属于本人）：{missing}。",
            })
        HomePersonalFocusTask.objects.filter(user=user).delete()
        HomePersonalFocusTask.objects.bulk_create(
            [
                HomePersonalFocusTask(user=user, task_id=task_id, sort_order=index)
                for index, task_id in enumerate(ids, start=1)
            ]
        )
        if state.initialized_at is None:
            state.initialized_at = timezone.now()
            state.save(update_fields=["initialized_at", "updated_at"])
    return ids


def home_payload(user):
    """四面板各最多 5 条；任务与日志按当前用户裁剪，公司事项对所有业务用户一致。"""
    focus_entries = personal_focus(user)
    return {
        "company_items": [
            {
                "id": item.pk, "title": item.title, "summary": item.summary,
                "published_at": item.published_at, "sort_order": item.sort_order,
            }
            for item in published_items(HOME_PREVIEW_ITEMS)
        ],
        "company_top_tasks": [task_panel_row(task) for task in company_top_tasks(user, HOME_PREVIEW_ITEMS)],
        "personal_focus_tasks": [
            {
                "id": entry.task_id, "title": entry.task.title, "sort_order": entry.sort_order,
                "completion_status": entry.task.completion_status, "due_at": entry.task.due_at,
            }
            for entry in focus_entries[:HOME_PREVIEW_ITEMS]
        ],
        "recent_logs": [
            {"id": log.pk, "log_date": log.log_date, "summary": log_summary(log)}
            for log in recent_submitted_logs(user, HOME_PREVIEW_ITEMS)
        ],
        "unread_notification_count": unread_notification_count(user),
    }
