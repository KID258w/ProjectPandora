from rest_framework import serializers

from .models import Tag


def tag_name_snapshots(values):
    """Return ordered {id, name} snapshots, resolving legacy bare IDs when possible."""
    if not isinstance(values, (list, tuple)):
        return values

    entries = []
    missing_names = set()
    for value in values:
        if isinstance(value, dict):
            tag_id = value.get("id")
            name = value.get("name")
        else:
            tag_id = value
            name = None
        try:
            tag_id = int(tag_id)
        except (TypeError, ValueError):
            entries.append({"id": tag_id, "name": str(name or "未知标签")})
            continue
        if name is None or name == "":
            missing_names.add(tag_id)
        entries.append({"id": tag_id, "name": name})

    resolved_names = (
        dict(Tag.objects.filter(pk__in=missing_names).values_list("pk", "name"))
        if missing_names else {}
    )
    for entry in entries:
        if not entry["name"]:
            entry["name"] = resolved_names.get(entry["id"]) or "未知标签"
    return entries


def validate_tag_ids(ids, existing_ids=()):
    if len(ids) != len(set(ids)):
        raise serializers.ValidationError({"tag_ids": "标签 ID 不能重复。"})
    rows = list(Tag.objects.filter(pk__in=ids))
    by_id = {row.pk: row for row in rows}
    if any(tag_id not in by_id or (not by_id[tag_id].is_active and tag_id not in existing_ids) for tag_id in ids):
        raise serializers.ValidationError({"tag_ids": "标签不存在、已停用或不允许重新关联。"})
    return [by_id[tag_id] for tag_id in ids]
