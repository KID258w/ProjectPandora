import calendar
import re
from datetime import date, timedelta

from django.db import IntegrityError, transaction
from django.utils import timezone
from drf_spectacular.utils import extend_schema
from rest_framework import serializers
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError
from rest_framework.response import Response

from apps.accounts.api_serializers import StrictInputMixin
from apps.accounts.models import User
from apps.core.api import BusinessAPIView, StandardPagination, paginated_response
from apps.tags.services import validate_tag_ids

from .models import DailyLog, DailyLogTag
from .serializers import (LogCalendarSerializer, LogDetailSerializer, LogListSerializer,
                          LogMineSerializer, LogSavedSerializer, LogViewerSerializer)


class LogInputSerializer(StrictInputMixin, serializers.Serializer):
    completed_work = serializers.CharField(required=False, allow_blank=True, trim_whitespace=True)
    next_day_plan = serializers.CharField(required=False, allow_blank=True, trim_whitespace=True)
    tag_ids = serializers.ListField(child=serializers.IntegerField(min_value=1), required=False)


def parse_date(value, field):
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        raise ValidationError({field: "日期格式应为 YYYY-MM-DD。"})
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise ValidationError({field: "日期无效。"}) from exc


def editable(log_date):
    today = timezone.localdate()
    return today - timedelta(days=2) <= log_date <= today


def require_editable(log_date):
    if not editable(log_date):
        raise ValidationError({"log_date": "只能填写今天、昨天和前天的日志。"})


def descendants(user):
    """Current direct and indirect reports, bounded to the authenticated organization tree."""
    seen = {user.pk}
    frontier = [user.pk]
    result = []
    while frontier:
        children = list(User.objects.filter(
            manager_id__in=frontier, account_type=User.AccountType.BUSINESS, is_active=True,
        ).select_related("department", "team"))
        frontier = []
        for child in children:
            if child.pk not in seen:
                seen.add(child.pk)
                result.append(child)
                frontier.append(child.pk)
    return result


def target_user(request):
    value = request.query_params.get("user_id")
    if value in (None, ""):
        return request.user
    try:
        user_id = int(value)
    except ValueError as exc:
        raise ValidationError({"user_id": "应为用户 ID。"}) from exc
    if user_id == request.user.pk:
        return request.user
    for candidate in descendants(request.user):
        if candidate.pk == user_id:
            return candidate
    raise PermissionDenied("不能查看该用户的日志。")


def tags_for(log):
    return [
        {"id": link.tag_id, "name": link.tag.name, "color": link.tag.color}
        for link in log.tag_links.select_related("tag").order_by("added_at", "id")
    ]


def log_data(log, detail=False):
    data = {
        "id": log.pk, "author_id": log.author_id,
        "author_name": log.author.display_name or log.author.username,
        "log_date": log.log_date, "status": log.status,
        "completed_work": log.completed_work, "next_day_plan": log.next_day_plan,
        "submitted_at": log.submitted_at, "updated_at": log.updated_at,
    }
    if detail:
        data["author"] = {"id": log.author_id, "display_name": data["author_name"]}
        data["department_id"] = log.department_id
        data["team_id"] = log.team_id
        data["created_at"] = log.created_at
        data["tags"] = tags_for(log)
    return data


def visible_log(request, pk):
    log = DailyLog.objects.select_related("author").filter(pk=pk).first()
    if log is None:
        raise NotFound("日志不存在。")
    if log.author_id == request.user.pk:
        return log
    if log.status != DailyLog.Status.SUBMITTED or log.author_id not in {u.pk for u in descendants(request.user)}:
        raise NotFound("日志不存在。")
    return log


class LogCalendarView(BusinessAPIView):
    serializer_class = LogCalendarSerializer
    @extend_schema(tags=["App 日志"], summary="按月查询已提交日志日期标记")
    def get(self, request):
        user = target_user(request)
        try:
            year = int(request.query_params.get("year", timezone.localdate().year))
            month = int(request.query_params.get("month", timezone.localdate().month))
            if not 1 <= year <= 9999:
                raise ValueError("year out of range")
            calendar.monthrange(year, month)
        except (ValueError, TypeError) as exc:
            raise ValidationError({"year_month": "年份或月份无效。"}) from exc
        dates = DailyLog.objects.filter(author=user, status=DailyLog.Status.SUBMITTED, log_date__year=year, log_date__month=month).order_by("log_date").values_list("log_date", flat=True)
        return Response({"year": year, "month": month, "days": [{"date": day, "has_submitted_log": True} for day in dates]})


class LogListView(BusinessAPIView):
    serializer_class = LogListSerializer
    pagination_class = StandardPagination
    @extend_schema(tags=["App 日志"], summary="本人或授权下属日志列表", operation_id="app_logs_list")
    def get(self, request):
        user = target_user(request)
        queryset = DailyLog.objects.filter(author=user).select_related("author")
        if user.pk != request.user.pk:
            queryset = queryset.filter(status=DailyLog.Status.SUBMITTED)
        for key, lookup in (("start_date", "log_date__gte"), ("end_date", "log_date__lte")):
            if request.query_params.get(key):
                queryset = queryset.filter(**{lookup: parse_date(request.query_params[key], key)})
        return paginated_response(self, request, queryset, lambda rows: [log_data(row) for row in rows])


class LogDetailView(BusinessAPIView):
    serializer_class = LogDetailSerializer
    @extend_schema(tags=["App 日志"], summary="查看日志详情与历史标签", operation_id="app_logs_detail")
    def get(self, request, pk):
        return Response(log_data(visible_log(request, pk), detail=True))


class LogMineView(BusinessAPIView):
    serializer_class = LogMineSerializer
    @extend_schema(tags=["App 日志"], summary="读取本人指定日期日志或空表单态")
    def get(self, request, log_date):
        day = parse_date(log_date, "log_date")
        log = DailyLog.objects.select_related("author").filter(author=request.user, log_date=day).first()
        return Response({"log_date": day, "log": log_data(log, detail=True) if log else None,
                         "status": log.status if log else "not_created", "can_edit": editable(day)})

    @extend_schema(tags=["App 日志"], summary="保存本人近三天日志", request=LogInputSerializer, responses=LogSavedSerializer)
    def put(self, request, log_date):
        day = parse_date(log_date, "log_date")
        require_editable(day)
        form = LogInputSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        values = form.validated_data
        try:
            with transaction.atomic():
                log = DailyLog.objects.select_for_update().filter(author=request.user, log_date=day).first()
                existing_ids = set(log.tag_links.values_list("tag_id", flat=True)) if log else set()
                tags = validate_tag_ids(values["tag_ids"], existing_ids) if "tag_ids" in values else None
                completed_work = values.get("completed_work", log.completed_work if log else "")
                next_day_plan = values.get("next_day_plan", log.next_day_plan if log else "")
                resulting_tag_ids = {tag.pk for tag in tags} if tags is not None else existing_ids
                if (log is None or log.status == DailyLog.Status.DRAFT) and not (
                    completed_work.strip() or next_day_plan.strip() or resulting_tag_ids
                ):
                    raise ValidationError({"completed_work": "空白日志不会保存；请先填写内容或关联标签。"})
                if log is None:
                    log = DailyLog(author=request.user, department=request.user.department,
                                   team=request.user.team, log_date=day)
                for field in ("completed_work", "next_day_plan"):
                    if field in values:
                        setattr(log, field, values[field])
                if log.status == DailyLog.Status.SUBMITTED and not log.completed_work.strip():
                    raise ValidationError({"completed_work": "已提交日志的今日完成不能为空。"})
                log.save()
                if tags is not None:
                    desired = {tag.pk for tag in tags}
                    log.tag_links.exclude(tag_id__in=desired).delete()
                    for tag in tags:
                        if tag.pk not in existing_ids:
                            DailyLogTag.objects.create(daily_log=log, tag=tag)
        except IntegrityError as exc:
            raise ValidationError("日志或标签发生并发变更，请重试。") from exc
        return Response({**log_data(log, detail=True), "can_edit": True})


class LogSubmitView(BusinessAPIView):
    serializer_class = LogSavedSerializer

    @extend_schema(tags=["App 日志"], summary="提交本人近三天日志", request=None)
    def post(self, request, log_date):
        day = parse_date(log_date, "log_date")
        require_editable(day)
        with transaction.atomic():
            log = DailyLog.objects.select_for_update().filter(author=request.user, log_date=day).first()
            if log is None or not log.completed_work.strip():
                raise ValidationError({"completed_work": "提交前必须填写今日完成。"})
            if log.status != DailyLog.Status.SUBMITTED:
                log.status = DailyLog.Status.SUBMITTED
                log.submitted_at = timezone.now()
                log.save(update_fields=["status", "submitted_at", "updated_at"])
        return Response({**log_data(log, detail=True), "can_edit": True})


class LogViewerListView(BusinessAPIView):
    serializer_class = LogViewerSerializer
    pagination_class = StandardPagination
    @extend_schema(tags=["App 日志"], summary="查询授权的日志查看对象")
    def get(self, request):
        users = descendants(request.user)
        keyword = request.query_params.get("keyword", "").strip().casefold()
        if keyword:
            users = [u for u in users if any(keyword in (value or "").casefold() for value in (
                u.display_name,
                u.team.name if u.team else "",
                u.department.name if u.department else "",
            ))]
        users.sort(key=lambda u: (u.department_id or 0, u.team_id or 0, u.pk))
        return paginated_response(self, request, users, lambda rows: [{
            "id": u.pk, "display_name": u.display_name or u.username,
            "business_role": u.business_role, "department_name": u.department.name if u.department else None,
            "team_name": u.team.name if u.team else None,
        } for u in rows])
