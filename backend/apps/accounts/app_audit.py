"""员工 App 操作审计：成功操作后的快照落库。

与 Web 管理员审计（``AdminAuditLog``）分开保存：本模块只记录 App 业务账号的成功登录与
成功状态变更。调用方应把写业务数据和写审计行放在同一个 ``transaction.atomic()`` 中，
审计写入失败时业务变更必须一起回滚，避免出现“业务成功但无审计记录”。

隐私约束：审计值受模块字段白名单约束；密码、完整身份证号、令牌和头像二进制永不写入，
这些字段最多只记录字段名。
"""

from datetime import date, datetime
from decimal import Decimal
from uuid import UUID

from .models import AppOperationAuditLog

Module = AppOperationAuditLog.Module

# 每个模块允许落库的变更字段白名单。白名单之外的字段只记录字段名，不保存值。
ALLOWED_VALUE_FIELDS = {
    Module.AUTH: frozenset(),
    Module.PROFILE: frozenset({"display_name", "username"}),
    Module.TASKS: frozenset({
        "title", "description", "required_materials", "association_note",
        "estimated_start_at", "due_at", "assignee_id", "tag_ids",
        "approval_status", "completion_status", "lifecycle_status", "decision",
        "approval_round", "cancellation_reason", "content", "comment",
        "completion_note", "materials_note", "review_status", "review_comment",
    }),
    Module.LOGS: frozenset({"log_date", "completed_work", "next_day_plan", "tag_ids", "status", "content"}),
    Module.TAGS: frozenset({"name", "color", "is_active"}),
}

# 任何情况下都不写入的值字段：命中这些关键字的字段只保留字段名。
SENSITIVE_FIELD_TOKENS = ("password", "id_number", "id_card", "token", "secret", "avatar")


def is_sensitive_field(name):
    lowered = str(name).lower()
    return any(token in lowered for token in SENSITIVE_FIELD_TOKENS)


def _json_safe(value):
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, UUID):
        return str(value)
    if isinstance(value, dict):
        return {key: _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [_json_safe(item) for item in value]
    return value


def _changed_keys(before, after):
    keys = list((before or {}).keys()) + list((after or {}).keys())
    changed = []
    for key in keys:
        if key in changed:
            continue
        before_value = (before or {}).get(key)
        after_value = (after or {}).get(key)
        if before_value != after_value:
            changed.append(key)
    return changed


def _sanitized_values(values, module):
    if not values:
        return None
    allowlist = ALLOWED_VALUE_FIELDS.get(module, frozenset())
    cleaned = {
        key: _json_safe(value)
        for key, value in values.items()
        if key in allowlist and not is_sensitive_field(key)
    }
    return cleaned or None


def actor_snapshot(actor):
    """操作时的姓名、业务职位与部门/团队快照。"""
    return {
        "actor_name_snapshot": (actor.display_name or actor.username or "")[:100],
        "actor_business_role_snapshot": actor.business_role or "",
        "actor_department_id_snapshot": actor.department_id,
        "actor_department_name_snapshot": actor.department.name if actor.department_id else "",
        "actor_team_id_snapshot": actor.team_id,
        "actor_team_name_snapshot": actor.team.name if actor.team_id else "",
    }


def record_app_operation(
    *,
    actor,
    module,
    action,
    object_type="",
    object_id="",
    object_label="",
    changed_fields=None,
    before=None,
    after=None,
    result=AppOperationAuditLog.Result.SUCCESS,
):
    """写入一条成功操作记录；由调用方的事务保证原子性。"""
    module = str(module)
    fields = _changed_keys(before, after) if changed_fields is None else list(changed_fields)
    fields = list(dict.fromkeys(str(field) for field in fields))
    return AppOperationAuditLog.objects.create(
        actor=actor,
        module=module,
        action=action,
        object_type=str(object_type)[:32],
        object_id=str(object_id)[:64],
        object_label_snapshot=str(object_label)[:200],
        result=result,
        changed_fields=fields,
        before_data=_sanitized_values(before, module),
        after_data=_sanitized_values(after, module),
        **actor_snapshot(actor),
    )
