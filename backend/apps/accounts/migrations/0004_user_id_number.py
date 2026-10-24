import apps.accounts.fields
from django.db import migrations


class Migration(migrations.Migration):
    dependencies = [
        ("accounts", "0003_remove_user_one_founder_and_more"),
    ]

    operations = [
        migrations.AddField(
            model_name="user",
            name="id_number",
            field=apps.accounts.fields.EncryptedTextField(blank=True, null=True),
        ),
    ]
