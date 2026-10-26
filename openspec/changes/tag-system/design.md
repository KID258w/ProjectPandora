# Design

## Context

See `proposal.md` for motivation and `specs/tag-system/spec.md` for observable behavior. Existing Android APIs already create/edit tasks, read task details, save/read personal logs, and return month/week/day work views. The change extends those contracts and adds a shared tag catalogue. The Web management API remains outside this capability.

## Goals / Non-Goals

**Goals:**

- Define founder-only tag management and shared read/search behavior for App roles.
- Preserve tag history and enforce inactive-tag association rules on task/log writes.
- Make task-bar color deterministic on the server so Android clients use the same color in every work view.
- Add relational tables without changing task, log, approval, or submission lifecycle columns.

**Non-Goals:**

- Web tag management, task/log tag filters, or log-content search.
- Tag descriptions, user-selected colors, tag restoration, or physical tag deletion.
- Changing existing task/log permissions, workflows, or visibility scopes.

## Decisions

### App API surface

All routes below require an authenticated Android business-user token; Web administrator tokens are not accepted.

| Method and path | Contract |
|---|---|
| `GET /api/v1/app/tags/?keyword=&page=&page_size=` | All roles receive enabled tags only, searched by name. Founder may pass `include_inactive=true` for the management center; only then does the response include inactive tags and `is_active`. |
| `POST /api/v1/app/tags/` | Founder only. Request `{ "name": "客户跟进" }`; the server assigns the color. |
| `PATCH /api/v1/app/tags/{id}/` | Founder only. Request accepts `name` only and only for enabled tags; color and status are not writable. |
| `DELETE /api/v1/app/tags/{id}/` | Founder only. Soft-deactivates the tag, retaining its row and all associations. Repeating the request for an inactive tag is idempotent; no reactivation endpoint exists. |

Tag list items contain `id`, `name`, `color`, and (for Founder management results) `is_active`. `keyword` filters names after the active/inactive scope is authorized. The ordinary picker, including the Founder's picker, omits inactive tags. A non-Founder requesting `include_inactive=true` receives `403`.

### Task and log API extensions

- `POST /api/v1/app/tasks/` accepts optional `tag_ids: number[]`; only enabled tags may be newly associated.
- `PATCH /api/v1/app/tasks/{id}/` accepts optional `tag_ids`. When present, it expresses the desired complete association set; an empty array removes all associations. The service applies a set difference: unchanged join rows and their original `added_at` values are preserved, removed rows are deleted, and new enabled tags are inserted in request order. When omitted, associations are unchanged. The existing task edit permission/status checks run before association changes.
- `GET /api/v1/app/tasks/{id}/` returns `tags: [{id, name, color}]`, ordered by association time and including inactive historical associations without an inactive-status marker. Existing task visibility checks remain authoritative.
- `PUT /api/v1/app/logs/mine/{log_date}/` accepts optional `tag_ids` with the same desired-set/difference semantics; omitted means unchanged and `[]` means clear. Unchanged join rows retain their original timestamps; new rows are inserted in request order. It remains restricted to the author's existing editable date window and preserves submitted status as before.
- `GET /api/v1/app/logs/{id}/` and `GET /api/v1/app/logs/mine/{log_date}/` return associated tags as `[{id, name, color}]` when a log exists. The manager-facing detail remains submitted-only and read-only. Calendar and list endpoints do not need tag data.
- If a requested inactive tag ID is already associated with that same task/log, it may remain in the submitted set. Any inactive tag not currently associated is rejected, including one removed in an earlier successful edit. The validation and record save run in one transaction.
- Task list and log list endpoints gain no tag filter and need not return tag names. Tag changes alone on an approved task do not create a new approval round.

Existing task/log edit rules are the source of authorization and status truth. Completed/cancelled/otherwise non-editable tasks remain non-editable; subordinate task views remain read-only. Only a log's author can change tags, and only for today, yesterday, or the day before yesterday in the configured business timezone.

### Work-view response

Each month task bar and week/day `all_day_tasks` item gains `bar_color: "#RRGGBB"`. The server chooses the color using the association with the earliest `added_at` among current task-tag rows, with association ID as a deterministic tie-breaker. If no association exists, it returns the fixed neutral-gray color. Inactive tags still contribute their stored color while associated. Work-view responses do not include tag names or status badges; task-bar rendering continues to show the task title only.

### Data model

Add three tables:

| Table | Key fields and constraints |
|---|---|
| `tags_tag` | `id`; required `name` (maximum 100 characters); `color` (stored palette value); `is_active` (default true); `created_at`; `updated_at`. Enabled names are globally unique; inactive names do not reserve a name. |
| `tags_task_tag` | `id`; `task_id` FK; `tag_id` FK; `added_at`; unique `(task_id, tag_id)`. Existing associations are retained when a tag is deactivated. |
| `tags_daily_log_tag` | `id`; `daily_log_id` FK; `tag_id` FK; `added_at`; unique `(daily_log_id, tag_id)`. Existing associations are retained when a tag is deactivated. |

Both association foreign keys use protected deletion for tags; a tag is deactivated, never physically deleted through the App. Removing an association deletes only the join row. Re-adding an enabled tag creates a new `added_at`, allowing the work-view first-current-association rule to be recalculated. During a task's initial creation, the request array order defines the order of simultaneous associations; later edits preserve existing association timestamps and order. Tag renames update one tag row, so historical task/log details return the current name while preserving the tag's color.

The system assigns color from a fixed ten-color palette by selecting the palette color currently used by the fewest tag rows; inactive tags count toward usage, and ties use fixed palette order. Store the assigned color on `tags_tag` so later palette changes cannot recolor historical records. Color assignment runs within the tag-creation transaction.

Enforce enabled-name uniqueness in both service validation and the database. Use a partial unique index on `(name)` where `is_active=true` on engines that support it; for MySQL, use an equivalent generated nullable active-name key with a unique index so multiple inactive duplicates remain valid. Verify the implementation against the actual deployment engine and its collation.

### Transaction and error behavior

- Tag create/rename checks enabled-name uniqueness and reports `409` on collision. Tag creation assigns color and inserts the row atomically.
- Task/log relation updates validate all IDs before applying any change; unknown IDs or newly requested inactive IDs yield `400`, leaving the record and all relations unchanged.
- Non-Founder tag management and out-of-scope task/log writes yield `403`; inaccessible task/log records preserve the existing `404`/`403` policy.
- Inactive tag `DELETE` is idempotent and does not remove relations. Unknown tag IDs return `404`.
- Tag-name uniqueness must be database-enforced to cover concurrent creation/rename requests; translate constraint violations to the same `409` API response.

## Risks / Trade-offs

- **SQLite/MySQL uniqueness behavior differs** → Add engine-specific uniqueness enforcement and run the same collision/deactivation cases against the configured engine.
- **Concurrent task/log edits can race with tag deactivation** → Lock or transactionally recheck tag state and current relations before saving; the commit must either preserve an existing association or reject the entire update.
- **Clients may assume omitted `tag_ids` means clear** → Document omission as “unchanged”; require explicit `[]` to clear associations.
- **A work-view client could choose a different tag color** → Return `bar_color` from the server and test its stable selection/fallback behavior.

## Migration Plan

1. Create the tag catalogue and two join tables, plus indexes and active-name uniqueness for the configured database.
2. Deploy the API changes. Existing tasks/logs have no associations after migration and therefore return the neutral-gray work-view color.
3. Deploy the Android picker, Founder management center, task/log detail chips, and work-view bar-color rendering against the documented API.
4. Roll back application code without dropping tables if tag data has been created. Destructive schema rollback requires an explicit backup and data-retention decision; no production tag rows or associations should be discarded automatically.

## Open Questions

None for the agreed MVP behavior. Exact palette color values remain a presentation-level choice and must be kept as one fixed, shared server/client palette when implementation begins.
