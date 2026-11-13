from django.urls import path

from .views import TagDetailView, TagListView

urlpatterns = [
    path("tags/", TagListView.as_view()),
    path("tags/<int:pk>/", TagDetailView.as_view()),
]
