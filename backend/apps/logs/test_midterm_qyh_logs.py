from datetime import date, datetime, timedelta, timezone
from types import SimpleNamespace
from unittest.mock import patch

from django.db import IntegrityError
from rest_framework.exceptions import NotFound, PermissionDenied, ValidationError
from rest_framework.test import APITestCase

from apps.accounts.models import AppOperationAuditLog, Department, Team, User
from apps.tags.models import PALETTE, Tag

from .models import DailyLog, DailyLogComment, DailyLogTag
from .views import editable, log_comment_data, parse_date, require_editable, target_user, visible_log


class LogsMidtermTests(APITestCase):
    today = date(2026, 11, 1)
    clock = datetime(2026, 11, 1, 2, tzinfo=timezone.utc)
    url = "/api/v1/app/logs/"

    @classmethod
    def setUpTestData(cls):
        cls.department = Department.objects.create(name="研发")
        cls.team = Team.objects.create(name="服务端", department=cls.department)
        cls.founder = User.objects.create(username="founder", business_role=User.Role.FOUNDER)
        cls.manager = User.objects.create(
            username="manager", business_role=User.Role.DEPARTMENT_MANAGER,
            department=cls.department, manager=cls.founder,
        )
        cls.leader = User.objects.create(
            username="leader", business_role=User.Role.TEAM_LEADER,
            department=cls.department, team=cls.team, manager=cls.manager,
        )
        cls.author = User.objects.create(
            username="author", display_name="日志作者", business_role=User.Role.EMPLOYEE,
            department=cls.department, team=cls.team, manager=cls.leader,
        )
        cls.peer = User.objects.create(
            username="peer", business_role=User.Role.EMPLOYEE,
            department=cls.department, team=cls.team, manager=cls.leader,
        )

    def setUp(self):
        self.enterContext(patch("apps.logs.views.timezone.localdate", return_value=self.today))
        self.client.force_authenticate(user=self.author)

    def log(self, *, author=None, offset=0, status=DailyLog.Status.DRAFT, text="测试日志"):
        return DailyLog.objects.create(
            author=author or self.author, log_date=self.today + timedelta(days=offset),
            status=status, completed_work=text,
        )

    def mine(self, offset=0):
        return f"{self.url}mine/{self.today + timedelta(days=offset)}/"

    def comments(self, log):
        return f"{self.url}{log.pk}/comments/"

    def response_data(self, response, status=200):
        self.assertEqual(response.status_code, status, response.data)
        return response.data["data"]

    def test_parse_date_valid_iso_day(self):
        self.assertEqual(parse_date("2026-11-01", "log_date"), self.today)

    def test_parse_date_accepts_real_leap_day(self):
        self.assertEqual(parse_date("2028-02-29", "start_date"), date(2028, 2, 29))

    def test_parse_date_rejects_invalid_shape_and_day(self):
        for value in ("2026-2-01", "2026-02-29", self.today, None):
            with self.subTest(value=value), self.assertRaises(ValidationError) as error:
                parse_date(value, "end_date")
            self.assertIn("end_date", error.exception.detail)

    def test_editable_accepts_yesterday(self):
        self.assertTrue(editable(self.today - timedelta(days=1)))

    def test_editable_includes_both_window_edges(self):
        for offset in (-2, 0):
            with self.subTest(offset=offset):
                self.assertTrue(editable(self.today + timedelta(days=offset)))

    def test_editable_excludes_day_before_window_and_tomorrow(self):
        for offset in (-3, 1):
            with self.subTest(offset=offset):
                self.assertFalse(editable(self.today + timedelta(days=offset)))

    def test_require_editable_allows_yesterday(self):
        require_editable(self.today - timedelta(days=1))

    def test_require_editable_allows_earliest_day(self):
        require_editable(self.today - timedelta(days=2))

    def test_require_editable_rejects_outside_window(self):
        for offset in (-3, 1):
            with self.subTest(offset=offset), self.assertRaises(ValidationError) as error:
                require_editable(self.today + timedelta(days=offset))
            self.assertIn("log_date", error.exception.detail)

    def test_log_put_creates_draft_with_association_and_audit(self):
        tag = Tag.objects.create(name="研发", color=PALETTE[0])
        data = self.response_data(self.client.put(
            self.mine(), {"next_day_plan": "  明天联调  ", "tag_ids": [tag.pk]}, format="json",
        ))
        log = DailyLog.objects.get(pk=data["id"])
        self.assertEqual((log.author, log.status, log.next_day_plan), (self.author, DailyLog.Status.DRAFT, "明天联调"))
        self.assertEqual(list(log.tag_links.values_list("tag_id", flat=True)), [tag.pk])
        self.assertTrue(AppOperationAuditLog.objects.filter(action="create", object_id=str(log.pk)).exists())

    def test_log_put_reuses_earliest_day_row_and_preserves_omitted_tags(self):
        log = self.log(offset=-2)
        tag = Tag.objects.create(name="保留", color=PALETTE[0])
        DailyLogTag.objects.create(daily_log=log, tag=tag)
        for _ in range(2):
            data = self.response_data(self.client.put(self.mine(-2), {"completed_work": "新正文"}, format="json"))
            self.assertEqual(data["id"], log.pk)
        log.refresh_from_db()
        self.assertEqual(log.completed_work, "新正文")
        self.assertEqual(DailyLog.objects.filter(author=self.author, log_date=log.log_date).count(), 1)
        self.assertEqual(list(log.tag_links.values_list("tag_id", flat=True)), [tag.pk])
        self.assertEqual(AppOperationAuditLog.objects.filter(action="update", object_id=str(log.pk)).count(), 1)

    def test_log_put_rolls_back_body_and_links_when_link_write_fails(self):
        log = self.log()
        old = Tag.objects.create(name="旧", color=PALETTE[0])
        new = Tag.objects.create(name="新", color=PALETTE[1])
        DailyLogTag.objects.create(daily_log=log, tag=old)
        with patch("apps.logs.views.DailyLogTag.objects.create", side_effect=IntegrityError("forced link failure")):
            self.response_data(self.client.put(
                self.mine(), {"completed_work": "应回滚", "tag_ids": [new.pk]}, format="json",
            ), 400)
        log.refresh_from_db()
        self.assertEqual(log.completed_work, "测试日志")
        self.assertEqual(list(log.tag_links.values_list("tag_id", flat=True)), [old.pk])
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_log_submit_persists_transition_and_audit(self):
        log = self.log()
        with patch("apps.logs.views.timezone.now", return_value=self.clock):
            data = self.response_data(self.client.post(f"{self.mine()}submit/", {}, format="json"))
        log.refresh_from_db()
        self.assertEqual((data["id"], log.status, log.submitted_at), (log.pk, DailyLog.Status.SUBMITTED, self.clock))
        audit = AppOperationAuditLog.objects.get(action="submit", object_id=str(log.pk))
        self.assertEqual(audit.after_data["status"], DailyLog.Status.SUBMITTED)

    def test_log_submit_earliest_day_repeated_call_is_idempotent(self):
        log = self.log(offset=-2, status=DailyLog.Status.SUBMITTED)
        DailyLog.objects.filter(pk=log.pk).update(submitted_at=self.clock - timedelta(days=1))
        self.response_data(self.client.post(f"{self.mine(-2)}submit/", {}, format="json"))
        log.refresh_from_db()
        self.assertEqual(log.submitted_at, self.clock - timedelta(days=1))
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_log_submit_rejects_blank_missing_and_out_of_window(self):
        blank = self.log(text="  ")
        old = self.log(offset=-3)
        for offset in (0, -1, -3):
            with self.subTest(offset=offset):
                self.response_data(self.client.post(f"{self.mine(offset)}submit/", {}, format="json"), 400)
        self.assertCountEqual(
            DailyLog.objects.values_list("pk", "status", "submitted_at"),
            [(blank.pk, DailyLog.Status.DRAFT, None), (old.pk, DailyLog.Status.DRAFT, None)],
        )
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_target_user_defaults_or_explicitly_selects_self(self):
        for query in ({}, {"user_id": str(self.author.pk)}):
            with self.subTest(query=query):
                request = SimpleNamespace(user=self.author, query_params=query)
                self.assertEqual(target_user(request), self.author)

    def test_target_user_allows_indirect_active_report(self):
        request = SimpleNamespace(user=self.manager, query_params={"user_id": str(self.author.pk)})
        self.assertEqual(target_user(request), self.author)

    def test_target_user_rejects_peer_inactive_or_bad_identifier(self):
        peer = SimpleNamespace(user=self.peer, query_params={"user_id": str(self.author.pk)})
        with self.assertRaises(PermissionDenied):
            target_user(peer)
        User.objects.filter(pk=self.author.pk).update(is_active=False)
        inactive = SimpleNamespace(user=self.manager, query_params={"user_id": str(self.author.pk)})
        with self.assertRaises(PermissionDenied):
            target_user(inactive)
        malformed = SimpleNamespace(user=self.manager, query_params={"user_id": "invalid"})
        with self.assertRaises(ValidationError) as error:
            target_user(malformed)
        self.assertIn("user_id", error.exception.detail)

    def test_visible_log_allows_submitted_report(self):
        log = self.log(status=DailyLog.Status.SUBMITTED)
        self.assertEqual(visible_log(SimpleNamespace(user=self.manager), log.pk), log)

    def test_visible_log_allows_author_historical_draft(self):
        log = self.log(offset=-10)
        self.assertEqual(visible_log(SimpleNamespace(user=self.author), log.pk), log)

    def test_visible_log_conceals_foreign_draft_and_missing_id(self):
        draft = self.log()
        submitted = self.log(offset=-1, status=DailyLog.Status.SUBMITTED)
        for user, pk in ((self.manager, draft.pk), (self.peer, submitted.pk), (self.author, max(draft.pk, submitted.pk) + 1)):
            with self.subTest(user=user, pk=pk), self.assertRaises(NotFound):
                visible_log(SimpleNamespace(user=user), pk)

    def test_log_list_returns_author_draft_and_newest_first(self):
        draft = self.log()
        submitted = self.log(offset=-1, status=DailyLog.Status.SUBMITTED)
        data = self.response_data(self.client.get(self.url))
        self.assertEqual([(row["id"], row["status"]) for row in data["results"]],
                         [(draft.pk, DailyLog.Status.DRAFT), (submitted.pk, DailyLog.Status.SUBMITTED)])

    def test_log_list_manager_gets_submitted_on_closed_range(self):
        self.log()
        target = self.log(offset=-1, status=DailyLog.Status.SUBMITTED)
        self.log(offset=-2, status=DailyLog.Status.SUBMITTED)
        self.client.force_authenticate(user=self.manager)
        data = self.response_data(self.client.get(self.url, {
            "user_id": self.author.pk, "start_date": target.log_date.isoformat(), "end_date": target.log_date.isoformat(),
        }))
        self.assertEqual([row["id"] for row in data["results"]], [target.pk])

    def test_log_list_rejects_bad_date_or_outside_scope(self):
        self.assertIn("start_date", self.response_data(self.client.get(self.url, {"start_date": "2026-02-29"}), 400))
        self.response_data(self.client.get(self.url, {"user_id": self.peer.pk}), 403)

    def test_log_calendar_marks_only_submitted_report_days(self):
        self.log(status=DailyLog.Status.SUBMITTED)
        wanted = self.log(offset=-1, status=DailyLog.Status.SUBMITTED)
        self.log(offset=-2)
        self.client.force_authenticate(user=self.manager)
        data = self.response_data(self.client.get(f"{self.url}calendar/", {
            "user_id": self.author.pk, "year": 2026, "month": 10,
        }))
        self.assertEqual(data["days"], [{"date": wanted.log_date, "has_submitted_log": True}])

    def test_log_calendar_defaults_to_business_month_even_with_adjacent_results(self):
        self.log(offset=-1, status=DailyLog.Status.SUBMITTED)
        self.log()
        self.assertEqual(self.response_data(self.client.get(f"{self.url}calendar/")), {
            "year": 2026, "month": 11, "days": [],
        })

    def test_log_calendar_rejects_invalid_month_or_unauthorized_target(self):
        for query in ({"month": 13}, {"year": 0}):
            with self.subTest(query=query):
                result = self.response_data(self.client.get(f"{self.url}calendar/", query), 400)
                self.assertIn("year_month", result)
        self.response_data(self.client.get(f"{self.url}calendar/", {"user_id": self.peer.pk}), 403)

    def test_comment_post_manager_can_write_on_old_submitted_log(self):
        log = self.log(offset=-10, status=DailyLog.Status.SUBMITTED)
        original = (log.completed_work, log.status, log.updated_at)
        self.client.force_authenticate(user=self.manager)
        data = self.response_data(self.client.post(self.comments(log), {"content": "  已查阅  "}, format="json"), 201)
        comment = DailyLogComment.objects.get(pk=data["id"])
        self.assertEqual((comment.author, comment.daily_log, comment.content), (self.manager, log, "已查阅"))
        self.assertFalse(data["is_log_author"])
        log.refresh_from_db()
        self.assertEqual((log.completed_work, log.status, log.updated_at), original)
        self.assertTrue(AppOperationAuditLog.objects.filter(action="comment", object_id=str(comment.pk)).exists())

    def test_comment_post_accepts_one_and_2000_chars_on_own_draft(self):
        log = self.log()
        for content in ("评", "评" * 2000):
            with self.subTest(length=len(content)):
                data = self.response_data(self.client.post(self.comments(log), {"content": f" {content} "}, format="json"), 201)
                self.assertEqual(data["content"], content)
                self.assertTrue(data["is_log_author"])
        self.assertEqual(log.comments.count(), 2)

    def test_comment_post_rejects_blank_overlong_and_hidden_log(self):
        draft = self.log()
        submitted = self.log(offset=-1, status=DailyLog.Status.SUBMITTED)
        for content in ("   ", "评" * 2001):
            with self.subTest(length=len(content)):
                data = self.response_data(self.client.post(self.comments(draft), {"content": content}, format="json"), 400)
                self.assertIn("content", data)
        for user, log in ((self.manager, draft), (self.peer, submitted)):
            self.client.force_authenticate(user=user)
            self.response_data(self.client.post(self.comments(log), {"content": "不可见"}, format="json"), 404)
        self.assertFalse(DailyLogComment.objects.exists())
        self.assertFalse(AppOperationAuditLog.objects.exists())

    def test_comment_list_preserves_creation_order_and_parent_scope(self):
        log = self.log(status=DailyLog.Status.SUBMITTED)
        other = self.log(offset=-1, status=DailyLog.Status.SUBMITTED)
        later = DailyLogComment.objects.create(author=self.author, daily_log=log, content="后发")
        earlier = DailyLogComment.objects.create(author=self.manager, daily_log=log, content="先发")
        DailyLogComment.objects.filter(pk=later.pk).update(created_at=self.clock)
        DailyLogComment.objects.filter(pk=earlier.pk).update(created_at=self.clock - timedelta(seconds=1))
        DailyLogComment.objects.create(author=self.author, daily_log=other, content="另一个")
        self.client.force_authenticate(user=self.manager)
        data = self.response_data(self.client.get(self.comments(log)))
        self.assertEqual([(row["id"], row["is_log_author"]) for row in data["results"]],
                         [(earlier.pk, False), (later.pk, True)])

    def test_comment_list_empty_for_visible_draft(self):
        log = self.log()
        data = self.response_data(self.client.get(self.comments(log)))
        self.assertEqual((data["count"], data["results"]), (0, []))

    def test_comment_list_hides_report_draft_and_peer_log(self):
        draft = self.log()
        DailyLogComment.objects.create(author=self.author, daily_log=draft, content="私密")
        submitted = self.log(offset=-1, status=DailyLog.Status.SUBMITTED)
        self.client.force_authenticate(user=self.manager)
        denied = self.response_data(self.client.get(self.comments(draft)), 404)
        self.client.force_authenticate(user=self.peer)
        self.assertEqual(self.response_data(self.client.get(self.comments(submitted)), 404), denied)
        self.assertNotIn("results", denied)

    def test_comment_data_marks_author_and_includes_role_and_team(self):
        log = self.log()
        comment = DailyLogComment.objects.create(author=self.author, daily_log=log, content="本人")
        data = log_comment_data(comment, log)
        self.assertEqual(data["author"], {
            "id": self.author.pk, "display_name": "日志作者", "business_role": User.Role.EMPLOYEE,
            "department_name": "研发", "team_name": "服务端",
        })
        self.assertTrue(data["is_log_author"])
        self.assertEqual((data["content"], data["created_at"]), ("本人", comment.created_at))

    def test_comment_data_falls_back_without_org_or_display_name(self):
        log = self.log()
        comment = DailyLogComment.objects.create(author=self.founder, daily_log=log, content="已阅")
        data = log_comment_data(comment, log)
        self.assertEqual(data["author"], {
            "id": self.founder.pk, "display_name": "founder", "business_role": User.Role.FOUNDER,
            "department_name": None, "team_name": None,
        })
        self.assertFalse(data["is_log_author"])

    def test_comment_data_does_not_classify_same_display_name_as_author(self):
        self.peer.display_name = self.author.display_name
        log = self.log()
        comment = DailyLogComment.objects.create(author=self.peer, daily_log=log, content="同名")
        data = log_comment_data(comment, log)
        self.assertEqual(data["author"]["display_name"], self.author.display_name)
        self.assertNotEqual(data["author"]["id"], self.author.pk)
        self.assertFalse(data["is_log_author"])
        self.assertEqual(set(data["author"]), {"id", "display_name", "business_role", "department_name", "team_name"})
