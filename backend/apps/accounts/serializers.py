from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer, TokenRefreshSerializer


class PandoraTokenObtainPairSerializer(TokenObtainPairSerializer):
    """JWT 登录序列化器，为接口文档补充中文字段说明。"""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        account_field = self.fields[self.username_field]
        account_field.label = "登录账号"
        account_field.help_text = "Django 用户名；当前骨架暂以 username 作为登录账号。"
        self.fields["password"].label = "登录密码"
        self.fields["password"].help_text = "该账号对应的密码。"


class PandoraTokenRefreshSerializer(TokenRefreshSerializer):
    """JWT 刷新序列化器，为接口文档补充中文字段说明。"""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields["refresh"].label = "刷新令牌"
        self.fields["refresh"].help_text = "登录时获得的 refresh 令牌。"
        self.fields["access"].label = "访问令牌"
        self.fields["access"].help_text = "刷新后返回的新 access 令牌。"


class LoginRequestSchema(serializers.Serializer):
    username = serializers.CharField(label="登录账号", help_text="当前使用 Django username。")
    password = serializers.CharField(
        label="登录密码",
        help_text="该账号对应的密码。",
        write_only=True,
        style={"input_type": "password"},
    )


class TokenPairResponseSchema(serializers.Serializer):
    access = serializers.CharField(label="访问令牌", help_text="用于访问需要登录的 API。")
    refresh = serializers.CharField(label="刷新令牌", help_text="用于申请新的访问令牌。")


class RefreshRequestSchema(serializers.Serializer):
    refresh = serializers.CharField(label="刷新令牌", help_text="登录时获得的 refresh 令牌。")


class RefreshResponseSchema(serializers.Serializer):
    access = serializers.CharField(label="访问令牌", help_text="刷新后返回的新 access 令牌。")
    refresh = serializers.CharField(
        label="刷新令牌",
        help_text="开启令牌轮换时返回的新 refresh 令牌。",
        required=False,
    )
