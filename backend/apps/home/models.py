from django.conf import settings
from django.db import models
from django.db.models import F, Q


PERSONAL_DAILY_ITEM_COLORS = (
    "#C7DDFF",
    "#BDEAF2",
    "#BFEBDD",
    "#CFE6B4",
    "#E7ECB0",
    "#FFE59E",
    "#FFD0A3",
    "#FFBFB3",
    "#F1C1D8",
    "#D7C8F2",
)


class HomePersonalFocusState(models.Model):
    """个人重点工作的初始化标记：只在首次读取/保存时预填一次公司十大。"""

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="personal_focus_state",
    )
    initialized_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "home_personal_focus_state"


class HomePersonalFocusTask(models.Model):
    """个人十大重点工作：仅保存用户自选的 user-task 顺序，不复制任务字段。

    任务本身是权威数据；任务完成、取消或转派后由服务层清理对应行，因此这里用
    CASCADE 跟随任务删除，避免残留指向不存在任务的排序记录。
    """

    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="personal_focus_tasks")
    task = models.ForeignKey("tasks.Task", on_delete=models.CASCADE, related_name="personal_focus_entries")
    sort_order = models.PositiveIntegerField()
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "home_personal_focus_task"
        ordering = ["sort_order", "id"]
        constraints = [
            models.UniqueConstraint(fields=["user", "task"], name="unique_personal_focus_user_task"),
            models.UniqueConstraint(fields=["user", "sort_order"], name="unique_personal_focus_user_order"),
        ]
        indexes = [models.Index(fields=["user", "sort_order"])]


class HomePersonalDailyItem(models.Model):
    """A user's private, date-spanning memo shown in their personal calendar."""

    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="personal_daily_items",
    )
    title = models.CharField(max_length=200)
    start_date = models.DateField()
    end_date = models.DateField()
    description = models.TextField(blank=True, default="")
    color = models.CharField(
        max_length=7,
        choices=[(value, value) for value in PERSONAL_DAILY_ITEM_COLORS],
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "home_personaldailyitem"
        ordering = ["start_date", "end_date", "id"]
        constraints = [
            models.CheckConstraint(
                condition=Q(end_date__gte=F("start_date")),
                name="home_pdi_end_on_or_after_start",
            ),
            models.CheckConstraint(
                condition=Q(color__in=PERSONAL_DAILY_ITEM_COLORS),
                name="home_pdi_color_in_palette",
            ),
        ]
        indexes = [
            models.Index(fields=["owner", "start_date"], name="home_pdi_owner_start_idx"),
            models.Index(fields=["owner", "end_date"], name="home_pdi_owner_end_idx"),
        ]
