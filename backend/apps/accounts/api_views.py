from django.db import IntegrityError, transaction
from drf_spectacular.utils import OpenApiParameter, extend_schema, extend_schema_view
from rest_framework import generics, serializers
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

from apps.core.api import EnvelopeMixin, EnvelopeSchema, StandardPagination
from .api_serializers import (
    AdminRefreshSerializer, AdminTokenSerializer, AppRefreshSerializer, AppTokenSerializer,
    BusinessUserCreateResponseSerializer, BusinessUserCreateSerializer, DepartmentSerializer, TeamSerializer,
    UserSerializer,
)
from .authentication import IsAdministrator, IsBusinessUser, ScopedJWTAuthentication
from .models import Department, Team, User


class VersionedMixin(EnvelopeMixin):
    schema = EnvelopeSchema()
    authentication_classes = [ScopedJWTAuthentication]


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


@extend_schema(tags=["App 认证"], summary="当前业务用户信息")
class AppMeView(AdminMeView):
    permission_classes = [IsBusinessUser]


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


@extend_schema_view(
    get=extend_schema(tags=["业务账号管理"], summary="业务用户列表", responses=UserSerializer(many=True)),
    post=extend_schema(tags=["业务账号管理"], summary="录入业务用户", request=BusinessUserCreateSerializer, responses={201: BusinessUserCreateResponseSerializer}),
)
class UserListView(AdminListCreateView):
    queryset = User.objects.filter(account_type="business").select_related("department", "team", "manager").order_by("id")

    def get_serializer_class(self):
        return BusinessUserCreateSerializer if self.request.method == "POST" else UserSerializer

    def create(self, request, *args, **kwargs):
        try:
            with transaction.atomic():
                serializer = self.get_serializer(data=request.data)
                serializer.is_valid(raise_exception=True)
                user = serializer.save()
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
