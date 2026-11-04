from rest_framework import serializers

from apps.accounts.api_serializers import StrictInputMixin


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


class HomeRecentLogRowSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    log_date = serializers.DateField()
    summary = serializers.CharField()


class HomePayloadSerializer(serializers.Serializer):
    company_items = HomeCompanyItemRowSerializer(many=True)
    company_top_tasks = HomeTaskRowSerializer(many=True)
    personal_focus_tasks = HomePersonalTaskRowSerializer(many=True)
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
