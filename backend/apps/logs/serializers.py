from rest_framework import serializers


class TagBriefSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    name = serializers.CharField()
    color = serializers.CharField()


class AuthorBriefSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    display_name = serializers.CharField()


class LogListSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    author_id = serializers.IntegerField()
    author_name = serializers.CharField()
    log_date = serializers.DateField()
    status = serializers.CharField()
    completed_work = serializers.CharField()
    next_day_plan = serializers.CharField()
    submitted_at = serializers.DateTimeField(allow_null=True)
    updated_at = serializers.DateTimeField()


class LogDetailSerializer(LogListSerializer):
    author = AuthorBriefSerializer()
    department_id = serializers.IntegerField(allow_null=True)
    team_id = serializers.IntegerField(allow_null=True)
    created_at = serializers.DateTimeField()
    tags = TagBriefSerializer(many=True)


class LogSavedSerializer(LogDetailSerializer):
    can_edit = serializers.BooleanField()


class LogMineSerializer(serializers.Serializer):
    log_date = serializers.DateField()
    log = LogDetailSerializer(allow_null=True)
    status = serializers.CharField()
    can_edit = serializers.BooleanField()


class CalendarDaySerializer(serializers.Serializer):
    date = serializers.DateField()
    has_submitted_log = serializers.BooleanField()


class LogCalendarSerializer(serializers.Serializer):
    year = serializers.IntegerField()
    month = serializers.IntegerField()
    days = CalendarDaySerializer(many=True)


class LogViewerSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    display_name = serializers.CharField()
    business_role = serializers.CharField()
    department_name = serializers.CharField(allow_null=True)
    team_name = serializers.CharField(allow_null=True)
