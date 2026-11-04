from django import forms
from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as DjangoUserAdmin
from django.contrib.auth.forms import AdminUserCreationForm, UserChangeForm

from .models import User


def _validate_placement(cleaned_data):
    account_type = cleaned_data.get("account_type")
    business_role = cleaned_data.get("business_role")
    department = cleaned_data.get("department")
    team = cleaned_data.get("team")
    manager = cleaned_data.get("manager")

    if account_type == User.AccountType.ADMIN:
        if business_role or department or team or manager:
            raise forms.ValidationError("管理员账号不能设置业务角色、部门、团队或上级。")
        return

    if account_type != User.AccountType.BUSINESS:
        return

    if business_role == User.Role.FOUNDER:
        if department or team or manager:
            raise forms.ValidationError("创始人不能设置部门、团队或上级。")
    elif business_role == User.Role.DEPARTMENT_MANAGER:
        if not department:
            raise forms.ValidationError("部门经理必须选择部门。")
        if team:
            raise forms.ValidationError("部门经理不能选择团队。")
    elif business_role in (User.Role.TEAM_LEADER, User.Role.EMPLOYEE):
        if not department or not team:
            raise forms.ValidationError("团队长和员工必须选择部门、团队。")
    else:
        raise forms.ValidationError("业务用户必须选择有效的业务角色。")


class PandoraUserCreationForm(AdminUserCreationForm):
    class Meta(AdminUserCreationForm.Meta):
        model = User
        fields = ("username", "display_name", "account_type", "business_role", "department", "team", "manager")

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields["account_type"].initial = User.AccountType.ADMIN

    def clean(self):
        cleaned_data = super().clean()
        if self.errors:
            return cleaned_data
        _validate_placement(cleaned_data)
        return cleaned_data


class PandoraUserChangeForm(UserChangeForm):
    class Meta(UserChangeForm.Meta):
        model = User
        fields = "__all__"

    def clean(self):
        cleaned_data = super().clean()
        if self.errors:
            return cleaned_data
        _validate_placement(cleaned_data)
        return cleaned_data


@admin.register(User)
class PandoraUserAdmin(DjangoUserAdmin):
    form = PandoraUserChangeForm
    add_form = PandoraUserCreationForm
    fieldsets = DjangoUserAdmin.fieldsets + (
        ("Pandora", {"fields": ("display_name", "account_type", "business_role", "department", "team", "manager")}),
    )
    add_fieldsets = DjangoUserAdmin.add_fieldsets + (
        ("Pandora", {"fields": ("display_name", "account_type", "business_role", "department", "team", "manager")}),
    )
