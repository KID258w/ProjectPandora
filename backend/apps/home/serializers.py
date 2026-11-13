from rest_framework import serializers

from apps.accounts.api_serializers import StrictInputMixin
from .models import HomePersonalDailyItem, PERSONAL_DAILY_ITEM_COLORS


class PersonalFocusInputSerializer(StrictInputMixin, serializers.Serializer):
    task_ids = serializers.ListField(
        child=serializers.IntegerField(min_value=1), allow_empty=True,
    )


class HomeTaskRowSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    title = serializers.CharField()
    completion_status = serializers.CharField()
    due_at = serializers.DateTimeField()


class HomeCompanyItemRowSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    title = serializers.CharField()
    summary = serializers.CharField()
    published_at = serializers.DateTimeField(allow_null=True)
    sort_order = serializers.IntegerField()


class HomePersonalTaskRowSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    title = serializers.CharField()
    sort_order = serializers.IntegerField()
    completion_status = serializers.CharField()
    due_at = serializers.DateTimeField()


class HomePersonalDailyItemSummarySerializer(serializers.ModelSerializer):
    class Meta:
        model = HomePersonalDailyItem
        fields = ["id", "title"]


class HomeRecentLogRowSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    log_date = serializers.DateField()
    summary = serializers.CharField()


class HomePayloadSerializer(serializers.Serializer):
    company_items = HomeCompanyItemRowSerializer(many=True)
    company_top_tasks = HomeTaskRowSerializer(many=True)
    personal_focus_tasks = HomePersonalTaskRowSerializer(many=True)
    personal_daily_items = HomePersonalDailyItemSummarySerializer(many=True)
    recent_logs = HomeRecentLogRowSerializer(many=True)
    unread_notification_count = serializers.IntegerField()


class PersonalFocusTaskRowSerializer(serializers.Serializer):
    task_id = serializers.IntegerField()
    title = serializers.CharField()
    sort_order = serializers.IntegerField()
    completion_status = serializers.CharField()
    due_at = serializers.DateTimeField()


class PersonalFocusSerializer(serializers.Serializer):
    initialized = serializers.BooleanField()
    tasks = PersonalFocusTaskRowSerializer(many=True)


class PersonalFocusOrderRowSerializer(serializers.Serializer):
    task_id = serializers.IntegerField()
    sort_order = serializers.IntegerField()


class PersonalFocusSavedSerializer(serializers.Serializer):
    tasks = PersonalFocusOrderRowSerializer(many=True)


class PersonalDailyItemSerializer(StrictInputMixin, serializers.ModelSerializer):
    title = serializers.CharField(max_length=200, trim_whitespace=True)
    description = serializers.CharField(required=False, allow_blank=True, default="")
    color = serializers.ChoiceField(choices=PERSONAL_DAILY_ITEM_COLORS)

    class Meta:
        model = HomePersonalDailyItem
        fields = [
            "id", "title", "start_date", "end_date", "description", "color",
            "created_at", "updated_at",
        ]
        read_only_fields = ["id", "created_at", "updated_at"]

    def validate_title(self, value):
        if not value:
            raise serializers.ValidationError("标题不能为空。")
        return value

    def validate(self, attrs):
        start_date = attrs.get("start_date", getattr(self.instance, "start_date", None))
        end_date = attrs.get("end_date", getattr(self.instance, "end_date", None))
        if start_date is not None and end_date is not None and end_date < start_date:
            raise serializers.ValidationError({"end_date": "结束日期不能早于开始日期。"})
        return attrs

    def create(self, validated_data):
        return HomePersonalDailyItem.objects.create(owner=self.context["request"].user, **validated_data)


class PersonalDailyItemRangeSerializer(StrictInputMixin, serializers.Serializer):
    start_date = serializers.DateField()
    end_date = serializers.DateField()

    def validate(self, attrs):
        if attrs["end_date"] < attrs["start_date"]:
            raise serializers.ValidationError({"end_date": "结束日期不能早于开始日期。"})
        return attrs


class PersonalDailyItemMoveSerializer(StrictInputMixin, serializers.Serializer):
    target_start_date = serializers.DateField()


class PersonalDailyItemCalendarRowSerializer(serializers.ModelSerializer):
    class Meta:
        model = HomePersonalDailyItem
        fields = ["id", "title", "start_date", "end_date", "color"]


class PersonalCalendarDaySerializer(serializers.Serializer):
    date = serializers.DateField()
    day_type = serializers.ChoiceField(choices=("holiday", "rest_day", "makeup_workday"))
    holiday_name = serializers.CharField(allow_null=True)
    label = serializers.CharField()


class PersonalDailyItemListSerializer(serializers.Serializer):
    items = PersonalDailyItemCalendarRowSerializer(many=True)
    calendar_days = PersonalCalendarDaySerializer(many=True)


class PersonalDailyItemDeletedSerializer(serializers.Serializer):
    deleted = serializers.BooleanField()
    id = serializers.IntegerField()
