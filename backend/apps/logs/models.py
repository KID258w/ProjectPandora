from django.conf import settings
from django.db import models


class DailyLog(models.Model):
    class Status(models.TextChoices):
        DRAFT = "draft", "草稿"
        SUBMITTED = "submitted", "已提交"

    author = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name="daily_logs")
    department = models.ForeignKey("accounts.Department", null=True, blank=True, on_delete=models.PROTECT)
    team = models.ForeignKey("accounts.Team", null=True, blank=True, on_delete=models.PROTECT)
    log_date = models.DateField()
    completed_work = models.TextField(blank=True)
    next_day_plan = models.TextField(blank=True)
    status = models.CharField(max_length=12, choices=Status.choices, default=Status.DRAFT)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    submitted_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-log_date", "-id"]
        constraints = [models.UniqueConstraint(fields=["author", "log_date"], name="unique_daily_log_author_date")]
        indexes = [models.Index(fields=["department", "log_date"]), models.Index(fields=["team", "log_date"])]


class DailyLogTag(models.Model):
    daily_log = models.ForeignKey(DailyLog, on_delete=models.CASCADE, related_name="tag_links")
    tag = models.ForeignKey("tags.Tag", on_delete=models.PROTECT)
    added_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["added_at", "id"]
        constraints = [models.UniqueConstraint(fields=["daily_log", "tag"], name="unique_daily_log_tag")]
