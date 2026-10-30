from django.urls import path

from . import views

urlpatterns = [
    path("admin/company-items/", views.CompanyItemListView.as_view()),
    path("admin/company-items/order/", views.CompanyItemOrderView.as_view()),
    path("admin/company-items/<int:pk>/", views.CompanyItemDetailView.as_view()),
    path("admin/company-items/<int:pk>/publish/", views.CompanyItemPublishView.as_view()),
    path("admin/company-items/<int:pk>/offline/", views.CompanyItemOfflineView.as_view()),
    path("admin/company-items/<int:pk>/attachments/", views.CompanyItemAttachmentView.as_view()),
    path(
        "admin/company-items/<int:pk>/attachments/<int:attachment_id>/",
        views.CompanyItemAttachmentDetailView.as_view(),
    ),
]
