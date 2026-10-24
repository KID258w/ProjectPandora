import hashlib
import hmac

from django.conf import settings
from django.db import migrations


def backfill_id_number_hash(apps, schema_editor):
    """为已有账号回填 id_number_hash，并统一为带密钥的 HMAC 值。"""
    User = apps.get_model("accounts", "User")
    key = settings.IDENTITY_NUMBER_ENCRYPTION_KEY.encode("ascii")
    for user in User.objects.all():
        if user.id_number:
            user.id_number_hash = hmac.new(key, user.id_number.encode("utf-8"), hashlib.sha256).hexdigest()
            user.save(update_fields=["id_number_hash"])


def reverse_backfill(apps, schema_editor):
    User = apps.get_model("accounts", "User")
    User.objects.update(id_number_hash=None)


class Migration(migrations.Migration):
    dependencies = [
        ("accounts", "0007_remove_user_valid_account_placement_and_more"),
    ]

    operations = [
        migrations.RunPython(backfill_id_number_hash, reverse_backfill),
    ]
