from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("accounts", "0010_normalize_id_number_hash"),
    ]

    operations = [
        migrations.AddField(
            model_name="user",
            name="avatar_data",
            field=models.BinaryField(blank=True, editable=False, null=True),
        ),
        migrations.AddField(
            model_name="user",
            name="avatar_content_type",
            field=models.CharField(blank=True, editable=False, max_length=32),
        ),
        migrations.AddField(
            model_name="user",
            name="avatar_updated_at",
            field=models.DateTimeField(blank=True, editable=False, null=True),
        ),
    ]
