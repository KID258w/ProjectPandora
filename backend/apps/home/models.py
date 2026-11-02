from django.conf import settings
from django.db import models


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
