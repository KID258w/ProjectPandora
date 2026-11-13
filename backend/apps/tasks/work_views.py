"""Read-only task calendar projections; no calendar records are persisted."""

import calendar
from datetime import date, timedelta

from django.utils import timezone
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import serializers
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError
from rest_framework.response import Response

from apps.accounts.models import User
from apps.core.api import BusinessAPIView
from apps.logs.views import parse_date

from .models import Task, TaskApprovalRecord, TaskChangeHistory, TaskSubmission, TaskUpdate
from .services import subordinate_ids, visible_task
from .china_calendar import calendar_days_for_range


NEUTRAL_COLOR = "#D1D5DB"
USER_PARAMETER = OpenApiParameter("user_id", int, description="目标成员 ID；省略时为本人。仅授权上级可查询下属。")
DATE_PARAMETER = OpenApiParameter("date", str, description="YYYY-MM-DD；省略时为今天。")


class WorkBarSerializer(serializers.Serializer):
    task_id = serializers.IntegerField()
    title = serializers.CharField()
    bar_color = serializers.CharField()
    start_date = serializers.DateField()
    end_date = serializers.DateField()


class WorkDaySerializer(serializers.Serializer):
    date = serializers.DateField()
    bars = WorkBarSerializer(many=True)
    overflow_count = serializers.IntegerField()


class CalendarDaySerializer(serializers.Serializer):
    date = serializers.DateField()
    day_type = serializers.ChoiceField(choices=("holiday", "rest_day", "makeup_workday"))
    holiday_name = serializers.CharField(allow_null=True)
    label = serializers.CharField()


class WorkEventSerializer(serializers.Serializer):
    task_id = serializers.IntegerField()
    task_title = serializers.CharField()
    event_type = serializers.CharField()
    occurred_at = serializers.DateTimeField()
    tag_color = serializers.CharField()


class MonthViewSerializer(serializers.Serializer):
    user_id = serializers.IntegerField()
    year = serializers.IntegerField()
    month = serializers.IntegerField()
    start_date = serializers.DateField()
    end_date = serializers.DateField()
    calendar_start_date = serializers.DateField()
    calendar_end_date = serializers.DateField()
    days = WorkDaySerializer(many=True)
    calendar_days = CalendarDaySerializer(many=True)


class WeekViewSerializer(serializers.Serializer):
    user_id = serializers.IntegerField()
    start_date = serializers.DateField()
    end_date = serializers.DateField()
    dates = serializers.ListField(child=serializers.DateField())
    all_day_tasks = WorkBarSerializer(many=True)
    events = WorkEventSerializer(many=True)
    calendar_days = CalendarDaySerializer(many=True)


class DayViewSerializer(WeekViewSerializer):
    date = serializers.DateField()


def target_user(request):
    value = request.query_params.get("user_id")
    if value is None:
        return request.user
    try:
        user_id = int(value)
    except (TypeError, ValueError) as exc:
        raise ValidationError({"user_id": "应为用户 ID。"}) from exc
    if user_id == request.user.pk:
        return request.user
    if user_id not in subordinate_ids(request.user):
        raise PermissionDenied("不能查看该成员的工作视图。")
    target = User.objects.filter(pk=user_id, account_type=User.AccountType.BUSINESS, is_active=True).first()
    if target is None:
        raise NotFound("成员不存在。")
    return target


def local_date(value):
    return timezone.localtime(value).date()


def color_for(task):
    links = task.tag_links.all()
    first = min(links, key=lambda link: (link.added_at, link.pk), default=None)
    return first.tag.color if first else NEUTRAL_COLOR


def bar(task, start_date=None, end_date=None):
    return {
        "task_id": task.pk, "title": task.title, "bar_color": color_for(task),
        "start_date": start_date or local_date(task.estimated_start_at or task.created_at),
        "end_date": end_date or local_date(task.due_at),
    }


def task_rows(queryset):
    return list(queryset.prefetch_related("tag_links__tag"))


def month_data(user, year, month):
    first = date(year, month, 1)
    last = date(year, month, calendar.monthrange(year, month)[1])
    calendar_start = first - timedelta(days=first.weekday())
    calendar_end = last + timedelta(days=6 - last.weekday())
    # DateTimeField date lookup uses the configured business time zone.
    tasks = task_rows(Task.objects.filter(
        assignee=user, approval_status=Task.Approval.APPROVED,
        lifecycle_status=Task.Lifecycle.ACTIVE, due_at__date__gte=calendar_start,
    ))
    bars = [bar(task) for task in tasks]
    bars = [item for item in bars if item["start_date"] <= calendar_end and item["end_date"] >= calendar_start]
    bars.sort(key=lambda item: (item["start_date"], item["task_id"]))
    days = []
    day = calendar_start
    while day <= calendar_end:
        matching = [item for item in bars if item["start_date"] <= day <= item["end_date"]]
        days.append({"date": day, "bars": matching[:3], "overflow_count": max(0, len(matching) - 3)})
        day += timedelta(days=1)
    return {"user_id": user.pk, "year": year, "month": month,
            "start_date": first, "end_date": last,
            "calendar_start_date": calendar_start, "calendar_end_date": calendar_end,
            "days": days, "calendar_days": calendar_days_for_range(calendar_start, calendar_end)}


def all_day_tasks(user, selected_start, selected_end):
    tasks = task_rows(Task.objects.filter(
        assignee=user, approval_status=Task.Approval.APPROVED,
        lifecycle_status=Task.Lifecycle.ACTIVE,
        due_at__date__gte=selected_start,
    ).order_by("due_at", "pk"))
    result = []
    for task in tasks:
        task_bar = bar(task)
        start = max(task_bar["start_date"], selected_start)
        end = min(task_bar["end_date"], selected_end)
        if start <= end:
            task_bar.update(start_date=start, end_date=end)
            result.append(task_bar)
    return result


def events(request, user, start, end):
    """Project actual recorded operations, then enforce the task detail ACL."""
    today = timezone.localdate()
    if start > today:
        return []
    end = min(end, today)
    result = []
    task_ids = set()

    def add(task_id, when, event_type):
        if when is not None and start <= local_date(when) <= end:
            result.append((task_id, when, event_type))
            task_ids.add(task_id)

    for row in TaskChangeHistory.objects.filter(actor=user, created_at__date__range=(start, end)).select_related("task"):
        if row.change_type == TaskChangeHistory.Change.CREATED:
            add(row.task_id, row.created_at, "created_and_submitted" if row.task.approval_records.exists() else "created")
        else:
            add(row.task_id, row.created_at, "updated")
    for row in TaskApprovalRecord.objects.filter(created_at__date__range=(start, end), task__creator=user, round__gt=1):
        add(row.task_id, row.created_at, "resubmitted")
    for row in TaskApprovalRecord.objects.filter(approver=user, decided_at__date__range=(start, end)).exclude(decision=TaskApprovalRecord.Decision.PENDING):
        add(row.task_id, row.decided_at, "approval_approved" if row.decision == TaskApprovalRecord.Decision.APPROVED else "approval_returned")
    for row in Task.objects.filter(creator=user, cancelled_at__date__range=(start, end)):
        add(row.pk, row.cancelled_at, "cancelled")
    for row in TaskUpdate.objects.filter(author=user, created_at__date__range=(start, end)):
        add(row.task_id, row.created_at, "progress_updated")
    for row in TaskSubmission.objects.filter(submitted_by=user, submitted_at__date__range=(start, end)):
        add(row.task_id, row.submitted_at, "submitted_and_completed" if row.review_status == TaskSubmission.Review.SELF_COMPLETED else "completion_submitted")
    for row in TaskSubmission.objects.filter(reviewed_by=user, reviewed_at__date__range=(start, end)).exclude(review_status=TaskSubmission.Review.SELF_COMPLETED):
        add(row.task_id, row.reviewed_at, "acceptance_approved" if row.review_status == TaskSubmission.Review.ACCEPTED else "acceptance_returned")

    allowed = {}
    for task_id in task_ids:
        try:
            allowed[task_id] = visible_task(request, task_id)
        except NotFound:
            pass
    from django.db.models import prefetch_related_objects
    prefetch_related_objects(list(allowed.values()), "tag_links__tag")
    response = [{"task_id": task_id, "task_title": allowed[task_id].title,
                 "event_type": kind, "occurred_at": when, "tag_color": color_for(allowed[task_id])}
                for task_id, when, kind in result if task_id in allowed]
    response.sort(key=lambda item: (item["occurred_at"], item["task_id"], item["event_type"]))
    return response


def selected_date(request):
    value = request.query_params.get("date")
    return parse_date(value, "date") if value is not None else timezone.localdate()


class MonthWorkView(BusinessAPIView):
    serializer_class = MonthViewSerializer

    @extend_schema(tags=["App 工作视图"], parameters=[USER_PARAMETER, OpenApiParameter("year", int), OpenApiParameter("month", int)], responses=MonthViewSerializer)
    def get(self, request):
        today = timezone.localdate()
        try:
            year = int(request.query_params.get("year", today.year))
            month = int(request.query_params.get("month", today.month))
            date(year, month, 1)
        except (TypeError, ValueError) as exc:
            raise ValidationError({"year_month": "年份或月份无效。"}) from exc
        return Response(month_data(target_user(request), year, month))


class WeekWorkView(BusinessAPIView):
    serializer_class = WeekViewSerializer

    @extend_schema(tags=["App 工作视图"], parameters=[USER_PARAMETER, DATE_PARAMETER], responses=WeekViewSerializer)
    def get(self, request):
        user = target_user(request)
        selected = selected_date(request)
        start = selected - timedelta(days=selected.weekday())
        end = start + timedelta(days=6)
        return Response({"user_id": user.pk, "start_date": start, "end_date": end,
                         "dates": [start + timedelta(days=i) for i in range(7)],
                         "all_day_tasks": all_day_tasks(user, start, end),
                         "events": events(request, user, start, end),
                         "calendar_days": calendar_days_for_range(start, end)})


class DayWorkView(BusinessAPIView):
    serializer_class = DayViewSerializer

    @extend_schema(tags=["App 工作视图"], parameters=[USER_PARAMETER, DATE_PARAMETER], responses=DayViewSerializer)
    def get(self, request):
        user = target_user(request)
        selected = selected_date(request)
        start = selected - timedelta(days=selected.weekday())
        end = start + timedelta(days=6)
        return Response({"user_id": user.pk, "date": selected, "start_date": start, "end_date": end,
                         "dates": [start + timedelta(days=i) for i in range(7)],
                         "all_day_tasks": all_day_tasks(user, selected, selected),
                         "events": events(request, user, selected, selected),
                         "calendar_days": calendar_days_for_range(start, end)})
