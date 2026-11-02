"""Versioned API response contract. Health and documentation remain unchanged."""
from uuid import uuid4

from drf_spectacular.openapi import AutoSchema
from rest_framework.pagination import PageNumberPagination
from rest_framework import generics

from apps.accounts.authentication import IsAdministrator, IsBusinessUser, ScopedJWTAuthentication


class EnvelopeMixin:
    def finalize_response(self, request, response, *args, **kwargs):
        response = super().finalize_response(request, response, *args, **kwargs)
        response.data = {
            "code": response.status_code,
            "message": "success" if response.status_code < 400 else "请求失败",
            "data": response.data,
            "requestId": str(uuid4()),
        }
        return response


class EnvelopeSchema(AutoSchema):
    def _get_response_bodies(self, direction="response"):
        responses = super()._get_response_bodies(direction)
        errors = {"400": "参数或业务规则校验失败。", "401": "未登录或令牌失效。", "403": "账号无权访问此入口。"}
        for status, description in errors.items():
            responses.setdefault(status, {
                "description": description,
                "content": {"application/json": {"schema": {
                    "type": "object",
                    "required": ["code", "message", "data", "requestId"],
                    "properties": {
                        "code": {"type": "integer", "example": int(status)},
                        "message": {"type": "string"},
                        "data": {},
                        "requestId": {"type": "string", "format": "uuid"},
                    },
                }}},
            })
        if self.method == "GET" and getattr(self.view, "pagination_class", None):
            responses["404"] = {**responses["400"], "description": "分页页码不存在。"}
        return responses

    def _get_response_for_code(self, *args, **kwargs):
        response = super()._get_response_for_code(*args, **kwargs)
        for media in response.get("content", {}).values():
            media["schema"] = {
                "type": "object",
                "required": ["code", "message", "data", "requestId"],
                "properties": {
                    "code": {"type": "integer"},
                    "message": {"type": "string"},
                    "data": media["schema"],
                    "requestId": {"type": "string", "format": "uuid"},
                },
            }
        return response


class StandardPagination(PageNumberPagination):
    page_size = 20
    page_size_query_param = "page_size"
    max_page_size = 100


class BusinessAPIView(EnvelopeMixin, generics.GenericAPIView):
    schema = EnvelopeSchema()
    authentication_classes = [ScopedJWTAuthentication]
    permission_classes = [IsBusinessUser]


class AdministratorAPIView(EnvelopeMixin, generics.GenericAPIView):
    """管理端入口：仅 Web 管理账号可访问，与 App 业务账号完全隔离。"""

    schema = EnvelopeSchema()
    authentication_classes = [ScopedJWTAuthentication]
    permission_classes = [IsAdministrator]


def paginated_response(view, request, queryset, serializer):
    from rest_framework.response import Response

    paginator = StandardPagination()
    page = paginator.paginate_queryset(queryset, request, view=view)
    return paginator.get_paginated_response(serializer(page)) if page is not None else Response(serializer(queryset))
