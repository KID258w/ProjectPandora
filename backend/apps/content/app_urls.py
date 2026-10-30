from django.urls import path

from . import app_views

urlpatterns = [
    path("company-items/", app_views.AppCompanyItemListView.as_view()),
    path("company-items/<int:pk>/", app_views.AppCompanyItemDetailView.as_view()),
]
