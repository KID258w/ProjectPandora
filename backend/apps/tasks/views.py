from django.db import IntegrityError, transaction
from django.db.models import Q
from django.http import HttpResponse
from django.utils import timezone
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import OpenApiParameter, OpenApiResponse, extend_schema
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.accounts.app_audit import record_app_operation
from apps.accounts.authentication import IsBusinessUser, ScopedJWTAuthentication, has_complete_supervisor_chain
from apps.accounts.models import AppOperationAuditLog, Department, Team, User
from apps.core.api import BusinessAPIView, StandardPagination, paginated_response
from apps.logs.views import descendants, parse_date

from .models import (Task, TaskApprovalRecord, TaskChangeHistory, TaskNotification,
                     TaskSubmission, TaskUpdate)
from .serializers import (CancelInputSerializer, DecisionInputSerializer, HistoryItemSerializer,
                          MarkReadInputSerializer, NotificationSerializer, PersonSerializer,
                          OrganizationMemberProfileSerializer, OrganizationTreeSerializer,
                          QueueItemSerializer, ReviewInputSerializer, SubmissionInputSerializer,
                          TaskCreateInputSerializer,
                          TaskDetailSerializer, TaskInputSerializer, TaskSummarySerializer,
                          TaskSubmissionSerializer, TaskUpdateSerializer, UpdateInputSerializer)
from .services import (assignable_users, create_approval_round, create_task_notification,
                       json_value, require_assignee, require_creator, require_executable,
                       resolve_task_notifications, send_assignment, set_task_tags,
                       subordinate_ids, task_detail, task_summary, task_tags, user_brief,
                       validate_assignee, validated_task_tags, visible_task)


TASK_DATE_FILTER_PARAMETERS = [
    OpenApiParameter("estimated_start_from", OpenApiTypes.DATE, OpenApiParameter.QUERY,
                     description="预计开始日期起始日，含边界；启用后未设置预计开始时间的任务不匹配。"),
    OpenApiParameter("estimated_start_to", OpenApiTypes.DATE, OpenApiParameter.QUERY,
                     description="预计开始日期结束日，含边界；启用后未设置预计开始时间的任务不匹配。"),
    OpenApiParameter("due_from", OpenApiTypes.DATE, OpenApiParameter.QUERY,
                     description="截止日期起始日，含边界。"),
    OpenApiParameter("due_to", OpenApiTypes.DATE, OpenApiParameter.QUERY,
                     description="截止日期结束日，含边界。"),
]


def form_data(serializer_class, data):
    form = serializer_class(data=data)
    form.is_valid(raise_exception=True)
    return form.validated_data


def require_empty_body(request):
    if request.data:
        raise ValidationError("该操作不接受请求字段。")


def filtered_tasks(request, queryset):
    completion = request.query_params.get("completion_status", "all")
    lifecycle = request.query_params.get("lifecycle_status", "active")
    if completion not in ("all", Task.Completion.UNFINISHED, Task.Completion.COMPLETED):
        raise ValidationError({"completion_status": "无效的完成状态。"})
    if lifecycle not in ("all", Task.Lifecycle.ACTIVE, Task.Lifecycle.CANCELLED):
        raise ValidationError({"lifecycle_status": "无效的归档状态。"})
    if completion != "all":
        queryset = queryset.filter(completion_status=completion)
    if lifecycle != "all":
        queryset = queryset.filter(lifecycle_status=lifecycle)
    date_filters = (
        ("due_from", "due_at__date__gte"),
        ("due_to", "due_at__date__lte"),
        ("estimated_start_from", "estimated_start_at__date__gte"),
        ("estimated_start_to", "estimated_start_at__date__lte"),
    )
    parsed_dates = {
        key: parse_date(request.query_params[key], key)
        for key, _lookup in date_filters
        if request.query_params.get(key)
    }
    for start_key, end_key, label in (
        ("due_from", "due_to", "截止日期"),
        ("estimated_start_from", "estimated_start_to", "预计开始日期"),
    ):
        if start_key in parsed_dates and end_key in parsed_dates and parsed_dates[start_key] > parsed_dates[end_key]:
            raise ValidationError({end_key: f"{label}结束日期不能早于起始日期。"})
    for key, lookup in date_filters:
        if key in parsed_dates:
            queryset = queryset.filter(**{lookup: parsed_dates[key]})
    return queryset


def validate_task_schedule(estimated_start_at, due_at):
    if estimated_start_at is not None and due_at is not None and estimated_start_at > due_at:
        raise ValidationError({"estimated_start_at": "预计开始时间不能晚于截止时间。"})


class TaskListView(BusinessAPIView):
    serializer_class = TaskSummarySerializer
    pagination_class = StandardPagination

    @extend_schema(tags=["App 任务"], summary="查询本人已审批任务", operation_id="app_tasks_list",
                   parameters=TASK_DATE_FILTER_PARAMETERS)
    def get(self, request):
        scope = request.query_params.get("scope", "mine")
        if scope not in ("mine", "created", "all"):
            raise ValidationError({"scope": "应为 mine、created 或 all。"})
        queryset = Task.objects.filter(approval_status=Task.Approval.APPROVED).select_related("creator", "assignee")
        if scope == "mine":
            queryset = queryset.filter(assignee=request.user)
        elif scope == "created":
            queryset = queryset.filter(creator=request.user)
        else:
            queryset = queryset.filter(Q(creator=request.user) | Q(assignee=request.user))
        queryset = filtered_tasks(request, queryset)
        return paginated_response(self, request, queryset, lambda rows: [task_summary(row) for row in rows])

    @extend_schema(tags=["App 任务"], summary="创建任务并送直属上级审批", request=TaskCreateInputSerializer, responses=TaskDetailSerializer)
    def post(self, request):
        values = form_data(TaskCreateInputSerializer, request.data)
        validate_task_schedule(values.get("estimated_start_at"), values["due_at"])
        assignee = validate_assignee(request.user, values["assignee_id"])
        tags = validated_task_tags(None, values.get("tag_ids", []))
        try:
            with transaction.atomic():
                task = Task.objects.create(
                    creator=request.user, assignee=assignee,
                    title=values["title"], description=values["description"],
                    required_materials=values.get("required_materials", ""),
                    association_note=values.get("association_note", ""),
                    creator_department=request.user.department, assignee_department=assignee.department,
                    estimated_start_at=values.get("estimated_start_at"),
                    due_at=values["due_at"],
                    approval_status=(Task.Approval.APPROVED if request.user.business_role == User.Role.FOUNDER else Task.Approval.PENDING),
                )
                set_task_tags(task, tags)
                changes = {key: {"before": None, "after": json_value(value)} for key, value in values.items() if key != "tag_ids"}
                if tags:
                    changes["tag_ids"] = {"before": [], "after": [tag.pk for tag in tags]}
                TaskChangeHistory.objects.create(task=task, actor=request.user,
                    change_type=TaskChangeHistory.Change.CREATED, changes=changes)
                if task.approval_status == Task.Approval.PENDING:
                    create_approval_round(task)
                else:
                    send_assignment(task)
                record_app_operation(
                    actor=request.user, module=AppOperationAuditLog.Module.TASKS, action="create",
                    object_type="task", object_id=task.pk, object_label=task.title,
                    before={},
                    after={**values, "tag_ids": [tag.pk for tag in tags]},
                )
        except IntegrityError as exc:
            raise ValidationError("创建任务时发生并发冲突，请重试。") from exc
        return Response(task_detail(task), status=201)


class SubordinateTaskListView(BusinessAPIView):
    serializer_class = TaskSummarySerializer
    pagination_class = StandardPagination

    @extend_schema(tags=["App 任务"], summary="只读查询授权下属承担或创建的任务",
                   parameters=TASK_DATE_FILTER_PARAMETERS)
    def get(self, request):
        allowed = subordinate_ids(request.user)
        scope = request.query_params.get("task_scope", "assigned")
        if scope not in ("assigned", "created"):
            raise ValidationError({"task_scope": "应为 assigned 或 created。"})
        value = request.query_params.get("user_id")
        if value:
            try:
                target = int(value)
            except ValueError as exc:
                raise ValidationError({"user_id": "应为用户 ID。"}) from exc
            if target not in allowed:
                raise PermissionDenied("不能查看该人员的任务。")
            allowed = {target}
        lookup = "assignee_id__in" if scope == "assigned" else "creator_id__in"
        queryset = Task.objects.filter(approval_status=Task.Approval.APPROVED, **{lookup: allowed}).select_related("creator", "assignee")
        keyword = request.query_params.get("keyword", "").strip()
        if keyword:
            selected = User.objects.filter(pk__in=allowed).filter(Q(display_name__icontains=keyword) | Q(username__icontains=keyword)).values_list("pk", flat=True)
            queryset = queryset.filter(**{lookup: selected})
        return paginated_response(self, request, filtered_tasks(request, queryset), lambda rows: [task_summary(row) for row in rows])


def queue_item(task, approval):
    return {"task_id": task.pk, "title": task.title, "creator": user_brief(task.creator),
            "assignee": user_brief(task.assignee), "approver": user_brief(approval.approver),
            "approval_status": task.approval_status, "submitted_at": approval.created_at}


class ApprovalInboxView(BusinessAPIView):
    serializer_class = QueueItemSerializer
    pagination_class = StandardPagination

    @extend_schema(tags=["App 审批"], summary="查询待本人审批任务")
    def get(self, request):
        records = TaskApprovalRecord.objects.filter(approver=request.user, decision=TaskApprovalRecord.Decision.PENDING,
            task__approval_status=Task.Approval.PENDING, task__lifecycle_status=Task.Lifecycle.ACTIVE).select_related("task", "task__creator", "task__assignee", "approver").order_by("-created_at", "-pk")
        return paginated_response(self, request, records, lambda rows: [queue_item(row.task, row) for row in rows])


class ApprovalOutboxView(BusinessAPIView):
    serializer_class = QueueItemSerializer
    pagination_class = StandardPagination

    @extend_schema(tags=["App 审批"], summary="查询本人待审批或退回待修改任务")
    def get(self, request):
        tasks = Task.objects.filter(creator=request.user, approval_status__in=[Task.Approval.PENDING, Task.Approval.RETURNED],
            lifecycle_status=Task.Lifecycle.ACTIVE).select_related("creator", "assignee").order_by("-created_at", "-pk")
        return paginated_response(self, request, tasks, lambda rows: [queue_item(task, task.approval_records.order_by("-round").select_related("approver").first()) for task in rows])


class TaskDetailView(BusinessAPIView):
    serializer_class = TaskDetailSerializer

    @extend_schema(tags=["App 任务"], summary="任务详情、审批、进展与完成记录", operation_id="app_task_detail")
    def get(self, request, pk):
        return Response(task_detail(visible_task(request, pk)))

    @extend_schema(tags=["App 任务"], summary="创建人修改未完成任务", request=TaskInputSerializer, responses=TaskDetailSerializer)
    def patch(self, request, pk):
        values = form_data(TaskInputSerializer, request.data)
        with transaction.atomic():
            task = Task.objects.select_for_update().select_related("creator", "assignee").filter(pk=pk).first()
            if task is None:
                raise NotFound("任务不存在。")
            require_creator(task, request.user)
            validate_task_schedule(
                values.get("estimated_start_at", task.estimated_start_at),
                values.get("due_at", task.due_at),
            )
            if task.lifecycle_status != Task.Lifecycle.ACTIVE or task.completion_status != Task.Completion.UNFINISHED:
                raise ValidationError("已完成或取消的任务不能修改。")
            if task.submissions.filter(review_status=TaskSubmission.Review.WAITING).exists():
                raise ValidationError("待验收任务不能修改定义。")
            tags = validated_task_tags(task, values["tag_ids"]) if "tag_ids" in values else None
            changes = {}
            key_changed = False
            for field, value in values.items():
                if field == "tag_ids":
                    before = [row["id"] for row in task_tags(task)]
                    wanted = {tag.pk for tag in tags}
                    after = [tag_id for tag_id in before if tag_id in wanted]
                    after.extend(tag.pk for tag in tags if tag.pk not in before)
                    if before != after:
                        changes[field] = {"before": before, "after": after}
                    continue
                if field == "assignee_id":
                    if task.assignee_id != value:
                        assignee = validate_assignee(request.user, value)
                        changes[field] = {"before": task.assignee_id, "after": value}
                        task.assignee = assignee
                        task.assignee_department = assignee.department
                        key_changed = True
                elif getattr(task, field) != value:
                    changes[field] = {"before": json_value(getattr(task, field)), "after": json_value(value)}
                    setattr(task, field, value)
                    if field in ("title", "description", "estimated_start_at", "due_at", "required_materials"):
                        key_changed = True
            if changes:
                task.save()
                set_task_tags(task, tags)
                history = TaskChangeHistory.objects.create(task=task, actor=request.user,
                    change_type=TaskChangeHistory.Change.UPDATED, changes=changes)
                if task.approval_status == Task.Approval.RETURNED:
                    task.returned_edit_required = False
                    task.save(update_fields=["returned_edit_required", "updated_at"])
                elif task.approval_status == Task.Approval.APPROVED and key_changed:
                    resolve_task_notifications(task, [
                        TaskNotification.Type.ASSIGNMENT_RECEIVED,
                        TaskNotification.Type.SUBMISSION_RETURNED,
                    ])
                    if request.user.business_role != User.Role.FOUNDER:
                        task.approval_status = Task.Approval.PENDING
                        task.save(update_fields=["approval_status", "updated_at"])
                        create_approval_round(task)
                    elif "assignee_id" in changes:
                        send_assignment(task, history.pk)
                record_app_operation(
                    actor=request.user, module=AppOperationAuditLog.Module.TASKS, action="update",
                    object_type="task", object_id=task.pk, object_label=task.title,
                    changed_fields=list(changes),
                    before={field: row["before"] for field, row in changes.items()},
                    after={field: row["after"] for field, row in changes.items()},
                )
        return Response(task_detail(task))


class TaskHistoryView(BusinessAPIView):
    serializer_class = HistoryItemSerializer

    @extend_schema(tags=["App 任务"], summary="读取任务定义变更历史", responses=OpenApiTypes.OBJECT)
    def get(self, request, pk):
        task = visible_task(request, pk)
        return Response({"results": [{"id": row.pk, "change_type": row.change_type,
            "actor": user_brief(row.actor), "changes": row.changes, "created_at": row.created_at}
            for row in task.changes.select_related("actor")]})


class TaskResubmitView(BusinessAPIView):
    serializer_class = TaskDetailSerializer

    @extend_schema(tags=["App 审批"], summary="创建人修改后重新送审", request=None, responses=OpenApiTypes.OBJECT)
    def post(self, request, pk):
        require_empty_body(request)
        with transaction.atomic():
            task = Task.objects.select_for_update().select_related("creator", "assignee").filter(pk=pk).first()
            if task is None:
                raise NotFound("任务不存在。")
            require_creator(task, request.user)
            if task.lifecycle_status != Task.Lifecycle.ACTIVE or task.approval_status != Task.Approval.RETURNED:
                raise ValidationError("只有退回的活动任务可以重新送审。")
            if task.returned_edit_required:
                raise ValidationError("请先修改被退回的任务。")
            task.approval_status = Task.Approval.PENDING
            task.save(update_fields=["approval_status", "updated_at"])
            approval = create_approval_round(task)
            record_app_operation(
                actor=request.user, module=AppOperationAuditLog.Module.TASKS, action="resubmit",
                object_type="task", object_id=task.pk, object_label=task.title,
                before={"approval_status": Task.Approval.RETURNED},
                after={"approval_status": task.approval_status, "approval_round": approval.round},
            )
        return Response({"task_id": task.pk, "approval_status": task.approval_status,
                         "approval_round": approval.round, "approver_id": approval.approver_id,
                         "submitted_at": approval.created_at})


class TaskApprovalView(BusinessAPIView):
    serializer_class = TaskDetailSerializer

    @extend_schema(tags=["App 审批"], summary="直属上级审批或退回任务", request=DecisionInputSerializer, responses=OpenApiTypes.OBJECT)
    def post(self, request, pk):
        values = form_data(DecisionInputSerializer, request.data)
        with transaction.atomic():
            task = Task.objects.select_for_update().select_related("creator", "assignee").filter(pk=pk).first()
            if task is None:
                raise NotFound("任务不存在。")
            approval = task.approval_records.select_for_update().order_by("-round").first()
            if approval is None or approval.approver_id != request.user.pk:
                raise PermissionDenied("仅本轮直属上级可审批。")
            if task.lifecycle_status != Task.Lifecycle.ACTIVE or task.approval_status != Task.Approval.PENDING or approval.decision != TaskApprovalRecord.Decision.PENDING:
                raise ValidationError("任务当前不在待审批状态。")
            approval.decision = values["decision"]
            approval.comment = values.get("comment", "")
            approval.decided_at = timezone.now()
            approval.save(update_fields=["decision", "comment", "decided_at"])
            resolve_task_notifications(task, [TaskNotification.Type.APPROVAL_REQUIRED])
            task.approval_status = values["decision"]
            task.returned_edit_required = values["decision"] == Task.Approval.RETURNED
            task.save(update_fields=["approval_status", "returned_edit_required", "updated_at"])
            result_type = (TaskNotification.Type.APPROVAL_APPROVED
                           if approval.decision == TaskApprovalRecord.Decision.APPROVED
                           else TaskNotification.Type.APPROVAL_RETURNED)
            create_task_notification(
                task,
                task.creator,
                result_type,
                f"approval:{task.pk}:{approval.round}:result:{approval.decision}",
            )
            if approval.decision == TaskApprovalRecord.Decision.APPROVED:
                send_assignment(task, approval.pk)
            record_app_operation(
                actor=request.user, module=AppOperationAuditLog.Module.TASKS,
                action=("approve" if approval.decision == TaskApprovalRecord.Decision.APPROVED else "return"),
                object_type="task", object_id=task.pk, object_label=task.title,
                before={"approval_status": Task.Approval.PENDING},
                after={
                    "approval_status": task.approval_status,
                    "decision": approval.decision,
                    "comment": approval.comment,
                },
            )
        return Response({"task_id": task.pk, "approval_status": task.approval_status,
                         "decision": approval.decision, "decided_at": approval.decided_at})


class TaskUpdateView(BusinessAPIView):
    serializer_class = TaskUpdateSerializer

    @extend_schema(tags=["App 任务"], summary="负责人追加文字进展", request=UpdateInputSerializer, responses=OpenApiTypes.OBJECT)
    def post(self, request, pk):
        values = form_data(UpdateInputSerializer, request.data)
        with transaction.atomic():
            task = Task.objects.select_for_update().filter(pk=pk).first()
            if task is None:
                raise NotFound("任务不存在。")
            require_executable(task, request.user)
            row = TaskUpdate.objects.create(task=task, author=request.user, content=values["content"])
            record_app_operation(
                actor=request.user, module=AppOperationAuditLog.Module.TASKS, action="progress",
                object_type="task_update", object_id=row.pk, object_label=task.title,
                before={}, after={"content": row.content},
            )
        return Response({"id": row.pk, "task_id": task.pk, "author_id": request.user.pk,
                         "content": row.content, "created_at": row.created_at}, status=201)


class TaskSubmissionView(BusinessAPIView):
    serializer_class = TaskSubmissionSerializer

    @extend_schema(tags=["App 任务"], summary="负责人提交完成说明与成果", request=SubmissionInputSerializer, responses=OpenApiTypes.OBJECT)
    def post(self, request, pk):
        values = form_data(SubmissionInputSerializer, request.data)
        with transaction.atomic():
            task = Task.objects.select_for_update().filter(pk=pk).first()
            if task is None:
                raise NotFound("任务不存在。")
            require_executable(task, request.user)
            if task.required_materials.strip() and not values.get("materials_note", "").strip():
                raise ValidationError({"materials_note": "请说明已提交的材料或成果位置。"})
            if task.submissions.filter(review_status=TaskSubmission.Review.WAITING).exists():
                raise ValidationError("已有待验收的完成提交。")
            self_assigned = task.creator_id == task.assignee_id
            row = TaskSubmission.objects.create(task=task, submitted_by=request.user,
                completion_note=values["completion_note"], materials_note=values.get("materials_note", ""),
                review_status=(TaskSubmission.Review.SELF_COMPLETED if self_assigned else TaskSubmission.Review.WAITING))
            if self_assigned:
                task.completion_status = Task.Completion.COMPLETED
                task.completed_at = timezone.now()
                task.save(update_fields=["completion_status", "completed_at", "updated_at"])
            else:
                resolve_task_notifications(task, [TaskNotification.Type.ASSIGNMENT_RECEIVED])
                resolve_task_notifications(task, [TaskNotification.Type.SUBMISSION_RETURNED])
                create_task_notification(task, task.creator, TaskNotification.Type.SUBMISSION_RECEIVED,
                                         f"submission:{row.pk}:received", submission=row)
            record_app_operation(
                actor=request.user, module=AppOperationAuditLog.Module.TASKS, action="submit",
                object_type="task_submission", object_id=row.pk, object_label=task.title,
                before={},
                after={
                    "completion_note": row.completion_note,
                    "materials_note": row.materials_note,
                    "review_status": row.review_status,
                },
            )
        return Response({"id": row.pk, "task_id": task.pk, "review_status": row.review_status,
                         "completion_note": row.completion_note, "materials_note": row.materials_note,
                         "submitted_at": row.submitted_at}, status=201)


class TaskReviewView(BusinessAPIView):
    serializer_class = TaskSubmissionSerializer

    @extend_schema(tags=["App 任务"], summary="直接派发者验收或退回完成提交", request=ReviewInputSerializer, responses=OpenApiTypes.OBJECT)
    def post(self, request, pk, submission_id):
        values = form_data(ReviewInputSerializer, request.data)
        with transaction.atomic():
            task = Task.objects.select_for_update().filter(pk=pk).first()
            if task is None:
                raise NotFound("任务不存在。")
            require_creator(task, request.user)
            if task.lifecycle_status != Task.Lifecycle.ACTIVE or task.approval_status != Task.Approval.APPROVED or task.completion_status != Task.Completion.UNFINISHED:
                raise ValidationError("任务当前不可验收。")
            row = TaskSubmission.objects.select_for_update().filter(pk=submission_id, task=task).first()
            if row is None:
                raise NotFound("提交记录不存在。")
            if row.review_status != TaskSubmission.Review.WAITING:
                raise ValidationError("该提交已处理。")
            row.review_status = values["decision"]
            row.review_comment = values.get("comment", "")
            row.reviewed_by = request.user
            row.reviewed_at = timezone.now()
            row.save(update_fields=["review_status", "review_comment", "reviewed_by", "reviewed_at"])
            resolve_task_notifications(task, [TaskNotification.Type.SUBMISSION_RECEIVED], submission=row)
            if row.review_status == TaskSubmission.Review.ACCEPTED:
                create_task_notification(task, task.assignee, TaskNotification.Type.SUBMISSION_ACCEPTED,
                                         f"submission:{row.pk}:accepted", submission=row)
            else:
                create_task_notification(task, task.assignee, TaskNotification.Type.SUBMISSION_RETURNED,
                                         f"submission:{row.pk}:returned", submission=row)
            if row.review_status == TaskSubmission.Review.ACCEPTED:
                task.completion_status = Task.Completion.COMPLETED
                task.completed_at = row.reviewed_at
                task.save(update_fields=["completion_status", "completed_at", "updated_at"])
            record_app_operation(
                actor=request.user, module=AppOperationAuditLog.Module.TASKS,
                action=("accept" if row.review_status == TaskSubmission.Review.ACCEPTED else "return"),
                object_type="task_submission", object_id=row.pk, object_label=task.title,
                before={"review_status": TaskSubmission.Review.WAITING},
                after={
                    "review_status": row.review_status,
                    "review_comment": row.review_comment,
                    "completion_status": task.completion_status,
                },
            )
        return Response({"submission_id": row.pk, "review_status": row.review_status,
                         "reviewed_by_id": request.user.pk, "reviewed_at": row.reviewed_at,
                         "task_completion_status": task.completion_status, "completed_at": task.completed_at})


class TaskCancelView(BusinessAPIView):
    serializer_class = TaskDetailSerializer

    @extend_schema(tags=["App 任务"], summary="直接派发者取消并归档未完成任务", request=CancelInputSerializer, responses=OpenApiTypes.OBJECT)
    def post(self, request, pk):
        values = form_data(CancelInputSerializer, request.data)
        with transaction.atomic():
            task = Task.objects.select_for_update().select_related("creator", "assignee").filter(pk=pk).first()
            if task is None:
                raise NotFound("任务不存在。")
            require_creator(task, request.user)
            if task.lifecycle_status != Task.Lifecycle.ACTIVE or task.completion_status != Task.Completion.UNFINISHED:
                raise ValidationError("任务已完成或取消。")
            recipient = None
            if task.approval_status == Task.Approval.PENDING:
                pending_approval = task.approval_records.filter(
                    decision=TaskApprovalRecord.Decision.PENDING
                ).select_related("approver").order_by("-round").first()
                recipient = pending_approval.approver if pending_approval else None
            elif task.approval_status == Task.Approval.APPROVED and task.assignee_id != task.creator_id:
                recipient = task.assignee
            task.lifecycle_status = Task.Lifecycle.CANCELLED
            task.cancellation_reason = values["reason"]
            task.cancelled_at = timezone.now()
            task.save(update_fields=["lifecycle_status", "cancellation_reason", "cancelled_at", "updated_at"])
            record_app_operation(
                actor=request.user, module=AppOperationAuditLog.Module.TASKS, action="cancel",
                object_type="task", object_id=task.pk, object_label=task.title,
                before={"lifecycle_status": Task.Lifecycle.ACTIVE},
                after={
                    "lifecycle_status": task.lifecycle_status,
                    "cancellation_reason": task.cancellation_reason,
                },
            )
            resolve_task_notifications(task, [
                TaskNotification.Type.APPROVAL_REQUIRED,
                TaskNotification.Type.ASSIGNMENT_RECEIVED,
                TaskNotification.Type.SUBMISSION_RECEIVED,
                TaskNotification.Type.SUBMISSION_RETURNED,
            ])
            if recipient is not None and recipient.pk != request.user.pk:
                create_task_notification(task, recipient, TaskNotification.Type.TASK_CANCELLED,
                                         f"task-cancelled:{task.pk}:{recipient.pk}")
        return Response({"task_id": task.pk, "lifecycle_status": task.lifecycle_status,
                         "cancelled_at": task.cancelled_at})


def person_data(user):
    return {"id": user.pk, "display_name": user.display_name or user.username,
            "business_role": user.business_role,
            "department_name": user.department.name if user.department else None,
            "team_name": user.team.name if user.team else None}


def _organization_scope(user):
    # Every authenticated App user can browse the full company roster.
    # Work-record access is restricted separately by the existing APIs.
    return "company"


def _organization_people(user, scope):
    return list(User.objects.filter(
        account_type=User.AccountType.BUSINESS, is_active=True,
    ).select_related("department", "team", "manager").order_by("id"))


def _member_tree_node(user, children=None):
    return {
        "node_type": "person",
        "id": user.pk,
        "display_name": user.display_name or user.username,
        "business_role": user.business_role,
        "department_name": user.department.name if user.department else None,
        "team_name": user.team.name if user.team else None,
        "children": children or [],
    }


def _unit_tree_node(node_type, unit, children=None):
    return {"node_type": node_type, "id": unit.pk, "name": unit.name, "children": children or []}


def _build_organization_tree(user, scope, people, keyword=""):
    keyword = keyword.strip().casefold()
    labels = dict(User.Role.choices)
    if keyword:
        matched_people = [
            person for person in people
            if keyword in (person.display_name or person.username).casefold()
            or keyword in person.business_role.casefold()
            or keyword in labels.get(person.business_role, "").casefold()
        ]
        return {"scope": scope, "roots": [_member_tree_node(person) for person in matched_people]}

    by_department = {}
    by_team = {}
    for person in people:
        if person.department_id:
            by_department.setdefault(person.department_id, []).append(person)
        if person.team_id:
            by_team.setdefault(person.team_id, []).append(person)

    def keep_person(person, children=None):
        children = children or []
        return _member_tree_node(person, children)

    def team_node(team):
        members = by_team.get(team.pk, [])
        leader = next((person for person in members if person.business_role == User.Role.TEAM_LEADER), None)
        children = []
        if leader:
            leader_node = keep_person(leader)
            if leader_node:
                children.append(leader_node)
        children.extend(
            node for person in members if person.business_role == User.Role.EMPLOYEE
            if (node := keep_person(person)) is not None
        )
        return _unit_tree_node("team", team, children)

    def department_node(department):
        members = by_department.get(department.pk, [])
        children = []
        manager = next((person for person in members if person.business_role == User.Role.DEPARTMENT_MANAGER), None)
        if manager:
            manager_node = keep_person(manager)
            if manager_node:
                children.append(manager_node)
        teams = Team.objects.filter(department=department, is_active=True).order_by("id")
        children.extend(node for team in teams if (node := team_node(team)) is not None)
        return _unit_tree_node("department", department, children)

    if scope == "company":
        departments = Department.objects.filter(is_active=True).order_by("id")
        children = [node for department in departments if (node := department_node(department)) is not None]
        founder = next((person for person in people if person.business_role == User.Role.FOUNDER), None)
        founder_root = [_member_tree_node(founder)] if founder else []
        roots = founder_root + children
    elif scope == "department":
        roots = [node for node in [department_node(user.department)] if node is not None]
    else:
        roots = [node for node in [team_node(user.team)] if node is not None]
    return {"scope": scope, "roots": roots}


def _profile_data(person):
    return {
        "id": person.pk,
        "is_self": False,
        "display_name": person.display_name or person.username,
        "phone": person.username,
        "business_role": person.business_role,
        "department_name": person.department.name if person.department else None,
        "team_name": person.team.name if person.team else None,
    }


class OrganizationTreeView(BusinessAPIView):
    serializer_class = OrganizationTreeSerializer

    @extend_schema(tags=["App organization"], summary="Browse the company organization tree")
    def get(self, request):
        scope = _organization_scope(request.user)
        people = _organization_people(request.user, scope)
        keyword = request.query_params.get("keyword", "")
        return Response(_build_organization_tree(request.user, scope, people, keyword))


class OrganizationMemberProfileView(BusinessAPIView):
    serializer_class = OrganizationMemberProfileSerializer

    @extend_schema(tags=["App organization"], summary="Read a company member profile")
    def get(self, request, pk):
        scope = _organization_scope(request.user)
        allowed_ids = {person.pk for person in _organization_people(request.user, scope)}
        person = User.objects.select_related("department", "team", "manager").filter(
            pk=pk, account_type=User.AccountType.BUSINESS, is_active=True,
        ).first()
        if person is None or person.pk not in allowed_ids:
            raise NotFound("\u6210\u5458\u4e0d\u5b58\u5728\u6216\u5f53\u524d\u4e0d\u53ef\u67e5\u770b\u3002")
        data = _profile_data(person)
        data["is_self"] = person.pk == request.user.pk
        task_subordinate_ids = subordinate_ids(request.user)
        log_subordinate_ids = {member.pk for member in descendants(request.user)}
        data["can_view_tasks"] = person.pk != request.user.pk and person.pk in task_subordinate_ids
        data["can_view_logs"] = person.pk != request.user.pk and person.pk in log_subordinate_ids
        return Response(data)


@extend_schema(
    tags=["App organization"],
    summary="Read a company member avatar",
    responses={
        (200, "image/jpeg"): OpenApiResponse(response=OpenApiTypes.BINARY, description="JPEG avatar image"),
        (200, "image/png"): OpenApiResponse(response=OpenApiTypes.BINARY, description="PNG avatar image"),
        (200, "image/webp"): OpenApiResponse(response=OpenApiTypes.BINARY, description="WebP avatar image"),
        404: OpenApiResponse(description="Member or avatar not found"),
    },
)
class OrganizationMemberAvatarView(APIView):
    authentication_classes = [ScopedJWTAuthentication]
    permission_classes = [IsBusinessUser]

    def get(self, request, pk):
        person = User.objects.only("avatar_data", "avatar_content_type").filter(
            pk=pk, account_type=User.AccountType.BUSINESS, is_active=True,
        ).first()
        if person is None or not person.avatar_data:
            return HttpResponse(status=404)
        response = HttpResponse(bytes(person.avatar_data), content_type=person.avatar_content_type)
        response["Cache-Control"] = "private, no-store"
        response["X-Content-Type-Options"] = "nosniff"
        return response


class AssignableUserListView(BusinessAPIView):
    serializer_class = PersonSerializer
    pagination_class = StandardPagination

    @extend_schema(tags=["App 组织"], summary="查询可指派的任务负责人")
    def get(self, request):
        users = assignable_users(request.user)
        keyword = request.query_params.get("keyword", "").strip().casefold()
        if keyword:
            users = [u for u in users if any(keyword in (value or "").casefold() for value in (
                u.display_name, u.username, u.department.name if u.department else "", u.team.name if u.team else "",
            ))]
        return paginated_response(self, request, users, lambda rows: [person_data(u) for u in rows])


class VisibleSubordinateListView(BusinessAPIView):
    serializer_class = PersonSerializer
    pagination_class = StandardPagination

    @extend_schema(tags=["App 组织"], summary="查询可只读查看任务的下属")
    def get(self, request):
        users = [u for u in descendants(request.user) if has_complete_supervisor_chain(u)]
        department_id = request.query_params.get("department_id")
        if department_id:
            try:
                department_id = int(department_id)
            except ValueError as exc:
                raise ValidationError({"department_id": "应为部门 ID。"}) from exc
            users = [u for u in users if u.department_id == department_id]
        keyword = request.query_params.get("keyword", "").strip().casefold()
        if keyword:
            users = [u for u in users if keyword in (u.display_name or "").casefold()]
        users.sort(key=lambda u: u.pk)
        return paginated_response(self, request, users, lambda rows: [person_data(u) for u in rows])


def notification_data(row):
    summary = ""
    approval_round = None
    approval_decision = None
    if row.notification_type == TaskNotification.Type.TASK_CANCELLED:
        summary = row.task.cancellation_reason
    elif row.submission_id:
        if row.notification_type == TaskNotification.Type.SUBMISSION_RECEIVED:
            summary = row.submission.completion_note
        else:
            summary = row.submission.review_comment
    elif row.notification_type in (
        TaskNotification.Type.APPROVAL_REQUIRED,
        TaskNotification.Type.APPROVAL_APPROVED,
        TaskNotification.Type.APPROVAL_RETURNED,
    ):
        # Approval notifications describe one approval round. Resolve that round
        # from the event key so older notifications do not inherit the task's
        # current (possibly newer) approval status after a resubmission.
        prefix = f"approval:{row.task_id}:"
        if row.event_key.startswith(prefix):
            try:
                approval_round = int(row.event_key[len(prefix):].split(":", 1)[0])
            except ValueError:
                approval_round = None
        if approval_round is not None:
            approval = next((record for record in row.task.approval_records.all()
                             if record.round == approval_round), None)
            if approval is not None:
                approval_decision = approval.decision
                if row.notification_type in (
                    TaskNotification.Type.APPROVAL_APPROVED,
                    TaskNotification.Type.APPROVAL_RETURNED,
                ):
                    summary = approval.comment
    return {"id": row.pk, "notification_type": row.notification_type, "task_id": row.task_id,
            "approval_round": approval_round, "approval_decision": approval_decision,
            "task_title": row.task.title, "task_lifecycle_status": row.task.lifecycle_status,
            "task_approval_status": row.task.approval_status,
            "task_completion_status": row.task.completion_status,
            "submission_id": row.submission_id, "summary": summary,
            "is_read": row.is_read, "resolved_at": row.resolved_at,
            "created_at": row.created_at, "read_at": row.read_at}


class NotificationListView(BusinessAPIView):
    serializer_class = NotificationSerializer
    pagination_class = StandardPagination

    @extend_schema(tags=["App 通知"], summary="分页查询本人站内任务通知")
    def get(self, request):
        unread_only = request.query_params.get("unread_only", "false").lower()
        if unread_only not in ("true", "false"):
            raise ValidationError({"unread_only": "应为 true 或 false。"})
        rows = TaskNotification.objects.filter(recipient=request.user).select_related(
            "task", "submission"
        ).prefetch_related("task__approval_records")
        if unread_only == "true":
            rows = rows.filter(is_read=False, resolved_at__isnull=True)
        return paginated_response(self, request, rows, lambda page: [notification_data(row) for row in page])


class NotificationUnreadCountView(BusinessAPIView):
    serializer_class = NotificationSerializer

    @extend_schema(tags=["App 通知"], summary="查询本人未读任务通知数", responses=OpenApiTypes.OBJECT)
    def get(self, request):
        return Response({"unread_count": TaskNotification.objects.filter(
            recipient=request.user, is_read=False, resolved_at__isnull=True
        ).count()})


class NotificationMarkReadView(BusinessAPIView):
    serializer_class = NotificationSerializer

    @extend_schema(tags=["App 通知"], summary="仅标记明确列出的本人通知已读", request=MarkReadInputSerializer, responses=OpenApiTypes.OBJECT)
    def post(self, request):
        values = form_data(MarkReadInputSerializer, request.data)
        with transaction.atomic():
            count = TaskNotification.objects.filter(recipient=request.user, is_read=False,
                pk__in=values["notification_ids"]).update(is_read=True, read_at=timezone.now())
        return Response({"marked_read_count": count})
