from rest_framework.permissions import AllowAny
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView
from drf_spectacular.utils import OpenApiResponse, extend_schema

from .serializers import (
    LoginRequestSchema,
    PandoraTokenObtainPairSerializer,
    PandoraTokenRefreshSerializer,
    RefreshRequestSchema,
    RefreshResponseSchema,
    TokenPairResponseSchema,
)


class PandoraTokenObtainPairView(TokenObtainPairView):
    authentication_classes = []
    permission_classes = [AllowAny]
    serializer_class = PandoraTokenObtainPairSerializer

    @extend_schema(
        summary="账号登录并获取令牌",
        description="提交账号和密码。验证成功后返回访问令牌 access 与刷新令牌 refresh。",
        request=LoginRequestSchema,
        responses={
            200: TokenPairResponseSchema,
            401: OpenApiResponse(description="账号或密码错误，或账号当前不可用。"),
        },
        tags=["身份认证"],
    )
    def post(self, request, *args, **kwargs):
        return super().post(request, *args, **kwargs)


class PandoraTokenRefreshView(TokenRefreshView):
    authentication_classes = []
    permission_classes = [AllowAny]
    serializer_class = PandoraTokenRefreshSerializer

    @extend_schema(
        summary="刷新访问令牌",
        description="提交有效的 refresh 令牌以获取新的 access 令牌。",
        request=RefreshRequestSchema,
        responses={
            200: RefreshResponseSchema,
            401: OpenApiResponse(description="刷新令牌无效、过期或已失效。"),
        },
        tags=["身份认证"],
    )
    def post(self, request, *args, **kwargs):
        return super().post(request, *args, **kwargs)
