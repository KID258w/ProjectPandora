from rest_framework import serializers

from .models import Tag


def validate_tag_ids(ids, existing_ids=()):
    if len(ids) != len(set(ids)):
        raise serializers.ValidationError({"tag_ids": "标签 ID 不能重复。"})
    rows = list(Tag.objects.filter(pk__in=ids))
    by_id = {row.pk: row for row in rows}
    if any(tag_id not in by_id or (not by_id[tag_id].is_active and tag_id not in existing_ids) for tag_id in ids):
        raise serializers.ValidationError({"tag_ids": "标签不存在、已停用或不允许重新关联。"})
    return [by_id[tag_id] for tag_id in ids]
