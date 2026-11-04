import hashlib
import hmac
from collections import defaultdict

from django.conf import settings
from django.db import migrations


def normalize_id_number(value):
    return value.strip().upper() if value is not None else None


def normalize_existing_id_numbers(apps, schema_editor):
    User = apps.get_model("accounts", "User")
    key = settings.IDENTITY_NUMBER_ENCRYPTION_KEY.encode("ascii")

    ids_by_hash = defaultdict(list)
    for user in User.objects.all().iterator():
        normalized = normalize_id_number(user.id_number)
        if not normalized:
            continue
        digest = hmac.new(key, normalized.encode("utf-8"), hashlib.sha256).hexdigest()
        ids_by_hash[digest].append(user.pk)

    duplicate_accounts = [account_ids for account_ids in ids_by_hash.values() if len(account_ids) > 1]
    if duplicate_accounts:
        details = "; ".join(", ".join(str(account_id) for account_id in account_ids) for account_ids in duplicate_accounts)
        raise RuntimeError(
            "Cannot normalize national IDs because case-insensitive duplicates exist. "
            f"Resolve the accounts with these User IDs, then rerun migration 0010: {details}"
        )

    for user in User.objects.all().iterator():
        normalized = normalize_id_number(user.id_number)
        if normalized != user.id_number:
            user.id_number = normalized
        user.id_number_hash = (
            hmac.new(key, normalized.encode("utf-8"), hashlib.sha256).hexdigest()
            if normalized else None
        )
        user.save(update_fields=["id_number", "id_number_hash"])


class Migration(migrations.Migration):
    dependencies = [
        ("accounts", "0009_user_cannot_manage_self"),
    ]

    operations = [
        migrations.RunPython(normalize_existing_id_numbers, migrations.RunPython.noop),
    ]
