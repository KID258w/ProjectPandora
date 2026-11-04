from django.db import models
from django.db.models import Q


class Tag(models.Model):
    name = models.CharField(max_length=100)
    color = models.CharField(max_length=7)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["id"]
        constraints = [models.UniqueConstraint(fields=["name"], condition=Q(is_active=True), name="unique_active_tag_name")]


PALETTE = (
    "#C7DDFF", "#BDEAF2", "#BFEBDD", "#CFE6B4", "#E7ECB0",
    "#FFE59E", "#FFD0A3", "#FFBFB3", "#F1C1D8", "#D7C8F2",
)


class TaskTag(models.Model):
    task = models.ForeignKey("tasks.Task", on_delete=models.CASCADE, related_name="tag_links")
    tag = models.ForeignKey(Tag, on_delete=models.PROTECT)
    added_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["added_at", "id"]
        constraints = [models.UniqueConstraint(fields=["task", "tag"], name="unique_task_tag")]
