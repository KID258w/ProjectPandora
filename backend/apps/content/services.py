"""公司重要事项的业务规则：发布上限、必填校验、排序与附件约束。

规则集中在服务层，管理端与 App 端共用同一数据出口，避免两边校验不一致。
"""

from django.conf import settings
from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import ValidationError

from .models import CompanyImportantItem, CompanyItemAttachment

MAX_PUBLISHED_ITEMS = 10
HOME_PREVIEW_ITEMS = 5
SECONDARY_LIST_LIMIT = 10
REQUIRED_FOR_PUBLISH = ("title", "summary", "body")


def published_items(limit=None):
    """已发布事项，按管理员维护的顺序返回；草稿和已下线永远不进入结果。"""
    queryset = CompanyImportantItem.objects.filter(
        status=CompanyImportantItem.Status.PUBLISHED
    ).order_by("sort_order", "id")
    return queryset[:limit] if limit else queryset


def item_brief(item):
    return {
        "id": item.pk, "title": item.title, "summary": item.summary,
        "status": item.status, "sort_order": item.sort_order,
        "published_at": item.published_at,
    }


def item_saved(item):
    """管理端创建/编辑后的返回体：含正文与草稿状态，便于表单回填。"""
    return {
        "id": item.pk, "title": item.title, "summary": item.summary, "body": item.body,
        "status": item.status, "sort_order": item.sort_order,
        "created_at": item.created_at, "updated_at": item.updated_at,
    }


def item_detail(item):
    data = {
        "id": item.pk, "title": item.title, "summary": item.summary, "body": item.body,
        "status": item.status, "sort_order": item.sort_order,
        "attachments": [attachment_data(row) for row in item.attachments.all()],
        "published_at": item.published_at, "offline_at": item.offline_at,
        "offline_reason": item.offline_reason,
        "created_at": item.created_at, "updated_at": item.updated_at,
    }
    return data


def attachment_data(attachment):
    return {
        "id": attachment.pk,
        "company_item_id": attachment.item_id,
        "original_name": attachment.original_name,
        "mime_type": attachment.mime_type,
        "size_bytes": attachment.size_bytes,
        "url": attachment.file_key.url,
    }


def app_item_summary(item):
    """App 只读摘要：不含状态机字段，客户端无从判断草稿或排序内部值。"""
    return {
        "id": item.pk, "title": item.title, "summary": item.summary,
        "published_at": item.published_at,
    }


def app_item_detail(item):
    return {
        "id": item.pk, "title": item.title, "summary": item.summary, "body": item.body,
        "attachments": [attachment_data(row) for row in item.attachments.all()],
        "published_at": item.published_at,
    }


def missing_publish_fields(item):
    """返回仍为空白的必填字段名，供 400 响应精确指出缺什么。"""
    return {
        field: "发布前必须填写该字段。"
        for field in REQUIRED_FOR_PUBLISH
        if not (getattr(item, field) or "").strip()
    }


def publish_item(item, actor, sort_order=None):
    with transaction.atomic():
        # 锁定目标行与当前已发布行，使“最多 10 条”的计数在并发下仍然成立。
        # SQLite 不支持 SELECT ... FOR UPDATE（单写入者仍安全）；MySQL 下逐行加锁。
        locked = CompanyImportantItem.objects.select_for_update().get(pk=item.pk)
        published_rows = list(
            CompanyImportantItem.objects.select_for_update()
            .filter(status=CompanyImportantItem.Status.PUBLISHED)
            .values_list("pk", "sort_order")
        )
        if locked.status == CompanyImportantItem.Status.PUBLISHED:
            raise ValidationError({"status": "该事项已处于发布状态。"})
        missing = missing_publish_fields(locked)
        if missing:
            raise ValidationError(missing)
        if len(published_rows) >= MAX_PUBLISHED_ITEMS:
            raise ValidationError(
                f"已发布事项已达 {MAX_PUBLISHED_ITEMS} 条，请先下线一条后再发布。"
            )
        if sort_order is not None:
            locked.sort_order = sort_order
        elif locked.sort_order <= 0:
            locked.sort_order = max((order for _, order in published_rows), default=0) + 1
        locked.status = CompanyImportantItem.Status.PUBLISHED
        locked.publisher = actor
        locked.published_at = timezone.now()
        locked.offline_at = None
        locked.offline_reason = ""
        locked.save(update_fields=[
            "status", "sort_order", "publisher", "published_at", "offline_at", "offline_reason",
            "updated_at",
        ])
    return locked


def offline_item(item, reason=""):
    with transaction.atomic():
        locked = CompanyImportantItem.objects.select_for_update().get(pk=item.pk)
        if locked.status != CompanyImportantItem.Status.PUBLISHED:
            raise ValidationError({"status": "只有已发布事项可以下线。"})
        locked.status = CompanyImportantItem.Status.OFFLINE
        locked.offline_at = timezone.now()
        locked.offline_reason = reason
        locked.save(update_fields=["status", "offline_at", "offline_reason", "updated_at"])
    return locked


def reorder_items(item_ids):
    """按给定顺序重排；未列出的行保持在末尾并维持原有相对顺序。

    不使用唯一约束依赖：逐行写入新序号即可，序号始终是 1..n 的连续值。
    """
    ids = list(item_ids)
    if len(set(ids)) != len(ids):
        raise ValidationError({"item_ids": "事项 ID 不能重复。"})
    with transaction.atomic():
        items = {item.pk: item for item in CompanyImportantItem.objects.select_for_update().all()}
        missing = [pk for pk in ids if pk not in items]
        if missing:
            raise ValidationError({"item_ids": f"以下事项不存在：{missing}。"})
        ordered_pks = ids + [pk for pk in items if pk not in set(ids)]
        for index, pk in enumerate(ordered_pks, start=1):
            item = items[pk]
            if item.sort_order != index:
                item.sort_order = index
                item.save(update_fields=["sort_order", "updated_at"])
    return [{"id": pk, "sort_order": items[pk].sort_order} for pk in ids]


def validate_attachment(upload):
    """附件大小与类型白名单；不通过时给出可读字段错误。"""
    max_bytes = settings.COMPANY_ITEM_ATTACHMENT_MAX_BYTES
    if upload.size > max_bytes:
        raise ValidationError({"file": f"附件不能超过 {max_bytes // (1024 * 1024)} MB。"})
    mime_type = (getattr(upload, "content_type", "") or "").lower().split(";")[0].strip()
    if mime_type not in settings.COMPANY_ITEM_ATTACHMENT_MIME_TYPES:
        raise ValidationError({"file": f"不支持的文件类型：{mime_type or '未知'}。"})
    return mime_type


def create_attachment(item, upload):
    mime_type = validate_attachment(upload)
    return CompanyItemAttachment.objects.create(
        item=item,
        file_key=upload,
        original_name=(upload.name or "")[:255],
        mime_type=mime_type,
        size_bytes=upload.size,
    )
