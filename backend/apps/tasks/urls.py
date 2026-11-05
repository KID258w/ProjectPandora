from django.urls import path

from . import views
from . import work_views

urlpatterns = [
    path("work-views/month/", work_views.MonthWorkView.as_view()),
    path("work-views/week/", work_views.WeekWorkView.as_view()),
    path("work-views/day/", work_views.DayWorkView.as_view()),
    path("tasks/subordinates/", views.SubordinateTaskListView.as_view()),
    path("task-approvals/inbox/", views.ApprovalInboxView.as_view()),
    path("task-approvals/outbox/", views.ApprovalOutboxView.as_view()),
    path("tasks/<int:pk>/history/", views.TaskHistoryView.as_view()),
    path("tasks/<int:pk>/resubmit/", views.TaskResubmitView.as_view()),
    path("tasks/<int:pk>/approval/", views.TaskApprovalView.as_view()),
    path("tasks/<int:pk>/updates/", views.TaskUpdateView.as_view()),
    path("tasks/<int:pk>/submissions/<int:submission_id>/review/", views.TaskReviewView.as_view()),
    path("tasks/<int:pk>/submissions/", views.TaskSubmissionView.as_view()),
    path("tasks/<int:pk>/cancel/", views.TaskCancelView.as_view()),
    path("tasks/<int:pk>/", views.TaskDetailView.as_view()),
    path("tasks/", views.TaskListView.as_view()),
    path("organization/tree/", views.OrganizationTreeView.as_view()),
    path("organization/members/<int:pk>/", views.OrganizationMemberProfileView.as_view()),
    path("organization/members/<int:pk>/avatar/", views.OrganizationMemberAvatarView.as_view()),
    path("organization/assignable-users/", views.AssignableUserListView.as_view()),
    path("organization/visible-subordinates/", views.VisibleSubordinateListView.as_view()),
    path("notifications/unread-count/", views.NotificationUnreadCountView.as_view()),
    path("notifications/mark-read/", views.NotificationMarkReadView.as_view()),
    path("notifications/", views.NotificationListView.as_view()),
]
