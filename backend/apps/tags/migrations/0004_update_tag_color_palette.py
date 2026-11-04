from django.db import migrations


OLD_PALETTE = (
    "#4C9A8A", "#6587C5", "#C58B5D", "#AA77B8", "#D07272",
    "#719B57", "#C3A248", "#5E9DB7", "#BA7891", "#7C88A3",
)
NEW_PALETTE = (
    "#C7DDFF", "#BDEAF2", "#BFEBDD", "#CFE6B4", "#E7ECB0",
    "#FFE59E", "#FFD0A3", "#FFBFB3", "#F1C1D8", "#D7C8F2",
)


def remap_colors(apps, palette_from, palette_to):
    Tag = apps.get_model("tags", "Tag")
    for old_color, new_color in zip(palette_from, palette_to):
        Tag.objects.filter(color=old_color).update(color=new_color)


def forwards(apps, schema_editor):
    remap_colors(apps, OLD_PALETTE, NEW_PALETTE)


def backwards(apps, schema_editor):
    remap_colors(apps, NEW_PALETTE, OLD_PALETTE)


class Migration(migrations.Migration):
    dependencies = [
        ("tags", "0003_tasktag_task_tasktag_unique_task_tag"),
    ]

    operations = [migrations.RunPython(forwards, backwards)]
