from django.urls import path

from .views import LogCalendarView, LogDetailView, LogListView, LogMineView, LogSubmitView, LogViewerListView

urlpatterns = [
    path("logs/calendar/", LogCalendarView.as_view()),
    path("logs/mine/<str:log_date>/submit/", LogSubmitView.as_view()),
    path("logs/mine/<str:log_date>/", LogMineView.as_view()),
    path("logs/<int:pk>/", LogDetailView.as_view()),
    path("logs/", LogListView.as_view()),
    path("log-viewers/", LogViewerListView.as_view()),
]
