from django.urls import path

from . import views

urlpatterns = [
    path("home/", views.HomeView.as_view()),
    path("home/company-top-tasks/", views.CompanyTopTasksView.as_view()),
    path("home/personal-focus/candidates/", views.PersonalFocusCandidatesView.as_view()),
    path("home/personal-focus/", views.PersonalFocusView.as_view()),
]
