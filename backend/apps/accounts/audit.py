"""管理操作审计：字段脱敏与落库。

审计记录只保存管理端变更摘要，绝不写入明文密码、完整身份证号或员工日志内容。
"""

from .models import AdminAuditLog


def _is_sensitive_key(key: str) -> bool:
    key_lower = key.lower()
    return any(token in key_lower for token in ("password", "id_number", "id_card", "token", "secret"))


def redact(value):
    """递归脱敏：敏感键替换为 ``***``，其余原样保留。"""
    if value is None:
        return None
    if isinstance(value, dict):
        return {key: ("***" if _is_sensitive_key(key) else redact(item)) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [redact(item) for item in value]
    return value


def record_audit(*, actor, module, action, object_type, object_id="", result="success", before=None, after=None):
    AdminAuditLog.objects.create(
        actor=actor,
        module=module,
        action=action,
        object_type=object_type,
        object_id=str(object_id),
        result=result,
        before_data=redact(before),
        after_data=redact(after),
    )
