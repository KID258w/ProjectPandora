"""App 端公司重要事项只读接口：所有业务用户看到同一份已发布内容。"""

from drf_spectacular.utils import extend_schema
from rest_framework.exceptions import NotFound
from rest_framework.response import Response

from apps.core.api import BusinessAPIView, StandardPagination, paginated_response

from .models import CompanyImportantItem
from .serializers import AppItemDetailSerializer, AppItemSummarySerializer
from .services import SECONDARY_LIST_LIMIT, app_item_detail, app_item_summary, published_items

TAGS = ["公司重要事项"]


class AppCompanyItemListView(BusinessAPIView):
    serializer_class = AppItemSummarySerializer
    pagination_class = StandardPagination

    @extend_schema(
        tags=TAGS, summary="已发布事项列表", operation_id="app_company_items_list",
        description="最多 10 条，按管理员维护的顺序返回；草稿与已下线不返回。",
    )
    def get(self, request):
        queryset = published_items(SECONDARY_LIST_LIMIT)
        return paginated_response(self, request, queryset, lambda rows: [app_item_summary(row) for row in rows])


class AppCompanyItemDetailView(BusinessAPIView):
    serializer_class = AppItemDetailSerializer

    @extend_schema(
        tags=TAGS, summary="已发布事项详情", operation_id="app_company_items_detail",
        description="草稿或已下线事项对 App 用户按不存在处理。",
    )
    def get(self, request, pk):
        item = CompanyImportantItem.objects.prefetch_related("attachments").filter(
            pk=pk, status=CompanyImportantItem.Status.PUBLISHED,
        ).first()
        if item is None:
            raise NotFound("公司事项不存在。")
        return Response(app_item_detail(item))
