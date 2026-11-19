from django.test import TestCase

from .models import CompanyImportantItem
from .services import missing_publish_fields, published_items, reorder_items


class MidtermMsyContentServiceTests(TestCase):
    def item(self, title, status, order):
        return CompanyImportantItem.objects.create(title=title, summary="摘要", body="正文", status=status, sort_order=order)

    # missing_publish_fields: valid, one missing, and multiple/whitespace missing.
    def test_publish_field_check_accepts_complete_content(self):
        item = self.item("标题", "draft", 1)
        self.assertEqual(missing_publish_fields(item), {})

    def test_publish_field_check_reports_single_missing_field(self):
        item = self.item("", "draft", 1)
        self.assertEqual(set(missing_publish_fields(item)), {"title"})

    def test_publish_field_check_treats_whitespace_as_missing(self):
        item = self.item(" ", "draft", 1)
        item.summary = "\t"
        item.body = " "
        self.assertEqual(set(missing_publish_fields(item)), {"title", "summary", "body"})

    # published_items: empty, ordered list, and bounded prefix.
    def test_published_items_empty_result(self):
        self.item("草稿", "draft", 1)
        self.assertEqual(list(published_items()), [])

    def test_published_items_order_and_status_filter(self):
        later = self.item("后", "published", 2)
        first = self.item("先", "published", 1)
        self.item("下线", "offline", 0)
        self.assertEqual(list(published_items()), [first, later])

    def test_published_items_limit_zero_and_one(self):
        first = self.item("先", "published", 1)
        self.item("后", "published", 2)
        self.assertEqual(list(published_items(limit=0)), list(published_items()))
        self.assertEqual(list(published_items(limit=1)), [first])

    # reorder_items: ordinary ordering, omitted rows, and invalid IDs.
    def test_reorder_items_writes_contiguous_order(self):
        first = self.item("A", "draft", 10)
        second = self.item("B", "draft", 20)
        rows = reorder_items([second.pk, first.pk])
        first.refresh_from_db(); second.refresh_from_db()
        self.assertEqual([(row["id"], row["sort_order"]) for row in rows], [(second.pk, 1), (first.pk, 2)])
        self.assertEqual((second.sort_order, first.sort_order), (1, 2))

    def test_reorder_items_appends_omitted_rows_in_existing_order(self):
        first = self.item("A", "draft", 1)
        second = self.item("B", "draft", 2)
        third = self.item("C", "draft", 3)
        reorder_items([third.pk])
        first.refresh_from_db(); second.refresh_from_db(); third.refresh_from_db()
        self.assertEqual((third.sort_order, first.sort_order, second.sort_order), (1, 2, 3))

    def test_reorder_items_rejects_duplicate_and_missing_ids(self):
        item = self.item("A", "draft", 1)
        with self.assertRaises(Exception):
            reorder_items([item.pk, item.pk])
        with self.assertRaises(Exception):
            reorder_items([999999])
