"""管理端公司重要事项接口：仅 Web 管理员可访问。

所有写操作都会写入管理员审计（module=company_items）；App 只读接口在 app_views.py。
"""

from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework.exceptions import NotFound, ValidationError
from rest_framework.response import Response

from apps.accounts.audit import record_audit
from apps.accounts.models import AdminAuditLog
from apps.core.api import AdministratorAPIView, StandardPagination, paginated_response

from .models import CompanyImportantItem
from .serializers import (
    AttachmentSerializer, AttachmentUploadInputSerializer, DeletedSerializer, ItemBriefSerializer,
    ItemCreateInputSerializer, ItemDetailSerializer, ItemSavedSerializer, ItemUpdateInputSerializer,
    OfflineInputSerializer, OfflineResultSerializer, OrderInputSerializer, OrderResultSerializer,
    PublishInputSerializer, PublishResultSerializer,
)
from .services import (attachment_data, create_attachment, item_brief, item_detail, item_saved,
                       offline_item, publish_item, reorder_items)

TAGS = ["公司重要事项"]
AUDIT_MODULE = AdminAuditLog.Module.COMPANY_ITEMS
AUDIT_OBJECT = "company_item"


def validated(serializer_class, data):
    form = serializer_class(data=data)
    form.is_valid(raise_exception=True)
    return form.validated_data


def audit_snapshot(item):
    return {
        "title": item.title, "summary": item.summary, "body": item.body,
        "status": item.status, "sort_order": item.sort_order,
    }


def require_item(pk):
    item = CompanyImportantItem.objects.filter(pk=pk).first()
    if item is None:
        raise NotFound("公司事项不存在。")
    return item


class CompanyItemListView(AdministratorAPIView):
    serializer_class = ItemBriefSerializer
    pagination_class = StandardPagination

    @extend_schema(
        tags=TAGS, summary="管理端事项列表",
        parameters=[
            OpenApiParameter("status", str, required=False, description="draft、published 或 offline；缺省返回全部。"),
            OpenApiParameter("keyword", str, required=False, description="按标题模糊筛选。"),
        ],
        responses=ItemBriefSerializer(many=True),
    )
    def get(self, request):
        queryset = CompanyImportantItem.objects.all()
        status = request.query_params.get("status")
        if status:
            if status not in CompanyImportantItem.Status.values:
                raise ValidationError({"status": "应为 draft、published 或 offline。"})
            queryset = queryset.filter(status=status)
        keyword = request.query_params.get("keyword", "").strip()
        if keyword:
            queryset = queryset.filter(title__icontains=keyword)
        return paginated_response(self, request, queryset, lambda rows: [item_brief(row) for row in rows])

    @extend_schema(tags=TAGS, summary="创建事项草稿", request=ItemCreateInputSerializer, responses=ItemSavedSerializer)
    def post(self, request):
        values = validated(ItemCreateInputSerializer, request.data)
        item = CompanyImportantItem.objects.create(
            status=CompanyImportantItem.Status.DRAFT, sort_order=0, **values,
        )
        record_audit(
            actor=request.user, module=AUDIT_MODULE, action="create", object_type=AUDIT_OBJECT,
            object_id=item.pk, after=audit_snapshot(item),
        )
        return Response(item_saved(item), status=201)


class CompanyItemDetailView(AdministratorAPIView):
    serializer_class = ItemDetailSerializer

    @extend_schema(tags=TAGS, summary="事项详情", responses=ItemDetailSerializer)
    def get(self, request, pk):
        return Response(item_detail(require_item(pk)))

    @extend_schema(tags=TAGS, summary="编辑事项", request=ItemUpdateInputSerializer, responses=ItemSavedSerializer)
    def patch(self, request, pk):
        item = require_item(pk)
        values = validated(ItemUpdateInputSerializer, request.data)
        if not values:
            raise ValidationError("请至少提交一个可编辑字段。")
        before = audit_snapshot(item)
        for field, value in values.items():
            setattr(item, field, value)
        item.save(update_fields=[*values.keys(), "updated_at"])
        record_audit(
            actor=request.user, module=AUDIT_MODULE, action="update", object_type=AUDIT_OBJECT,
            object_id=item.pk, before=before, after=audit_snapshot(item),
        )
        return Response(item_saved(item))


class CompanyItemPublishView(AdministratorAPIView):
    serializer_class = PublishResultSerializer

    @extend_schema(
        tags=TAGS, summary="发布事项",
        description="已发布事项最多 10 条；未填写排序时追加到末尾。",
        request=PublishInputSerializer, responses=PublishResultSerializer,
    )
    def post(self, request, pk):
        item = require_item(pk)
        values = validated(PublishInputSerializer, request.data)
        before = audit_snapshot(item)
        published = publish_item(item, request.user, values.get("sort_order"))
        record_audit(
            actor=request.user, module=AUDIT_MODULE, action="publish", object_type=AUDIT_OBJECT,
            object_id=published.pk, before=before, after=audit_snapshot(published),
        )
        return Response({
            "id": published.pk, "status": published.status,
            "published_at": published.published_at, "sort_order": published.sort_order,
        })


class CompanyItemOfflineView(AdministratorAPIView):
    serializer_class = OfflineResultSerializer

    @extend_schema(
        tags=TAGS, summary="下线事项",
        description="下线后 App 立即不可见；记录保留，可重新发布。",
        request=OfflineInputSerializer, responses=OfflineResultSerializer,
    )
    def post(self, request, pk):
        item = require_item(pk)
        values = validated(OfflineInputSerializer, request.data)
        before = audit_snapshot(item)
        offline = offline_item(item, values.get("reason", ""))
        after = audit_snapshot(offline)
        if values.get("reason"):
            after["reason"] = values["reason"]
        record_audit(
            actor=request.user, module=AUDIT_MODULE, action="offline", object_type=AUDIT_OBJECT,
            object_id=offline.pk, before=before, after=after,
        )
        return Response({
            "id": offline.pk, "status": offline.status, "offline_at": offline.offline_at,
        })


class CompanyItemOrderView(AdministratorAPIView):
    serializer_class = OrderResultSerializer

    @extend_schema(
        tags=TAGS, summary="批量保存事项顺序",
        description="按 item_ids 顺序写入 1..n；未列出的现存事项顺延到末尾并保持原有相对顺序。",
        request=OrderInputSerializer, responses=OrderResultSerializer,
    )
    def put(self, request):
        values = validated(OrderInputSerializer, request.data)
        items = reorder_items(values["item_ids"])
        record_audit(
            actor=request.user, module=AUDIT_MODULE, action="reorder", object_type=AUDIT_OBJECT,
            after={"item_ids": values["item_ids"]},
        )
        return Response({"updated": len(items), "items": items})


class CompanyItemAttachmentView(AdministratorAPIView):
    serializer_class = AttachmentSerializer

    @extend_schema(
        tags=TAGS, summary="上传事项附件",
        description="multipart/form-data，字段名 file；受大小与类型白名单限制。",
        request=AttachmentUploadInputSerializer, responses=AttachmentSerializer,
    )
    def post(self, request, pk):
        item = require_item(pk)
        upload = request.FILES.get("file")
        if upload is None:
            raise ValidationError({"file": "请上传文件。"})
        attachment = create_attachment(item, upload)
        record_audit(
            actor=request.user, module=AUDIT_MODULE, action="upload_attachment",
            object_type="company_item_attachment", object_id=attachment.pk,
            after={"company_item_id": item.pk, "original_name": attachment.original_name,
                   "mime_type": attachment.mime_type, "size_bytes": attachment.size_bytes},
        )
        return Response(attachment_data(attachment), status=201)


class CompanyItemAttachmentDetailView(AdministratorAPIView):
    serializer_class = DeletedSerializer

    @extend_schema(
        tags=TAGS, summary="删除事项附件",
        description="仅能删除当前事项下的附件；数据库记录删除成功后再清理文件。",
        responses=DeletedSerializer,
    )
    def delete(self, request, pk, attachment_id):
        item = require_item(pk)
        attachment = item.attachments.filter(pk=attachment_id).first()
        if attachment is None:
            raise NotFound("附件不存在。")
        storage, stored_name = attachment.file_key.storage, attachment.file_key.name
        metadata = {"company_item_id": item.pk, "original_name": attachment.original_name}
        attachment.delete()
        storage.delete(stored_name)
        record_audit(
            actor=request.user, module=AUDIT_MODULE, action="delete_attachment",
            object_type="company_item_attachment", object_id=attachment_id, before=metadata,
        )
        return Response({"deleted": True})
