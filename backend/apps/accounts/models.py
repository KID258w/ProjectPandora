from django.contrib.auth.models import AbstractUser
from django.db import models


class User(AbstractUser):
    """Project user base; role and organization fields will be added with their feature design."""

    display_name = models.CharField(max_length=100, blank=True)
