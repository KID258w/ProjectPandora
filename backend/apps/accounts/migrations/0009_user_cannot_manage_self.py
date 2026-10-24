from django.db import migrations, models
from django.db.models import F, Q


def reject_existing_self_managers(apps, schema_editor):
    User = apps.get_model("accounts", "User")
    account_ids = list(
        User.objects.filter(manager_id=F("id")).values_list("id", flat=True)
    )
    if account_ids:
        rendered_ids = ", ".join(str(account_id) for account_id in account_ids)
        raise RuntimeError(
            "Cannot add user_cannot_manage_self while self-manager rows exist. "
            f"Resolve these User IDs, then rerun migration 0009: {rendered_ids}"
        )


class Migration(migrations.Migration):
    dependencies = [
        ("accounts", "0008_backfill_id_number_hash"),
    ]

    operations = [
        migrations.RunPython(reject_existing_self_managers, migrations.RunPython.noop),
        migrations.AddConstraint(
            model_name="user",
            constraint=models.CheckConstraint(
                condition=~Q(manager_id=F("id")),
                name="user_cannot_manage_self",
            ),
        ),
    ]
