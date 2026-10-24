from datetime import date

from django.db import IntegrityError, transaction
from django.db.models import Q
from drf_spectacular.utils import OpenApiParameter, extend_schema, extend_schema_view
from rest_framework import generics, serializers
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

from rest_framework.views import APIView

from apps.core.api import EnvelopeMixin, EnvelopeSchema, StandardPagination
from .api_serializers import (
    AdminRefreshSerializer, AdminTokenSerializer, AppPasswordChangeSerializer, AppRefreshSerializer,
    AppTokenSerializer, AppMeUpdateSerializer, AuditLogSerializer, BusinessUserCreateResponseSerializer,
    BusinessUserCreateSerializer, DepartmentDetailSerializer, DepartmentSerializer, DepartmentUpdateSerializer,
    TeamDetailSerializer, TeamSerializer, TeamUpdateSerializer, UserSerializer, UserUpdateSerializer,
)
from .audit import record_audit
from .authentication import IsAdministrator, IsBusinessUser, ScopedJWTAuthentication
from .models import AdminAuditLog, Department, Team, User


class VersionedMixin(EnvelopeMixin):
    schema = EnvelopeSchema()
    authentication_classes = [ScopedJWTAuthentication]


def _user_audit_summary(user):
    """用户审计摘要：仅组织/身份/启用状态，不含密码、身份证号。"""
    return {
        "username": user.username,
        "display_name": user.display_name,
        "business_role": user.business_role,
        "department_id": user.department_id,
        "team_id": user.team_id,
        "is_active": user.is_active,
    }


def _parse_date_param(value, name):
    try:
        return date.fromisoformat(value)
    except (ValueError, TypeError) as exc:
        raise serializers.ValidationError({name: "日期格式应为 YYYY-MM-DD。"}) from exc


@extend_schema(tags=["管理端认证"], summary="管理员登录")
class AdminLoginView(VersionedMixin, TokenObtainPairView):
    authentication_classes = []
    permission_classes = [AllowAny]
    serializer_class = AdminTokenSerializer


@extend_schema(tags=["App 认证"], summary="业务用户登录")
class AppLoginView(AdminLoginView):
    serializer_class = AppTokenSerializer


@extend_schema(tags=["管理端认证"], summary="刷新管理员令牌")
class AdminRefreshView(VersionedMixin, TokenRefreshView):
    authentication_classes = []
    permission_classes = [AllowAny]
    serializer_class = AdminRefreshSerializer


@extend_schema(tags=["App 认证"], summary="刷新业务用户令牌")
class AppRefreshView(AdminRefreshView):
    serializer_class = AppRefreshSerializer


@extend_schema(tags=["管理端认证"], summary="当前管理员信息")
class AdminMeView(VersionedMixin, generics.RetrieveAPIView):
    permission_classes = [IsAdministrator]
    serializer_class = UserSerializer

    def get_object(self):
        return self.request.user


class AdminListCreateView(VersionedMixin, generics.ListCreateAPIView):
    permission_classes = [IsAdministrator]
    pagination_class = StandardPagination

    def create(self, request, *args, **kwargs):
        # Uniqueness constraints also protect concurrent requests.
        try:
            with transaction.atomic():
                return super().create(request, *args, **kwargs)
        except IntegrityError as exc:
            raise serializers.ValidationError("账号、组织名称或负责人已存在，请刷新后重试。") from exc


@extend_schema_view(get=extend_schema(tags=["组织管理"], summary="部门列表"), post=extend_schema(tags=["组织管理"], summary="创建部门"))
class DepartmentListView(AdminListCreateView):
    queryset = Department.objects.all()
    serializer_class = DepartmentSerializer

    def perform_create(self, serializer):
        instance = serializer.save()
        record_audit(
            actor=self.request.user, module=AdminAuditLog.Module.DEPARTMENTS, action="create",
            object_type="department", object_id=instance.pk,
            after={"name": instance.name, "description": instance.description, "is_active": instance.is_active},
        )


@extend_schema_view(
    get=extend_schema(tags=["组织管理"], summary="团队列表", parameters=[OpenApiParameter("department_id", int)]),
    post=extend_schema(tags=["组织管理"], summary="创建团队"),
)
class TeamListView(AdminListCreateView):
    serializer_class = TeamSerializer
    queryset = Team.objects.all()

    def get_queryset(self):
        queryset = super().get_queryset()
        department_id = self.request.query_params.get("department_id")
        if department_id is not None:
            try:
                department_id = int(department_id)
                if department_id < 1:
                    raise ValueError
            except ValueError as exc:
                raise serializers.ValidationError({"department_id": "必须为正整数。"}) from exc
            queryset = queryset.filter(department_id=department_id)
        return queryset

    def perform_create(self, serializer):
        instance = serializer.save()
        record_audit(
            actor=self.request.user, module=AdminAuditLog.Module.TEAMS, action="create",
            object_type="team", object_id=instance.pk,
            after={"name": instance.name, "description": instance.description, "department_id": instance.department_id, "is_active": instance.is_active},
        )


@extend_schema_view(
    get=extend_schema(tags=["业务账号管理"], summary="业务用户列表", responses=UserSerializer(many=True)),
    post=extend_schema(tags=["业务账号管理"], summary="录入业务用户", request=BusinessUserCreateSerializer, responses={201: BusinessUserCreateResponseSerializer}),
)
class UserListView(AdminListCreateView):
    queryset = User.objects.filter(account_type="business").select_related("department", "team", "manager").order_by("id")

    def get_serializer_class(self):
        return BusinessUserCreateSerializer if self.request.method == "POST" else UserSerializer

    def get_queryset(self):
        queryset = super().get_queryset()
        keyword = self.request.query_params.get("keyword")
        business_role = self.request.query_params.get("business_role")
        department_id = self.request.query_params.get("department_id")
        team_id = self.request.query_params.get("team_id")
        is_active = self.request.query_params.get("is_active")
        if keyword:
            queryset = queryset.filter(Q(username__icontains=keyword) | Q(display_name__icontains=keyword))
        if business_role:
            queryset = queryset.filter(business_role=business_role)
        if department_id:
            queryset = queryset.filter(department_id=department_id)
        if team_id:
            queryset = queryset.filter(team_id=team_id)
        if is_active in ("true", "false"):
            queryset = queryset.filter(is_active=(is_active == "true"))
        return queryset

    def create(self, request, *args, **kwargs):
        try:
            with transaction.atomic():
                serializer = self.get_serializer(data=request.data)
                serializer.is_valid(raise_exception=True)
                user = serializer.save()
                record_audit(
                    actor=request.user, module=AdminAuditLog.Module.USERS, action="create",
                    object_type="user", object_id=user.pk, after=_user_audit_summary(user),
                )
                response_data = BusinessUserCreateResponseSerializer(user).data
                response_data["initial_password"] = serializer.initial_password
                return Response(
                    response_data,
                    status=201,
                    headers={
                        "Cache-Control": "no-store, no-cache, max-age=0, private",
                        "Pragma": "no-cache",
                        "Expires": "0",
                    },
                )
        except IntegrityError as exc:
            raise serializers.ValidationError("账号或负责人已存在，请刷新后重试。") from exc


@extend_schema_view(get=extend_schema(tags=["组织管理"], summary="部门详情"), patch=extend_schema(tags=["组织管理"], summary="更新部门"))
class DepartmentDetailView(VersionedMixin, generics.RetrieveUpdateAPIView):
    permission_classes = [IsAdministrator]
    queryset = Department.objects.all()

    def get_serializer_class(self):
        return DepartmentUpdateSerializer if self.request.method == "PATCH" else DepartmentDetailSerializer

    def perform_update(self, serializer):
        instance = serializer.instance
        before = {"name": instance.name, "description": instance.description, "is_active": instance.is_active}
        updated = serializer.save()
        record_audit(
            actor=self.request.user, module=AdminAuditLog.Module.DEPARTMENTS, action="update",
            object_type="department", object_id=updated.pk,
            before=before, after={"name": updated.name, "description": updated.description, "is_active": updated.is_active},
        )


@extend_schema_view(get=extend_schema(tags=["组织管理"], summary="团队详情"), patch=extend_schema(tags=["组织管理"], summary="更新团队"))
class TeamDetailView(VersionedMixin, generics.RetrieveUpdateAPIView):
    permission_classes = [IsAdministrator]
    queryset = Team.objects.all()

    def get_serializer_class(self):
        return TeamUpdateSerializer if self.request.method == "PATCH" else TeamDetailSerializer

    def perform_update(self, serializer):
        instance = serializer.instance
        before = {"name": instance.name, "description": instance.description, "is_active": instance.is_active}
        updated = serializer.save()
        record_audit(
            actor=self.request.user, module=AdminAuditLog.Module.TEAMS, action="update",
            object_type="team", object_id=updated.pk,
            before=before, after={"name": updated.name, "description": updated.description, "is_active": updated.is_active},
        )


@extend_schema_view(get=extend_schema(tags=["业务账号管理"], summary="员工详情"), patch=extend_schema(tags=["业务账号管理"], summary="调整员工身份/组织/启用状态"))
class UserDetailView(VersionedMixin, generics.RetrieveUpdateAPIView):
    permission_classes = [IsAdministrator]
    queryset = User.objects.filter(account_type="business").select_related("department", "team", "manager")

    def get_serializer_class(self):
        return UserUpdateSerializer if self.request.method == "PATCH" else UserSerializer

    def update(self, request, *args, **kwargs):
        partial = kwargs.pop("partial", True)
        instance = self.get_object()
        serializer = self.get_serializer(instance, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        self.perform_update(serializer)
        return Response(UserSerializer(instance).data)

    def perform_update(self, serializer):
        instance = serializer.instance
        before = _user_audit_summary(instance)
        updated = serializer.save()
        record_audit(
            actor=self.request.user, module=AdminAuditLog.Module.USERS, action="update",
            object_type="user", object_id=updated.pk,
            before=before, after=_user_audit_summary(updated),
        )


@extend_schema(tags=["管理概况"], summary="管理概况", responses={200: {"type": "object", "properties": {"active_users": {"type": "integer"}, "departments": {"type": "integer"}, "teams": {"type": "integer"}, "published_company_items": {"type": "integer"}}}})
class DashboardView(VersionedMixin, APIView):
    permission_classes = [IsAdministrator]

    def get(self, request):
        return Response({
            "active_users": User.objects.filter(account_type="business", is_active=True).count(),
            "departments": Department.objects.filter(is_active=True).count(),
            "teams": Team.objects.filter(is_active=True).count(),
            "published_company_items": 0,
        })


@extend_schema(tags=["管理审计"], summary="管理操作记录")
class AuditLogListView(VersionedMixin, generics.ListAPIView):
    permission_classes = [IsAdministrator]
    serializer_class = AuditLogSerializer
    pagination_class = StandardPagination
    queryset = AdminAuditLog.objects.select_related("actor").all()

    def get_queryset(self):
        queryset = super().get_queryset()
        start_date = self.request.query_params.get("start_date")
        end_date = self.request.query_params.get("end_date")
        actor_id = self.request.query_params.get("actor_id")
        module = self.request.query_params.get("module")
        if start_date:
            queryset = queryset.filter(created_at__date__gte=_parse_date_param(start_date, "start_date"))
        if end_date:
            queryset = queryset.filter(created_at__date__lte=_parse_date_param(end_date, "end_date"))
        if actor_id:
            queryset = queryset.filter(actor_id=actor_id)
        if module:
            queryset = queryset.filter(module=module)
        return queryset


@extend_schema_view(
    get=extend_schema(tags=["App 认证"], summary="当前业务用户信息"),
    patch=extend_schema(tags=["App 认证"], summary="修改本人资料", request=AppMeUpdateSerializer, responses=UserSerializer),
)
class AppMeView(VersionedMixin, generics.RetrieveUpdateAPIView):
    permission_classes = [IsBusinessUser]

    def get_serializer_class(self):
        return AppMeUpdateSerializer if self.request.method == "PATCH" else UserSerializer

    def get_object(self):
        return self.request.user

    def update(self, request, *args, **kwargs):
        partial = kwargs.pop("partial", True)
        instance = self.get_object()
        serializer = self.get_serializer(instance, data=request.data, partial=partial)
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            serializer.save()
        return Response(UserSerializer(instance).data)


@extend_schema(tags=["App 认证"], summary="修改本人密码")
class AppPasswordChangeView(VersionedMixin, generics.GenericAPIView):
    permission_classes = [IsBusinessUser]
    serializer_class = AppPasswordChangeSerializer

    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response({"password_changed": True})
