from rest_framework import serializers
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView
from drf_spectacular.utils import extend_schema


class HealthCheckSerializer(serializers.Serializer):
    status = serializers.CharField(label="状态", help_text="服务状态；正常时为 ok。")
    service = serializers.CharField(label="服务名称", help_text="当前后端服务标识。")


class HealthCheckView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    @extend_schema(
        summary="健康检查",
        description="检查 Pandora 后端是否正常运行。无需登录。",
        responses=HealthCheckSerializer,
        tags=["基础服务"],
    )
    def get(self, request):
        return Response({"status": "ok", "service": "pandora-backend"})
