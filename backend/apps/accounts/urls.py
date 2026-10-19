from django.urls import path
from . import api_views as views

urlpatterns = [
    path("admin/auth/token/", views.AdminLoginView.as_view()),
    path("admin/auth/token/refresh/", views.AdminRefreshView.as_view()),
    path("admin/me/", views.AdminMeView.as_view()),
    path("admin/departments/", views.DepartmentListView.as_view()),
    path("admin/teams/", views.TeamListView.as_view()),
    path("admin/users/", views.UserListView.as_view()),
    path("app/auth/token/", views.AppLoginView.as_view()),
    path("app/auth/token/refresh/", views.AppRefreshView.as_view()),
    path("app/me/", views.AppMeView.as_view()),
]
