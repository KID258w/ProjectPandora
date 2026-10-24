from collections.abc import Mapping
from secrets import token_urlsafe

from rest_framework import serializers
from rest_framework.exceptions import AuthenticationFailed
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer, TokenRefreshSerializer
from rest_framework_simplejwt.tokens import RefreshToken

from .models import Department, Team, User


class StrictInputMixin:
    def to_internal_value(self, data):
        if not isinstance(data, Mapping):
            return super().to_internal_value(data)
        allowed = {name for name, field in self.fields.items() if not field.read_only}
        unknown = set(data) - allowed
        if unknown:
            raise serializers.ValidationError({key: "不允许传入此字段。" for key in sorted(unknown)})
        return super().to_internal_value(data)


class DepartmentSerializer(StrictInputMixin, serializers.ModelSerializer):
    class Meta:
        model = Department
        fields = ["id", "name", "description", "created_at"]
        read_only_fields = ["id", "created_at"]


class TeamSerializer(StrictInputMixin, serializers.ModelSerializer):
    department_id = serializers.PrimaryKeyRelatedField(source="department", queryset=Department.objects.all())

    class Meta:
        model = Team
        fields = ["id", "name", "description", "department_id", "created_at"]
        read_only_fields = ["id", "created_at"]
        validators = []

    def validate(self, attrs):
        if Team.objects.filter(department=attrs["department"], name=attrs["name"]).exists():
            raise serializers.ValidationError({"name": "该部门已存在同名团队。"})
        return attrs


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


class BusinessUserCreateResponseSerializer(UserSerializer):
    initial_password = serializers.CharField(read_only=True, max_length=128)

    class Meta(UserSerializer.Meta):
        fields = UserSerializer.Meta.fields + ["initial_password"]


class BusinessUserCreateSerializer(StrictInputMixin, serializers.ModelSerializer):
    id_number = serializers.CharField(write_only=True, trim_whitespace=True, max_length=64)
    display_name = serializers.CharField(max_length=100)
    business_role = serializers.ChoiceField(choices=User.Role.choices)
    department_id = serializers.PrimaryKeyRelatedField(source="department", queryset=Department.objects.all(), required=False, allow_null=True)
    team_id = serializers.PrimaryKeyRelatedField(source="team", queryset=Team.objects.all(), required=False, allow_null=True)

    class Meta:
        model = User
        fields = ["username", "id_number", "display_name", "business_role", "department_id", "team_id"]
        validators = []

    def validate(self, attrs):
        role = attrs["business_role"]
        department, team = attrs.get("department"), attrs.get("team")
        is_active = True
        if role == User.Role.FOUNDER:
            if department or team:
                raise serializers.ValidationError("创始人不能指定部门或团队。")
            if is_active and User.objects.filter(business_role=role, is_active=True).exists():
                raise serializers.ValidationError({"business_role": "已存在创始人。"})
            manager = None
        else:
            if department is None:
                raise serializers.ValidationError({"department_id": "该身份必须选择部门。"})
            if role == User.Role.DEPARTMENT_MANAGER:
                if team is not None:
                    raise serializers.ValidationError({"team_id": "部门经理不能指定团队。"})
                if is_active and User.objects.filter(business_role=role, department=department, is_active=True).exists():
                    raise serializers.ValidationError({"business_role": "该部门已存在经理。"})
                manager_query = {"business_role": User.Role.FOUNDER}
            else:
                if team is None or team.department_id != department.pk:
                    raise serializers.ValidationError({"team_id": "请选择所选部门下的团队。"})
                if role == User.Role.TEAM_LEADER:
                    if is_active and User.objects.filter(business_role=role, team=team, is_active=True).exists():
                        raise serializers.ValidationError({"business_role": "该团队已存在团队长。"})
                    manager_query = {"business_role": User.Role.DEPARTMENT_MANAGER, "department": department}
                else:
                    manager_query = {"business_role": User.Role.TEAM_LEADER, "team": team, "department": department}
            manager = User.objects.filter(account_type=User.AccountType.BUSINESS, is_active=True, **manager_query).first()
            if manager is None:
                raise serializers.ValidationError({"business_role": "请先录入启用的直属上级。"})
        attrs["manager"] = manager
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
