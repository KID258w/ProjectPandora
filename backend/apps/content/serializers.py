from rest_framework import serializers

from apps.accounts.api_serializers import StrictInputMixin


class ItemCreateInputSerializer(StrictInputMixin, serializers.Serializer):
    title = serializers.CharField(max_length=200, allow_blank=False, trim_whitespace=True)
    summary = serializers.CharField(max_length=300, allow_blank=False, trim_whitespace=True)
    body = serializers.CharField(allow_blank=False, trim_whitespace=True)


class ItemUpdateInputSerializer(StrictInputMixin, serializers.Serializer):
    title = serializers.CharField(max_length=200, allow_blank=False, trim_whitespace=True, required=False)
    summary = serializers.CharField(max_length=300, allow_blank=False, trim_whitespace=True, required=False)
    body = serializers.CharField(allow_blank=False, trim_whitespace=True, required=False)


class PublishInputSerializer(StrictInputMixin, serializers.Serializer):
    sort_order = serializers.IntegerField(min_value=1, required=False)


class OfflineInputSerializer(StrictInputMixin, serializers.Serializer):
    reason = serializers.CharField(max_length=200, allow_blank=True, trim_whitespace=True, required=False)


class OrderInputSerializer(StrictInputMixin, serializers.Serializer):
    item_ids = serializers.ListField(child=serializers.IntegerField(min_value=1), allow_empty=False)


class AttachmentUploadInputSerializer(StrictInputMixin, serializers.Serializer):
    file = serializers.FileField()


class AttachmentSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    company_item_id = serializers.IntegerField()
    original_name = serializers.CharField()
    mime_type = serializers.CharField()
    size_bytes = serializers.IntegerField()
    url = serializers.CharField()


class ItemBriefSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    title = serializers.CharField()
    summary = serializers.CharField()
    status = serializers.CharField()
    sort_order = serializers.IntegerField()
    published_at = serializers.DateTimeField(allow_null=True)


class ItemDetailSerializer(ItemBriefSerializer):
    body = serializers.CharField()
    attachments = AttachmentSerializer(many=True)
    offline_at = serializers.DateTimeField(allow_null=True)
    offline_reason = serializers.CharField()
    created_at = serializers.DateTimeField()
    updated_at = serializers.DateTimeField()


class ItemSavedSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    title = serializers.CharField()
    summary = serializers.CharField()
    body = serializers.CharField()
    status = serializers.CharField()
    sort_order = serializers.IntegerField()
    created_at = serializers.DateTimeField()
    updated_at = serializers.DateTimeField()


class PublishResultSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    status = serializers.CharField()
    published_at = serializers.DateTimeField(allow_null=True)
    sort_order = serializers.IntegerField()


class OfflineResultSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    status = serializers.CharField()
    offline_at = serializers.DateTimeField(allow_null=True)
    offline_reason = serializers.CharField()


class OrderItemSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    sort_order = serializers.IntegerField()


class OrderResultSerializer(serializers.Serializer):
    updated = serializers.IntegerField()
    items = OrderItemSerializer(many=True)


class DeletedSerializer(serializers.Serializer):
    deleted = serializers.BooleanField()


class AppItemSummarySerializer(serializers.Serializer):
    id = serializers.IntegerField()
    title = serializers.CharField()
    summary = serializers.CharField()
    published_at = serializers.DateTimeField(allow_null=True)


class AppItemDetailSerializer(AppItemSummarySerializer):
    body = serializers.CharField()
    attachments = AttachmentSerializer(many=True)
