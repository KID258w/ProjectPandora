## Context

The existing Home panel called “个人十大重点工作” stores an ordered subset of company tasks. Product decision replaces that panel with Personal Daily Items: employee-created memo entries that may cover inclusive date ranges. The existing Compose Demo already demonstrates the interaction flow; Issue 8 adds production persistence and Android API integration.

Daily items remain separate from `Task`, logs, tag relations, company work-view data, and operation-audit records. Every App business user can access their own entries, while other users and Web administrators cannot read or manage them.

## Goals / Non-Goals

**Goals:** Persist owner-scoped personal items; expose validated App APIs; include up to five items covering business-time-zone today in Home; connect Android Home and a personal calendar; reuse the existing company Month view's visual grid conventions; create, edit, delete, and move items while preserving duration.

**Non-Goals:** Web management, company task or log integration, task completion/approval/overdue state, tags, recurrence, sharing, audit entries, or adding personal items to company work views.

## Decisions

### Data model

- Add `HomePersonalDailyItem` in the existing `home` Django app, table `home_personaldailyitem`.
- Store owner, required title, inclusive `start_date` / `end_date`, optional description, a selected palette hex color, and creation/update timestamps.
- Owner is assigned from the authenticated business user. Database/API enforce `end_date >= start_date` and one of the ten tag-palette colors. Multiple same-day and overlapping items are allowed.
- Use owner/date indexes for interval overlap queries. Item deletion physically removes its row. User deactivation preserves rows; physical user deletion cascades them.
- Preserve existing personal-focus tables, data, and routes during compatibility. Do not convert old task IDs into memo entries.

### API behavior

- Extend `GET /api/v1/app/home/` with `personal_daily_items`, containing at most five `{id, title}` summaries whose inclusive dates cover the service's `TIME_ZONE` today. Keep the legacy `personal_focus_tasks` response during compatibility; new Android Home ignores it.
- `GET /api/v1/app/personal-items/?start_date=&end_date=` returns every caller-owned item whose inclusive range overlaps the requested inclusive range, sorted by start date, end date, and ID. It is unpaginated so the calendar can compute overflow accurately.
- `POST /api/v1/app/personal-items/`, `GET/PATCH/DELETE /api/v1/app/personal-items/{id}/` provide create, detail, update, and delete. Only title, dates, description, and preset color are writable. Ownership is never accepted from input; non-owned IDs resolve as `404`.
- `POST /api/v1/app/personal-items/{id}/move/` accepts `target_start_date`; the service moves both dates by the delta from the existing start date, preserving the inclusive duration. Retrying the same target start is idempotent.
- API writes do not create App or Web audit entries. Daily items do not use task, log, or tag endpoints.

### Android calendar and navigation

- Keep two equal-width company summary cards at the top of Home, remove the Home greeting and employee position/date text, and embed the complete personal daily-items month calendar below them.
- Remove the daily-items preview card and Daily Greeting card. Home's embedded calendar provides the existing create, date-list, detail, edit, delete, month-selection, holiday-marker, and drag interactions without a separate calendar navigation step.
- Reuse or extract the visual and layout conventions from `feature/views/ViewsScreen.kt`'s month grid: seven weekday columns, date cells, grid spacing, business-date handling, and existing color/type styles. Keep daily-item fetching, colored bars, overflow list, edit/detail state, and drag callbacks specific to daily items; do not route them through company work-view APIs.
- The calendar queries the visible grid's date bounds. Tapping a date or `+N项` opens all items covering that date as colored cards. Tapping a bar/card opens the detail popup.
- Create/edit uses title, inclusive start/end dates, optional description, and all ten visible color samples. The selected date defaults both endpoints; otherwise use today.
- Long-press drag uses the initial-touch date and release date to calculate the new start date. The entire bar visibly follows the pointer. The move API preserves the original duration. Changing duration remains an edit-form operation.
- Date calculations and Home's meaning of today follow the business timezone, `Asia/Shanghai`.

## Alternatives Considered

- Reusing company `Task` records was rejected because daily items have no assignee, approval, completion, or acceptance lifecycle.
- Reusing company work-view API data was rejected because that API is a read-only projection of authorized company tasks and task events.
- Reusing the old personal-focus task table was rejected because it stores task IDs and ordering, not employee-authored memo content.
- Automatically migrating old personal-focus selections was rejected because they represent company tasks and do not contain dates, descriptions, or memo colors.
- A dedicated move endpoint was selected over a general date PATCH for drag so the server preserves the original range length and can safely retry a fixed target start.

## Risks / Trade-offs

- [The existing month grid is coupled to company task payloads] → extract only shared date-grid styling/layout where useful; keep daily-item data and callbacks independent.
- [Legacy App versions still expect personal-focus data] → retain old response fields/routes and stored rows through compatibility; new App ignores them.
- [Calendar overlap queries can return many entries] → query only the visible month-grid date range and use owner/date indexes; return all matches for correct overflow counts.
- [Users may confuse memo entries with tasks] → label the Home panel and calendar “每日事项”; do not show task statuses or add the entries to task screens.

## Migration Plan

Add the personal-item table without modifying existing focus records. Keep the legacy focus tables, response field, and routes during this change; do not create conversion rows. Apply the new migration, deploy backend routes, and then use the API from the Android App. Any later legacy-focus cleanup requires a separate compatibility and data-retention decision.
