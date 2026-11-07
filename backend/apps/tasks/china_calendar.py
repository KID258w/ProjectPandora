"""Versioned mainland China public-holiday schedules used by work views.

Dates are copied from the annual State Council General Office notices. Add a
year only after its official arrangement has been published; do not infer
public holidays or make-up workdays from lunar dates or weekends.
"""

from datetime import date, timedelta


HOLIDAY_RANGES = {
    2025: (
        ("2025-01-01", "2025-01-01", "元旦", "元旦"),
        ("2025-01-28", "2025-02-04", "春节", "春节"),
        ("2025-04-04", "2025-04-06", "清明节", "清明"),
        ("2025-05-01", "2025-05-05", "劳动节", "五一"),
        ("2025-05-31", "2025-06-02", "端午节", "端午"),
        ("2025-10-01", "2025-10-08", "国庆节、中秋节", "国庆"),
    ),
    2026: (
        ("2026-01-01", "2026-01-03", "元旦", "元旦"),
        ("2026-02-15", "2026-02-23", "春节", "春节"),
        ("2026-04-04", "2026-04-06", "清明节", "清明"),
        ("2026-05-01", "2026-05-05", "劳动节", "五一"),
        ("2026-06-19", "2026-06-21", "端午节", "端午"),
        ("2026-09-25", "2026-09-27", "中秋节", "中秋"),
        ("2026-10-01", "2026-10-07", "国庆节", "国庆"),
    ),
    2027: (
        ("2027-01-01", "2027-01-03", "元旦", "元旦"),
        ("2027-02-05", "2027-02-13", "春节", "春节"),
        ("2027-04-03", "2027-04-05", "清明节", "清明"),
        ("2027-05-01", "2027-05-05", "劳动节", "五一"),
        ("2027-06-09", "2027-06-09", "端午节", "端午"),
        ("2027-09-15", "2027-09-15", "中秋节", "中秋"),
        ("2027-10-01", "2027-10-07", "国庆节", "国庆"),
    ),
}

MAKEUP_WORKDAYS = {
    2025: ("2025-01-26", "2025-02-08", "2025-04-27", "2025-09-28", "2025-10-11"),
    2026: ("2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09", "2026-09-20", "2026-10-10"),
    2027: ("2027-01-31", "2027-02-14", "2027-05-08", "2027-09-26", "2027-10-09"),
}

# Named public-holiday dates that fall inside a larger continuous rest period.
# Their festival name takes precedence over the generic "休" marker.
NAMED_HOLIDAYS = {
    "2025-10-06": ("中秋节", "中秋"),
}


def calendar_days_for_range(start_date: date, end_date: date) -> list[dict]:
    """Return only configured holiday/rest/make-up workday entries in range."""
    result = []
    for year in range(start_date.year, end_date.year + 1):
        for first_text, last_text, name, first_label in HOLIDAY_RANGES.get(year, ()):
            first = date.fromisoformat(first_text)
            last = date.fromisoformat(last_text)
            current = max(first, start_date)
            stop = min(last, end_date)
            while current <= stop:
                named_holiday = NAMED_HOLIDAYS.get(current.isoformat())
                is_first_day = current == first
                is_named_holiday = named_holiday is not None
                holiday_name, label = named_holiday or (name, first_label if is_first_day else "休")
                result.append({
                    "date": current,
                    "day_type": "holiday" if is_first_day or is_named_holiday else "rest_day",
                    "holiday_name": holiday_name,
                    "label": label,
                })
                current += timedelta(days=1)

        for day_text in MAKEUP_WORKDAYS.get(year, ()):
            current = date.fromisoformat(day_text)
            if start_date <= current <= end_date:
                result.append({
                    "date": current,
                    "day_type": "makeup_workday",
                    "holiday_name": None,
                    "label": "班",
                })

    return sorted(result, key=lambda item: item["date"])
