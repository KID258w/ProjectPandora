from django.contrib.auth.models import AbstractUser
from django.contrib.auth.models import UserManager
from django.db import models
from django.db.models import Q


class Department(models.Model):
    name = models.CharField(max_length=100, unique=True)
    description = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["id"]


class Team(models.Model):
    name = models.CharField(max_length=100)
    description = models.TextField(blank=True)
    department = models.ForeignKey(Department, on_delete=models.PROTECT, related_name="teams")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["id"]
        constraints = [models.UniqueConstraint(fields=["department", "name"], name="unique_team_name_in_department")]


class PandoraUserManager(UserManager):
    def create_superuser(self, username, email=None, password=None, **extra_fields):
        extra_fields.setdefault("account_type", "admin")
        return super().create_superuser(username, email, password, **extra_fields)


class User(AbstractUser):
    class AccountType(models.TextChoices):
        ADMIN = "admin", "Web 管理员"
        BUSINESS = "business", "App 业务用户"

    class Role(models.TextChoices):
        FOUNDER = "founder", "创始人"
        DEPARTMENT_MANAGER = "department_manager", "部门经理"
        TEAM_LEADER = "team_leader", "团队长"
        EMPLOYEE = "employee", "员工"

    display_name = models.CharField(max_length=100, blank=True)
    account_type = models.CharField(max_length=16, choices=AccountType.choices, default=AccountType.BUSINESS)
    business_role = models.CharField(max_length=24, choices=Role.choices, blank=True, default="")
    department = models.ForeignKey(Department, null=True, blank=True, on_delete=models.PROTECT, related_name="users")
    team = models.ForeignKey(Team, null=True, blank=True, on_delete=models.PROTECT, related_name="users")
    manager = models.ForeignKey("self", null=True, blank=True, on_delete=models.PROTECT, related_name="direct_reports")

    objects = PandoraUserManager()

    class Meta:
        constraints = [
            models.CheckConstraint(condition=Q(account_type__in=["admin", "business"]), name="valid_account_type"),
            models.CheckConstraint(
                condition=(
                    Q(account_type="admin", business_role="", department__isnull=True, team__isnull=True, manager__isnull=True)
                    | Q(account_type="business", business_role="founder", department__isnull=True, team__isnull=True, manager__isnull=True)
                    | Q(account_type="business", business_role="department_manager", department__isnull=False, team__isnull=True, manager__isnull=False)
                    | Q(account_type="business", business_role__in=["team_leader", "employee"], department__isnull=False, team__isnull=False, manager__isnull=False)
                ),
                name="valid_account_placement",
            ),
            models.UniqueConstraint(fields=["business_role"], condition=Q(business_role="founder"), name="one_founder"),
            models.UniqueConstraint(fields=["department"], condition=Q(business_role="department_manager"), name="one_manager_per_department"),
            models.UniqueConstraint(fields=["team"], condition=Q(business_role="team_leader"), name="one_leader_per_team"),
        ]
