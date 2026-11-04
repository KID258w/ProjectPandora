from django.db import migrations, models
import django.db.models.deletion
from django.utils import timezone


def resolve_legacy_task_notifications(apps, schema_editor):
    database = schema_editor.connection.alias
    TaskApprovalRecord = apps.get_model("tasks", "TaskApprovalRecord")
    TaskNotification = apps.get_model("tasks", "TaskNotification")
    TaskSubmission = apps.get_model("tasks", "TaskSubmission")
    resolved_at = timezone.now()

    for notification in TaskNotification.objects.using(database).filter(
        resolved_at__isnull=True
    ).select_related("task").iterator():
        task = notification.task
        should_resolve = False
        if notification.notification_type == "approval_required":
            pending_round = TaskApprovalRecord.objects.using(database).filter(
                task_id=task.pk, decision="pending"
            ).order_by("-round").values_list("round", flat=True).first()
            should_resolve = (
                task.lifecycle_status != "active"
                or task.approval_status != "pending"
                or pending_round is None
                or notification.event_key != f"approval:{task.pk}:{pending_round}"
            )
        elif notification.notification_type == "assignment_received":
            should_resolve = (
                task.lifecycle_status != "active"
                or task.approval_status != "approved"
                or task.completion_status != "unfinished"
                or task.assignee_id != notification.recipient_id
                or TaskSubmission.objects.using(database).filter(task_id=task.pk).exists()
            )
        if should_resolve:
            TaskNotification.objects.using(database).filter(pk=notification.pk).update(
                resolved_at=resolved_at
            )


class Migration(migrations.Migration):

    dependencies = [
        ("tasks", "0001_initial"),
    ]

    operations = [
        migrations.AddField(
            model_name="tasknotification",
            name="submission",
            field=models.ForeignKey(
                blank=True,
                null=True,
                on_delete=django.db.models.deletion.PROTECT,
                related_name="notifications",
                to="tasks.tasksubmission",
            ),
        ),
        migrations.AlterField(
            model_name="tasknotification",
            name="notification_type",
            field=models.CharField(
                choices=[
                    ("approval_required", "待审批"),
                    ("assignment_received", "新任务"),
                    ("task_cancelled", "任务已取消"),
                    ("submission_received", "待验收"),
                    ("submission_accepted", "验收通过"),
                    ("submission_returned", "结果退回"),
                ],
                max_length=24,
            ),
        ),
        migrations.AddField(
            model_name="tasknotification",
            name="resolved_at",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.RunPython(resolve_legacy_task_notifications, migrations.RunPython.noop),
    ]
