from datetime import date

from django.test import SimpleTestCase

from .china_calendar import calendar_days_for_range


class MidtermMsyChinaCalendarTests(SimpleTestCase):
    """Issue #42: holiday calendar range boundaries and configured data."""

    def test_range_includes_both_holiday_endpoints(self):
        days = calendar_days_for_range(date(2026, 1, 1), date(2026, 1, 3))

        self.assertEqual([row["date"] for row in days], [date(2026, 1, day) for day in (1, 2, 3)])
        self.assertEqual(days[0]["day_type"], "holiday")
        self.assertTrue(all(row["day_type"] in {"holiday", "rest_day"} for row in days))

    def test_named_holiday_overrides_generic_rest_day_label(self):
        days = calendar_days_for_range(date(2025, 10, 5), date(2025, 10, 7))

        by_date = {row["date"]: row for row in days}
        self.assertEqual(by_date[date(2025, 10, 6)]["holiday_name"], "中秋节")
        self.assertEqual(by_date[date(2025, 10, 6)]["label"], "中秋")
        self.assertEqual(by_date[date(2025, 10, 5)]["label"], "休")

    def test_range_returns_empty_for_unconfigured_year_and_reversed_interval(self):
        self.assertEqual(calendar_days_for_range(date(2030, 1, 1), date(2030, 12, 31)), [])
        self.assertEqual(calendar_days_for_range(date(2026, 1, 3), date(2026, 1, 1)), [])
