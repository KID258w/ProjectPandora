from collections.abc import Mapping
from secrets import token_urlsafe

from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers
from rest_framework.exceptions import AuthenticationFailed
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer, TokenRefreshSerializer
from rest_framework_simplejwt.tokens import RefreshToken

from .models import AdminAuditLog, Department, Team, User


class StrictInputMixin:
    def to_internal_value(self, data):
        if not isinstance(data, Mapping):
            return super().to_internal_value(data)
        allowed = {name for name, field in self.fields.items() if not field.read_only}
        unknown = set(data) - allowed
        if unknown:
            raise serializers.ValidationError({key: "不允许传入此字段。" for key in sorted(unknown)})
        return super().to_internal_value(data)


def resolve_placement(role, department, team, is_active, exclude_pk=None):
    """校验业务角色→组织归属形状、负责人唯一性，返回规范化后的 (manager, department, team)。

    规则与录入共用，保证 create/update 行为一致：创始人无组织；部门经理有部门无团队；
    团队长/员工有部门与团队；负责人名额只对启用账号唯一；停用组织不可作为候选。
    """
    if role == User.Role.FOUNDER:
        if department or team:
            raise serializers.ValidationError("创始人不能指定部门或团队。")
        if is_active and User.objects.filter(business_role=role, is_active=True).exclude(pk=exclude_pk).exists():
            raise serializers.ValidationError({"business_role": "已存在创始人。"})
        return None, None, None

    if department is None:
        raise serializers.ValidationError({"department_id": "该身份必须选择部门。"})
    if not department.is_active:
        raise serializers.ValidationError({"department_id": "该部门已停用，不能作为归属。"})

    if role == User.Role.DEPARTMENT_MANAGER:
        if team is not None:
            raise serializers.ValidationError({"team_id": "部门经理不能指定团队。"})
        if is_active and User.objects.filter(business_role=role, department=department, is_active=True).exclude(pk=exclude_pk).exists():
            raise serializers.ValidationError({"business_role": "该部门已存在经理。"})
        manager_query = {"business_role": User.Role.FOUNDER}
    else:
        if team is None or team.department_id != department.pk:
            raise serializers.ValidationError({"team_id": "请选择所选部门下的团队。"})
        if not team.is_active:
            raise serializers.ValidationError({"team_id": "该团队已停用，不能作为归属。"})
        if role == User.Role.TEAM_LEADER:
            if is_active and User.objects.filter(business_role=role, team=team, is_active=True).exclude(pk=exclude_pk).exists():
                raise serializers.ValidationError({"business_role": "该团队已存在团队长。"})
            manager_query = {"business_role": User.Role.DEPARTMENT_MANAGER, "department": department}
        else:
            manager_query = {"business_role": User.Role.TEAM_LEADER, "team": team, "department": department}

    manager = User.objects.filter(account_type=User.AccountType.BUSINESS, is_active=True, **manager_query).first()
    if manager is None:
        raise serializers.ValidationError({"business_role": "请先录入启用的直属上级。"})
    return manager, department, team


class DepartmentSerializer(StrictInputMixin, serializers.ModelSerializer):
    class Meta:
        model = Department
        fields = ["id", "name", "description", "is_active", "created_at"]
        read_only_fields = ["id", "is_active", "created_at"]


class DepartmentUpdateSerializer(StrictInputMixin, serializers.ModelSerializer):
    class Meta:
        model = Department
        fields = ["name", "description", "is_active"]
        validators = []

    def validate(self, attrs):
        if not attrs.get("is_active", self.instance.is_active):
            if Team.objects.filter(department=self.instance, is_active=True).exists():
                raise serializers.ValidationError({"is_active": "该部门仍有启用的下属团队，不能停用。"})
            if User.objects.filter(account_type=User.AccountType.BUSINESS, department=self.instance, is_active=True).exists():
                raise serializers.ValidationError({"is_active": "该部门仍有启用的成员，不能停用。"})
        return attrs


class DepartmentDetailSerializer(serializers.ModelSerializer):
    manager = serializers.SerializerMethodField()
    teams = serializers.SerializerMethodField()
    members = serializers.SerializerMethodField()

    class Meta:
        model = Department
        fields = ["id", "name", "description", "is_active", "created_at", "manager", "teams", "members"]

    @extend_schema_field({"type": "object", "properties": {"id": {"type": "integer"}, "display_name": {"type": "string"}}, "nullable": True})
    def get_manager(self, obj):
        manager = User.objects.filter(
            account_type=User.AccountType.BUSINESS, business_role=User.Role.DEPARTMENT_MANAGER,
            department=obj, is_active=True,
        ).first()
        return {"id": manager.id, "display_name": manager.display_name} if manager else None

    @extend_schema_field({"type": "array", "items": {"type": "object", "properties": {"id": {"type": "integer"}, "name": {"type": "string"}, "is_active": {"type": "boolean"}}}})
    def get_teams(self, obj):
        return [{"id": team.id, "name": team.name, "is_active": team.is_active} for team in obj.teams.all()]

    @extend_schema_field({"type": "array", "items": {"type": "object", "properties": {"id": {"type": "integer"}, "display_name": {"type": "string"}, "business_role": {"type": "string"}}}})
    def get_members(self, obj):
        return [
            {"id": user.id, "display_name": user.display_name, "business_role": user.business_role}
            for user in obj.users.filter(is_active=True)
        ]


class TeamSerializer(StrictInputMixin, serializers.ModelSerializer):
    department_id = serializers.PrimaryKeyRelatedField(source="department", queryset=Department.objects.filter(is_active=True))

    class Meta:
        model = Team
        fields = ["id", "name", "description", "department_id", "is_active", "created_at"]
        read_only_fields = ["id", "is_active", "created_at"]
        validators = []

    def validate(self, attrs):
        if Team.objects.filter(department=attrs["department"], name=attrs["name"]).exists():
            raise serializers.ValidationError({"name": "该部门已存在同名团队。"})
        return attrs


class TeamUpdateSerializer(StrictInputMixin, serializers.ModelSerializer):
    class Meta:
        model = Team
        fields = ["name", "description", "is_active"]
        validators = []

    def validate(self, attrs):
        if not attrs.get("is_active", self.instance.is_active):
            if User.objects.filter(account_type=User.AccountType.BUSINESS, team=self.instance, is_active=True).exists():
                raise serializers.ValidationError({"is_active": "该团队仍有启用的成员，不能停用。"})
        return attrs


class TeamDetailSerializer(serializers.ModelSerializer):
    department_id = serializers.IntegerField(read_only=True)
    department_name = serializers.CharField(source="department.name", read_only=True, default=None)
    leader = serializers.SerializerMethodField()
    members = serializers.SerializerMethodField()

    class Meta:
        model = Team
        fields = ["id", "name", "description", "is_active", "department_id", "department_name", "leader", "members", "created_at"]

    @extend_schema_field({"type": "object", "properties": {"id": {"type": "integer"}, "display_name": {"type": "string"}}, "nullable": True})
    def get_leader(self, obj):
        leader = User.objects.filter(
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            team=obj, is_active=True,
        ).first()
        return {"id": leader.id, "display_name": leader.display_name} if leader else None

    @extend_schema_field({"type": "array", "items": {"type": "object", "properties": {"id": {"type": "integer"}, "display_name": {"type": "string"}, "business_role": {"type": "string"}}}})
    def get_members(self, obj):
        return [
            {"id": user.id, "display_name": user.display_name, "business_role": user.business_role}
            for user in obj.users.filter(is_active=True)
        ]


class UserSerializer(serializers.ModelSerializer):
    department_id = serializers.IntegerField(read_only=True, allow_null=True)
    department_name = serializers.CharField(source="department.name", read_only=True, default=None)
    team_id = serializers.IntegerField(read_only=True, allow_null=True)
    team_name = serializers.CharField(source="team.name", read_only=True, default=None)
    manager_id = serializers.IntegerField(read_only=True, allow_null=True)
    manager_name = serializers.CharField(source="manager.display_name", read_only=True, default=None)
    id_number_masked = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = ["id", "username", "account_type", "business_role", "display_name", "department_id", "department_name", "team_id", "team_name", "manager_id", "manager_name", "is_active", "id_number_masked"]

    def get_id_number_masked(self, obj) -> str | None:
        value = obj.id_number
        if not value:
            return None
        if len(value) <= 4:
            return "*" * len(value)
        return "*" * (len(value) - 4) + value[-4:]


class UserUpdateSerializer(StrictInputMixin, serializers.ModelSerializer):
    business_role = serializers.ChoiceField(choices=User.Role.choices, required=False)
    department_id = serializers.PrimaryKeyRelatedField(
        source="department", queryset=Department.objects.filter(is_active=True), required=False, allow_null=True,
    )
    team_id = serializers.PrimaryKeyRelatedField(
        source="team", queryset=Team.objects.filter(is_active=True), required=False, allow_null=True,
    )

    class Meta:
        model = User
        fields = ["business_role", "department_id", "team_id", "is_active"]
        validators = []

    def validate(self, attrs):
        instance = self.instance
        role = attrs.get("business_role", instance.business_role)
        department = attrs.get("department", instance.department)
        team = attrs.get("team", instance.team)
        is_active = attrs.get("is_active", instance.is_active)
        # 调整角色时清空不适用组织；显式传入不兼容字段则报错。
        if role == User.Role.FOUNDER:
            if department or team:
                raise serializers.ValidationError("创始人不能指定部门或团队。")
            department, team = None, None
        elif role == User.Role.DEPARTMENT_MANAGER:
            if "team" in attrs and team is not None:
                raise serializers.ValidationError({"team_id": "部门经理不能指定团队。"})
            team = None
        manager, department, team = resolve_placement(role, department, team, is_active, exclude_pk=instance.pk)
        attrs["manager"] = manager
        attrs["department"] = department
        attrs["team"] = team
        return attrs

    def update(self, instance, validated_data):
        for field in ("business_role", "department", "team", "manager", "is_active"):
            if field in validated_data:
                setattr(instance, field, validated_data[field])
        instance.save()
        return instance


class AppMeUpdateSerializer(StrictInputMixin, serializers.ModelSerializer):
    username = serializers.CharField(max_length=150, trim_whitespace=True, required=False)
    display_name = serializers.CharField(max_length=100, required=False)
    id_number = serializers.CharField(max_length=64, trim_whitespace=True, required=False, allow_blank=True)

    class Meta:
        model = User
        fields = ["username", "display_name", "id_number"]
        validators = []

    def validate_username(self, value):
        if not value:
            raise serializers.ValidationError("手机号不能为空。")
        if User.objects.filter(username=value).exclude(pk=self.instance.pk).exists():
            raise serializers.ValidationError("该手机号已被使用。")
        return value

    def validate_display_name(self, value):
        if not value.strip():
            raise serializers.ValidationError("姓名不能为空。")
        return value.strip()

    def validate_id_number(self, value):
        if value and not value.strip():
            raise serializers.ValidationError("身份证号不能为空白。")
        value = value.strip()
        if value and User.objects.filter(id_number_hash=User._hash_id_number(value)).exclude(pk=self.instance.pk).exists():
            raise serializers.ValidationError("该身份证号已被使用。")
        return value


class AppPasswordChangeSerializer(StrictInputMixin, serializers.Serializer):
    current_password = serializers.CharField(write_only=True, trim_whitespace=False, max_length=128)
    new_password = serializers.CharField(write_only=True, trim_whitespace=False, max_length=128)
    new_password_confirm = serializers.CharField(write_only=True, trim_whitespace=False, max_length=128)

    def validate(self, attrs):
        user = self.context["request"].user
        if not user.check_password(attrs["current_password"]):
            raise serializers.ValidationError({"current_password": "当前密码不正确。"})
        if attrs["new_password"] != attrs["new_password_confirm"]:
            raise serializers.ValidationError({"new_password_confirm": "两次输入的新密码不一致。"})
        try:
            validate_password(attrs["new_password"], user)
        except DjangoValidationError as exc:
            raise serializers.ValidationError({"new_password": exc.messages}) from exc
        return attrs

    def save(self):
        user = self.context["request"].user
        user.set_password(self.validated_data["new_password"])
        user.save(update_fields=["password"])
        return user


class AuditLogSerializer(serializers.ModelSerializer):
    actor_id = serializers.IntegerField(read_only=True)
    actor_name = serializers.CharField(source="actor.display_name", read_only=True, default=None)

    class Meta:
        model = AdminAuditLog
        fields = ["id", "actor_id", "actor_name", "module", "action", "object_type", "object_id", "result", "before_data", "after_data", "created_at"]


class BusinessUserCreateResponseSerializer(UserSerializer):
    initial_password = serializers.CharField(read_only=True, max_length=128)

    class Meta(UserSerializer.Meta):
        fields = UserSerializer.Meta.fields + ["initial_password"]


class BusinessUserCreateSerializer(StrictInputMixin, serializers.ModelSerializer):
    id_number = serializers.CharField(write_only=True, trim_whitespace=True, max_length=64)
    display_name = serializers.CharField(max_length=100)
    business_role = serializers.ChoiceField(choices=User.Role.choices)
    department_id = serializers.PrimaryKeyRelatedField(source="department", queryset=Department.objects.filter(is_active=True), required=False, allow_null=True)
    team_id = serializers.PrimaryKeyRelatedField(source="team", queryset=Team.objects.filter(is_active=True), required=False, allow_null=True)

    class Meta:
        model = User
        fields = ["username", "id_number", "display_name", "business_role", "department_id", "team_id"]
        validators = []

    def validate(self, attrs):
        id_number = attrs.get("id_number")
        if id_number and User.objects.filter(id_number_hash=User._hash_id_number(id_number)).exists():
            raise serializers.ValidationError({"id_number": "该身份证号已被使用。"})
        role = attrs["business_role"]
        department, team = attrs.get("department"), attrs.get("team")
        manager, department, team = resolve_placement(role, department, team, is_active=True)
        attrs["manager"] = manager
        attrs["department"] = department
        attrs["team"] = team
        return attrs

    def create(self, validated_data):
        self.initial_password = token_urlsafe(12)
        return User.objects.create_user(
            account_type=User.AccountType.BUSINESS,
            is_active=True,
            password=self.initial_password,
            **validated_data,
        )


class ScopedTokenSerializer(TokenObtainPairSerializer):
    account_type = None

    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        token["account_type"] = user.account_type
        return token

    def validate(self, attrs):
        data = super().validate(attrs)
        if self.user.account_type != self.account_type:
            raise AuthenticationFailed("账号或密码错误，或账号不可用于此入口。")
        return data


class AdminTokenSerializer(ScopedTokenSerializer):
    account_type = User.AccountType.ADMIN


class AppTokenSerializer(ScopedTokenSerializer):
    account_type = User.AccountType.BUSINESS


class ScopedRefreshSerializer(TokenRefreshSerializer):
    account_type = None

    def validate(self, attrs):
        token = RefreshToken(attrs["refresh"])
        if token.get("account_type") != self.account_type:
            raise AuthenticationFailed("刷新令牌不属于此入口。")
        if not User.objects.filter(pk=token.get("user_id"), account_type=self.account_type, is_active=True).exists():
            raise AuthenticationFailed("账号已停用或令牌已失效。")
        return super().validate(attrs)


class AdminRefreshSerializer(ScopedRefreshSerializer):
    account_type = User.AccountType.ADMIN


class AppRefreshSerializer(ScopedRefreshSerializer):
    account_type = User.AccountType.BUSINESS
