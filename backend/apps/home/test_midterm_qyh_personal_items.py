from datetime import date, timedelta

from rest_framework.test import APITestCase

from apps.accounts.models import AppOperationAuditLog, Department, User

from .models import HomePersonalDailyItem
from .serializers import PersonalDailyItemRangeSerializer, PersonalDailyItemSerializer


class PersonalItemsMidtermTests(APITestCase):
    url = "/api/v1/app/personal-items/"

    @classmethod
    def setUpTestData(cls):
        cls.owner = User.objects.create(username="owner", business_role=User.Role.FOUNDER)
        cls.other = User.objects.create(
            username="other", business_role=User.Role.DEPARTMENT_MANAGER,
            department=Department.objects.create(name="研发"), manager=cls.owner,
        )

    def setUp(self):
        self.client.force_authenticate(user=self.owner)

    def payload(self, **fields):
        return {
            "title": "事项", "start_date": "2026-10-02", "end_date": "2026-10-06",
            "description": "说明", "color": "#C7DDFF", **fields,
        }

    def item(self, **fields):
        return HomePersonalDailyItem.objects.create(**{
            "owner": self.owner, "title": "事项", "description": "说明", "color": "#C7DDFF",
            "start_date": date(2026, 10, 2), "end_date": date(2026, 10, 6), **fields,
        })

    def detail(self, item):
        return f"{self.url}{item.pk}/"

    def move(self, item, target):
        return self.client.post(f"{self.detail(item)}move/", {"target_start_date": target}, format="json")

    def response_data(self, response, status=200):
        self.assertEqual(response.status_code, status, response.data)
        return response.data["data"]

    def test_item_validation_accepts_multi_year_range(self):
        form = PersonalDailyItemSerializer(data=self.payload(start_date="2026-12-31", end_date="2028-02-29"))
        self.assertTrue(form.is_valid(), form.errors)
        self.assertEqual(form.validated_data["end_date"], date(2028, 2, 29))

    def test_item_validation_accepts_equal_dates(self):
        form = PersonalDailyItemSerializer(data=self.payload(start_date="2028-02-29", end_date="2028-02-29"))
        self.assertTrue(form.is_valid(), form.errors)
        self.assertEqual(form.validated_data["start_date"], form.validated_data["end_date"])

    def test_item_validation_compares_partial_patch_to_saved_date(self):
        item = self.item()
        form = PersonalDailyItemSerializer(item, data={"start_date": "2026-10-07"}, partial=True)
        self.assertFalse(form.is_valid())
        self.assertIn("end_date", form.errors)
        item.refresh_from_db()
        self.assertEqual(item.start_date, date(2026, 10, 2))

    def test_range_validation_accepts_cross_year(self):
        form = PersonalDailyItemRangeSerializer(data={"start_date": "2026-12-31", "end_date": "2027-01-01"})
        self.assertTrue(form.is_valid(), form.errors)
        self.assertEqual(form.validated_data, {"start_date": date(2026, 12, 31), "end_date": date(2027, 1, 1)})

    def test_range_validation_accepts_single_day(self):
        form = PersonalDailyItemRangeSerializer(data={"start_date": "2026-10-01", "end_date": "2026-10-01"})
        self.assertTrue(form.is_valid(), form.errors)
        self.assertEqual(form.validated_data["start_date"], form.validated_data["end_date"])

    def test_range_validation_rejects_inverted_range(self):
        form = PersonalDailyItemRangeSerializer(data={"start_date": "2026-10-02", "end_date": "2026-10-01"})
        self.assertFalse(form.is_valid())
        self.assertIn("end_date", form.errors)

    def test_item_list_returns_own_overlap_and_holiday_annotation(self):
        own = self.item()
        self.item(owner=self.other)
        data = self.response_data(self.client.get(self.url, {"start_date": "2026-10-01", "end_date": "2026-10-02"}))
        self.assertEqual([row["id"] for row in data["items"]], [own.pk])
        self.assertEqual(data["calendar_days"][0], {
            "date": date(2026, 10, 1), "day_type": "holiday", "holiday_name": "国庆节", "label": "国庆",
        })
        self.assertEqual([row["date"] for row in data["calendar_days"]], [date(2026, 10, 1), date(2026, 10, 2)])

    def test_item_list_includes_each_closed_interval_endpoint(self):
        left = self.item()
        right = self.item(start_date=date(2026, 10, 8), end_date=date(2026, 10, 9))
        self.item(start_date=date(2026, 10, 9), end_date=date(2026, 10, 9))
        data = self.response_data(self.client.get(self.url, {"start_date": "2026-10-06", "end_date": "2026-10-08"}))
        self.assertEqual([row["id"] for row in data["items"]], [left.pk, right.pk])

    def test_item_list_rejects_missing_bad_or_reversed_dates(self):
        for query, field in (
            ({"start_date": "2026-10-01"}, "end_date"),
            ({"start_date": "2026-02-29", "end_date": "2026-10-01"}, "start_date"),
            ({"start_date": "2026-10-02", "end_date": "2026-10-01"}, "end_date"),
        ):
            with self.subTest(query=query):
                self.assertIn(field, self.response_data(self.client.get(self.url, query), 400))

    def test_item_create_saves_trimmed_payload_without_audit(self):
        existing = self.item()
        audit_count = AppOperationAuditLog.objects.count()
        data = self.response_data(self.client.post(self.url, self.payload(title="  新事项  "), format="json"), 201)
        row = HomePersonalDailyItem.objects.get(pk=data["id"])
        self.assertEqual((row.owner, row.title, row.description), (self.owner, "新事项", "说明"))
        self.assertNotEqual(row.pk, existing.pk)
        self.assertEqual(AppOperationAuditLog.objects.count(), audit_count)

    def test_item_create_accepts_200_characters_and_single_day(self):
        title = "事" * 200
        data = self.response_data(self.client.post(
            self.url, self.payload(title=title, end_date="2026-10-02"), format="json",
        ), 201)
        item = HomePersonalDailyItem.objects.get(pk=data["id"])
        self.assertEqual(item.title, title)
        self.assertEqual(item.start_date, item.end_date)

    def test_item_create_rejects_bad_fields_and_owner_override(self):
        for fields, field in (
            ({"title": "  "}, "title"), ({"title": "事" * 201}, "title"),
            ({"color": "#000000"}, "color"), ({"owner_id": self.other.pk}, "owner_id"),
        ):
            with self.subTest(field=field, fields=fields):
                result = self.response_data(self.client.post(self.url, self.payload(**fields), format="json"), 400)
                self.assertIn(field, result)
        self.assertFalse(HomePersonalDailyItem.objects.exists())
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_item_detail_returns_owned_item_full_fields(self):
        own = self.item()
        self.item(owner=self.other)
        data = self.response_data(self.client.get(self.detail(own)))
        self.assertEqual((data["id"], data["description"]), (own.pk, own.description))
        self.assertEqual(data["start_date"], "2026-10-02")

    def test_item_detail_tracks_current_authenticated_owner(self):
        first = self.item()
        second = self.item(owner=self.other)
        self.client.force_authenticate(user=self.other)
        self.assertEqual(self.response_data(self.client.get(self.detail(second)))["id"], second.pk)
        self.response_data(self.client.get(self.detail(first)), 404)

    def test_item_detail_foreign_and_missing_share_404_contract(self):
        foreign = self.item(owner=self.other)
        denied = self.response_data(self.client.get(self.detail(foreign)), 404)
        missing = self.response_data(self.client.get(f"{self.url}{foreign.pk + 1}/"), 404)
        self.assertEqual(denied, missing)

    def test_item_patch_updates_only_sent_fields_without_audit(self):
        item = self.item()
        self.response_data(self.client.patch(
            self.detail(item), {"description": "改后", "color": "#D7C8F2"}, format="json",
        ))
        item.refresh_from_db()
        self.assertEqual((item.description, item.color, item.title, item.start_date),
                         ("改后", "#D7C8F2", "事项", date(2026, 10, 2)))
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_item_patch_can_shrink_to_one_day(self):
        item = self.item()
        data = self.response_data(self.client.patch(self.detail(item), {"end_date": "2026-10-02"}, format="json"))
        item.refresh_from_db()
        self.assertEqual(data["end_date"], "2026-10-02")
        self.assertEqual(item.end_date, item.start_date)

    def test_item_patch_rejects_reversed_and_foreign_updates(self):
        own = self.item()
        foreign = self.item(owner=self.other)
        self.assertIn("end_date", self.response_data(self.client.patch(
            self.detail(own), {"start_date": "2026-10-07"}, format="json",
        ), 400))
        self.response_data(self.client.patch(self.detail(foreign), {"description": "禁止"}, format="json"), 404)
        own.refresh_from_db()
        foreign.refresh_from_db()
        self.assertEqual(own.start_date, date(2026, 10, 2))
        self.assertEqual(foreign.description, "说明")

    def test_item_delete_removes_only_requested_owned_item(self):
        own = self.item()
        foreign = self.item(owner=self.other)
        self.assertEqual(self.response_data(self.client.delete(self.detail(own))), {"deleted": True, "id": own.pk})
        self.assertEqual(list(HomePersonalDailyItem.objects.values_list("pk", flat=True)), [foreign.pk])
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_item_delete_repeated_id_returns_not_found(self):
        item = self.item()
        self.response_data(self.client.delete(self.detail(item)))
        self.response_data(self.client.delete(self.detail(item)), 404)
        self.assertFalse(HomePersonalDailyItem.objects.exists())

    def test_item_delete_cannot_touch_other_account(self):
        foreign = self.item(owner=self.other)
        self.response_data(self.client.delete(self.detail(foreign)), 404)
        self.assertTrue(HomePersonalDailyItem.objects.filter(pk=foreign.pk, owner=self.other).exists())

    def test_item_move_across_month_and_year_preserves_duration(self):
        item = self.item()
        for target, expected_end in (("2026-10-30", "2026-11-03"), ("2026-12-30", "2027-01-03")):
            with self.subTest(target=target):
                data = self.response_data(self.move(item, target))
                self.assertEqual((data["start_date"], data["end_date"]), (target, expected_end))
                item.refresh_from_db()
                self.assertEqual((item.end_date - item.start_date, item.description), (timedelta(days=4), "说明"))
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_item_move_single_day_and_repeated_target_are_idempotent(self):
        item = self.item(end_date=date(2026, 10, 2))
        first = self.response_data(self.move(item, "2028-02-29"))
        item.refresh_from_db()
        updated_at = item.updated_at
        second = self.response_data(self.move(item, "2028-02-29"))
        item.refresh_from_db()
        self.assertEqual((item.start_date, item.end_date), (date(2028, 2, 29), date(2028, 2, 29)))
        self.assertEqual(first, second)
        self.assertEqual(item.updated_at, updated_at)

    def test_item_move_rejects_invalid_date_and_foreign_item(self):
        own = self.item()
        foreign = self.item(owner=self.other)
        self.assertIn("target_start_date", self.response_data(self.move(own, "2026-02-29"), 400))
        self.response_data(self.move(foreign, "2026-10-12"), 404)
        for item in (own, foreign):
            item.refresh_from_db()
            self.assertEqual(item.start_date, date(2026, 10, 2))
