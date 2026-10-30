"""App 首页四面板与个人十大重点工作接口。"""

from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework.response import Response

from apps.core.api import BusinessAPIView, StandardPagination, paginated_response

from .serializers import (HomePayloadSerializer, HomeTaskRowSerializer, PersonalFocusInputSerializer,
                          PersonalFocusSavedSerializer, PersonalFocusSerializer)
from .services import (COMPANY_TOP_LIMIT, company_top_tasks, home_payload, ordered_candidates,
                       personal_focus, save_personal_focus, task_panel_row)

TAGS = ["首页"]


def validated(serializer_class, data):
    form = serializer_class(data=data)
    form.is_valid(raise_exception=True)
    return form.validated_data


def focus_row(entry):
    task = entry.task
    return {
        "task_id": task.pk, "title": task.title, "sort_order": entry.sort_order,
        "completion_status": task.completion_status, "due_at": task.due_at,
    }


class HomeView(BusinessAPIView):
    serializer_class = HomePayloadSerializer

    @extend_schema(
        tags=TAGS, summary="首页四面板聚合",
        description="四个面板各最多返回 5 条摘要，并附带 Issue 4 的未读任务通知数。",
        responses=HomePayloadSerializer,
    )
    def get(self, request):
        return Response(home_payload(request.user))


class CompanyTopTasksView(BusinessAPIView):
    serializer_class = HomeTaskRowSerializer
    pagination_class = StandardPagination

    @extend_schema(
        tags=TAGS, summary="公司十大重要任务",
        description="按当前用户本人未完成任务生成，先按来源（他人派发优先）再按截止时间排序，最多 10 条。",
        responses=HomeTaskRowSerializer(many=True),
    )
    def get(self, request):
        rows = list(company_top_tasks(request.user, COMPANY_TOP_LIMIT))
        return paginated_response(self, request, rows, lambda page: [task_panel_row(row) for row in page])


class PersonalFocusView(BusinessAPIView):
    serializer_class = PersonalFocusSerializer

    @extend_schema(
        tags=TAGS, summary="读取个人十大重点工作",
        description="首次读取时按当时的公司十大预填最多 10 项；之后只清理失效项，不随公司十大自动重建。",
        responses=PersonalFocusSerializer,
    )
    def get(self, request):
        entries = personal_focus(request.user)
        return Response({"initialized": True, "tasks": [focus_row(entry) for entry in entries]})

    @extend_schema(
        tags=TAGS, summary="保存个人十大重点工作",
        description="整体替换个人重点顺序，最多 10 条；只能选择本人承担、已审批且未完成的任务。",
        request=PersonalFocusInputSerializer, responses=PersonalFocusSavedSerializer,
    )
    def put(self, request):
        values = validated(PersonalFocusInputSerializer, request.data)
        task_ids = save_personal_focus(request.user, values["task_ids"])
        return Response({"tasks": [
            {"task_id": task_id, "sort_order": index}
            for index, task_id in enumerate(task_ids, start=1)
        ]})


class PersonalFocusCandidatesView(BusinessAPIView):
    serializer_class = HomeTaskRowSerializer
    pagination_class = StandardPagination

    @extend_schema(
        tags=TAGS, summary="个人重点候选任务",
        description="本人承担、已审批且未完成的任务；不含仅由本人派发给下属的任务。",
        parameters=[OpenApiParameter("keyword", str, required=False, description="按任务标题模糊筛选。")],
        responses=HomeTaskRowSerializer(many=True),
    )
    def get(self, request):
        queryset = ordered_candidates(request.user)
        keyword = request.query_params.get("keyword", "").strip()
        if keyword:
            queryset = queryset.filter(title__icontains=keyword)
        return paginated_response(self, request, queryset, lambda page: [task_panel_row(row) for row in page])
