"""Shared server-side validation for employee profile writes."""

import re
from datetime import date
from unicodedata import normalize

from rest_framework import serializers

from .models import User


NAME_SEPARATORS = " ·・‧-‐‑'’"
ID_WEIGHTS = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
ID_CHECK_DIGITS = "10X98765432"


class EmployeeProfileFieldsMixin:
    def _other_users(self):
        users = User.objects.all()
        return users.exclude(pk=self.instance.pk) if self.instance is not None else users

    def validate_display_name(self, value):
        value = normalize("NFC", value)
        if (
            not 1 <= len(value) <= 50
            or not any(char.isalpha() for char in value)
            or not all(char.isalpha() or char in NAME_SEPARATORS for char in value)
        ):
            raise serializers.ValidationError("姓名须为 1–50 个字符，仅允许 Unicode 字母及常见姓名分隔符。")
        return value

    def validate_username(self, value):
        if re.fullmatch(r"[0-9]{11}", value) is None:
            raise serializers.ValidationError("手机号须为 11 位数字。")
        if self._other_users().filter(username=value).exists():
            raise serializers.ValidationError("该手机号已被使用。")
        return value

    def validate_id_number(self, value):
        value = User.normalize_id_number(value)
        if re.fullmatch(r"[0-9]{17}[0-9X]", value) is None:
            raise serializers.ValidationError("身份证号须为 18 位，末位为数字或 X。")
        try:
            date(int(value[6:10]), int(value[10:12]), int(value[12:14]))
        except ValueError as exc:
            raise serializers.ValidationError("身份证号中的出生日期无效。") from exc
        checksum = sum(int(digit) * weight for digit, weight in zip(value[:17], ID_WEIGHTS))
        if ID_CHECK_DIGITS[checksum % 11] != value[-1]:
            raise serializers.ValidationError("身份证号校验码不正确。")
        if self._other_users().filter(id_number_hash=User._hash_id_number(value)).exists():
            raise serializers.ValidationError("该身份证号已被使用。")
        return value
