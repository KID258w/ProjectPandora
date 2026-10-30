from django.conf import settings
from django.db import models


def attachment_upload_to(instance, filename):
    """按年月归档附件，避免同名文件互相覆盖（存储层仍会自动追加后缀）。"""
    return f"company-items/{instance.item_id or 'new'}/{filename}"


class CompanyImportantItem(models.Model):
    """公司重要事项：管理员维护的公司级信息，不承载任务/验收等业务语义。"""

    class Status(models.TextChoices):
        DRAFT = "draft", "草稿"
        PUBLISHED = "published", "已发布"
        OFFLINE = "offline", "已下线"

    title = models.CharField(max_length=200)
    summary = models.CharField(max_length=300)
    body = models.TextField()
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.DRAFT)
    sort_order = models.PositiveIntegerField(default=0)
    publisher = models.ForeignKey(
        settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.PROTECT,
        related_name="published_company_items",
    )
    published_at = models.DateTimeField(null=True, blank=True)
    offline_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "content_company_item"
        ordering = ["sort_order", "id"]
        indexes = [models.Index(fields=["status", "sort_order"])]


class CompanyItemAttachment(models.Model):
    """事项附件：只存文件引用与元数据，不把二进制塞进关系表。"""

    item = models.ForeignKey(CompanyImportantItem, on_delete=models.CASCADE, related_name="attachments")
    file_key = models.FileField(upload_to=attachment_upload_to, max_length=255)
    original_name = models.CharField(max_length=255)
    mime_type = models.CharField(max_length=120)
    size_bytes = models.PositiveIntegerField()
    uploaded_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "content_company_item_attachment"
        ordering = ["uploaded_at", "id"]
