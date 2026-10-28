from django.db import IntegrityError, transaction
from drf_spectacular.utils import extend_schema
from rest_framework import serializers, status
from rest_framework.exceptions import APIException, PermissionDenied, ValidationError
from rest_framework.response import Response

from apps.accounts.api_serializers import StrictInputMixin
from apps.accounts.models import User
from apps.core.api import BusinessAPIView, StandardPagination, paginated_response

from .models import Tag
from .services import choose_color


class Conflict(APIException):
    status_code = 409
    default_detail = "启用标签名称已存在。"


class TagInputSerializer(StrictInputMixin, serializers.Serializer):
    name = serializers.CharField(max_length=100, allow_blank=False, trim_whitespace=True)


class TagOutputSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    name = serializers.CharField()
    color = serializers.CharField()
    is_active = serializers.BooleanField()
    created_at = serializers.DateTimeField()
    updated_at = serializers.DateTimeField()


def tag_data(tag, include_status=True):
    data = {"id": tag.pk, "name": tag.name, "color": tag.color}
    if include_status:
        data["is_active"] = tag.is_active
    data.update(created_at=tag.created_at, updated_at=tag.updated_at)
    return data


def founder_only(request):
    if request.user.business_role != User.Role.FOUNDER:
        raise PermissionDenied("仅创始人可管理标签。")


@extend_schema(tags=["App 标签"], summary="查询启用标签或创始人管理列表")
class TagListView(BusinessAPIView):
    serializer_class = TagOutputSerializer
    pagination_class = StandardPagination
    def get(self, request):
        include_inactive = request.query_params.get("include_inactive", "false").lower()
        if include_inactive not in ("true", "false"):
            raise ValidationError({"include_inactive": "应为 true 或 false。"})
        if include_inactive == "true":
            founder_only(request)
        queryset = Tag.objects.all() if include_inactive == "true" else Tag.objects.filter(is_active=True)
        keyword = request.query_params.get("keyword", "").strip()
        if keyword:
            queryset = queryset.filter(name__icontains=keyword)
        return paginated_response(self, request, queryset, lambda rows: [tag_data(row) for row in rows])

    @extend_schema(tags=["App 标签"], summary="创始人创建标签", request=TagInputSerializer)
    def post(self, request):
        founder_only(request)
        form = TagInputSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        try:
            with transaction.atomic():
                name = form.validated_data["name"]
                if Tag.objects.filter(name=name, is_active=True).exists():
                    raise Conflict()
                tag = Tag.objects.create(name=name, color=choose_color())
        except IntegrityError as exc:
            raise Conflict() from exc
        return Response(tag_data(tag), status=status.HTTP_201_CREATED)


class TagDetailView(BusinessAPIView):
    serializer_class = TagOutputSerializer
    @extend_schema(tags=["App 标签"], summary="创始人重命名标签", request=TagInputSerializer)
    def patch(self, request, pk):
        founder_only(request)
        form = TagInputSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        try:
            with transaction.atomic():
                tag = Tag.objects.select_for_update().filter(pk=pk, is_active=True).first()
                if tag is None:
                    raise ValidationError("标签不存在或已停用。")
                name = form.validated_data["name"]
                if Tag.objects.filter(name=name, is_active=True).exclude(pk=pk).exists():
                    raise Conflict()
                tag.name = name
                tag.save(update_fields=["name", "updated_at"])
        except IntegrityError as exc:
            raise Conflict() from exc
        return Response(tag_data(tag))

    @extend_schema(tags=["App 标签"], summary="创始人不可逆停用标签")
    def delete(self, request, pk):
        founder_only(request)
        with transaction.atomic():
            tag = Tag.objects.select_for_update().filter(pk=pk).first()
            if tag is None:
                raise ValidationError("标签不存在。")
            if tag.is_active:
                tag.is_active = False
                tag.save(update_fields=["is_active", "updated_at"])
        return Response({"id": tag.pk, "is_active": False})
