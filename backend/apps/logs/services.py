from .models import DailyLog


def recent_submitted_logs(user, limit=5):
    """Single source for the home panel; drafts and other users never enter its result."""
    return DailyLog.objects.filter(
        author=user, status=DailyLog.Status.SUBMITTED,
    ).order_by("-log_date", "-pk")[:limit]
