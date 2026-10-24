import hashlib
import hmac
from collections import defaultdict

from django.conf import settings
from django.db import migrations


def backfill_id_number_hash(apps, schema_editor):
    """为已有账号回填 id_number_hash，并统一为带密钥的 HMAC 值。"""
    User = apps.get_model("accounts", "User")
    key = settings.IDENTITY_NUMBER_ENCRYPTION_KEY.encode("ascii")

    ids_by_hash = defaultdict(list)
    for user in User.objects.all().iterator():
        if not user.id_number:
            continue
        digest = hmac.new(key, user.id_number.encode("utf-8"), hashlib.sha256).hexdigest()
        ids_by_hash[digest].append(user.pk)
    duplicate_accounts = [account_ids for account_ids in ids_by_hash.values() if len(account_ids) > 1]
    if duplicate_accounts:
        details = "; ".join(", ".join(str(account_id) for account_id in account_ids) for account_ids in duplicate_accounts)
        raise RuntimeError(
            "Cannot backfill unique national ID hashes because duplicate IDs exist. "
            f"Resolve the accounts with these User IDs, then rerun migration 0008: {details}"
        )

    for user in User.objects.all().iterator():
        if user.id_number:
            user.id_number_hash = hmac.new(key, user.id_number.encode("utf-8"), hashlib.sha256).hexdigest()
            user.save(update_fields=["id_number_hash"])


def reverse_backfill(apps, schema_editor):
    """Keep derived hashes when reverting this data-only migration."""
    return None


class Migration(migrations.Migration):
    dependencies = [
        ("accounts", "0007_remove_user_valid_account_placement_and_more"),
    ]

    operations = [
        migrations.RunPython(backfill_id_number_hash, reverse_backfill),
    ]
