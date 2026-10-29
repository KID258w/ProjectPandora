from rest_framework import serializers

from apps.accounts.api_serializers import StrictInputMixin
from apps.logs.serializers import TagBriefSerializer, AuthorBriefSerializer


class TaskCreateInputSerializer(StrictInputMixin, serializers.Serializer):
    assignee_id = serializers.IntegerField(min_value=1)
    title = serializers.CharField(max_length=200, allow_blank=False, trim_whitespace=True)
    description = serializers.CharField(allow_blank=False, trim_whitespace=True)
    required_materials = serializers.CharField(allow_blank=True, trim_whitespace=True, required=False)
    association_note = serializers.CharField(allow_blank=True, trim_whitespace=True, required=False)
    due_at = serializers.DateTimeField()
    tag_ids = serializers.ListField(child=serializers.IntegerField(min_value=1), required=False)


class TaskInputSerializer(StrictInputMixin, serializers.Serializer):
    assignee_id = serializers.IntegerField(min_value=1, required=False)
    title = serializers.CharField(max_length=200, allow_blank=False, trim_whitespace=True, required=False)
    description = serializers.CharField(allow_blank=False, trim_whitespace=True, required=False)
    required_materials = serializers.CharField(allow_blank=True, trim_whitespace=True, required=False)
    association_note = serializers.CharField(allow_blank=True, trim_whitespace=True, required=False)
    due_at = serializers.DateTimeField(required=False)
    tag_ids = serializers.ListField(child=serializers.IntegerField(min_value=1), required=False)


class DecisionInputSerializer(StrictInputMixin, serializers.Serializer):
    decision = serializers.ChoiceField(choices=["approved", "returned"])
    comment = serializers.CharField(allow_blank=True, trim_whitespace=True, required=False)

    def validate(self, attrs):
        if attrs["decision"] == "returned" and not attrs.get("comment", "").strip():
            raise serializers.ValidationError({"comment": "退回必须填写原因。"})
        return attrs


class ReviewInputSerializer(StrictInputMixin, serializers.Serializer):
    decision = serializers.ChoiceField(choices=["accepted", "returned"])
    comment = serializers.CharField(allow_blank=True, trim_whitespace=True, required=False)

    def validate(self, attrs):
        if attrs["decision"] == "returned" and not attrs.get("comment", "").strip():
            raise serializers.ValidationError({"comment": "退回必须填写原因。"})
        return attrs


class UpdateInputSerializer(StrictInputMixin, serializers.Serializer):
    content = serializers.CharField(allow_blank=False, trim_whitespace=True)


class SubmissionInputSerializer(StrictInputMixin, serializers.Serializer):
    completion_note = serializers.CharField(allow_blank=False, trim_whitespace=True)
    materials_note = serializers.CharField(allow_blank=True, trim_whitespace=True, required=False)


class CancelInputSerializer(StrictInputMixin, serializers.Serializer):
    reason = serializers.CharField(allow_blank=False, trim_whitespace=True)


class MarkReadInputSerializer(StrictInputMixin, serializers.Serializer):
    notification_ids = serializers.ListField(child=serializers.IntegerField(min_value=1), allow_empty=True)


class TaskSummarySerializer(serializers.Serializer):
    id = serializers.IntegerField()
    title = serializers.CharField()
    creator_id = serializers.IntegerField()
    assignee_id = serializers.IntegerField()
    creator_name = serializers.CharField()
    assignee_name = serializers.CharField()
    approval_status = serializers.CharField()
    completion_status = serializers.CharField()
    lifecycle_status = serializers.CharField()
    due_at = serializers.DateTimeField()


class TaskApprovalSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    round = serializers.IntegerField()
    approver = AuthorBriefSerializer()
    decision = serializers.CharField()
    comment = serializers.CharField()
    submitted_at = serializers.DateTimeField()
    decided_at = serializers.DateTimeField(allow_null=True)


class TaskUpdateSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    author = AuthorBriefSerializer()
    content = serializers.CharField()
    created_at = serializers.DateTimeField()


class TaskSubmissionSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    submitted_by = AuthorBriefSerializer()
    completion_note = serializers.CharField()
    materials_note = serializers.CharField()
    review_status = serializers.CharField()
    reviewed_by = AuthorBriefSerializer(allow_null=True)
    review_comment = serializers.CharField()
    submitted_at = serializers.DateTimeField()
    reviewed_at = serializers.DateTimeField(allow_null=True)


class TaskDetailSerializer(TaskSummarySerializer):
    creator = AuthorBriefSerializer()
    assignee = AuthorBriefSerializer()
    description = serializers.CharField()
    required_materials = serializers.CharField()
    association_note = serializers.CharField()
    creator_department_id = serializers.IntegerField(allow_null=True)
    assignee_department_id = serializers.IntegerField(allow_null=True)
    completed_at = serializers.DateTimeField(allow_null=True)
    cancelled_at = serializers.DateTimeField(allow_null=True)
    cancellation_reason = serializers.CharField()
    created_at = serializers.DateTimeField()
    updated_at = serializers.DateTimeField()
    tags = TagBriefSerializer(many=True)
    approval_history = TaskApprovalSerializer(many=True)
    updates = TaskUpdateSerializer(many=True)
    submissions = TaskSubmissionSerializer(many=True)


class QueueItemSerializer(serializers.Serializer):
    task_id = serializers.IntegerField()
    title = serializers.CharField()
    creator = AuthorBriefSerializer()
    assignee = AuthorBriefSerializer()
    approver = AuthorBriefSerializer()
    approval_status = serializers.CharField()
    submitted_at = serializers.DateTimeField()


class HistoryItemSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    change_type = serializers.CharField()
    actor = AuthorBriefSerializer()
    changes = serializers.JSONField()
    created_at = serializers.DateTimeField()


class NotificationSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    notification_type = serializers.CharField()
    approval_round = serializers.IntegerField(allow_null=True)
    approval_decision = serializers.CharField(allow_null=True)
    task_id = serializers.IntegerField()
    task_title = serializers.CharField()
    task_lifecycle_status = serializers.CharField()
    task_approval_status = serializers.CharField()
    task_completion_status = serializers.CharField()
    submission_id = serializers.IntegerField(allow_null=True)
    summary = serializers.CharField(allow_blank=True)
    is_read = serializers.BooleanField()
    resolved_at = serializers.DateTimeField(allow_null=True)
    created_at = serializers.DateTimeField()
    read_at = serializers.DateTimeField(allow_null=True)


class PersonSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    display_name = serializers.CharField()
    business_role = serializers.CharField()
    department_name = serializers.CharField(allow_null=True)
    team_name = serializers.CharField(allow_null=True)
