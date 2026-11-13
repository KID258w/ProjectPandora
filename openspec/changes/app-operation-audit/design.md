## Context

The existing `AdminAuditLog` records Web administrator management operations. The Web requirements currently prohibit general task and employee-log access. The new requirement adds a narrow exception: an administrator may inspect employee App operation metadata and historical values for manual recovery, without getting general task/log management access.

The feature must cover successful App login and supported writes across accounts, tasks, logs and tags. Existing business records remain the source of current state; the audit history supplements them with the values needed to investigate overwritten content.

## Goals / Non-Goals

**Goals:**

- Preserve who performed an App operation, when it occurred, what object and fields it affected, and the relevant before/after business values.
- Let the Web administrator browse recent records and inspect a record's historical values for manual handling.
- Keep employee App audit records distinct from Web administrator audit records.
- Keep audit records inaccessible to business users and exclude credentials and full identity numbers.

**Non-Goals:**

- One-click restore, general task/log search or browsing, task/log editing in Web, audit export, or read-access tracking.
- Recording failed logins/writes, personal-focus order changes or notification read status.
- Storing passwords, full identity numbers, authentication tokens or avatar binary data.

## Decisions

1. **Use a separate `AppOperationAuditLog` entity/table.** Keep `AdminAuditLog` for Web management actions so its actor, permissions and retention semantics are not mixed with employee App events. Store actor FK plus name, business-role, department and team snapshots; store the target type, ID and display label snapshot.

2. **Write successful event snapshots with the business operation.** For supported state-changing operations, record only after the business change succeeds and in the same database transaction where possible. Failed writes create no entry. A successful login creates an entry after authentication succeeds. Use an allowlist for `changed_fields`, `before_data` and `after_data`; for creation the previous value is empty. Keep task/log text and other recoverable business values in these snapshots, but never credential values, full ID numbers, tokens or avatar bytes. For tag associations, persist ordered `{id, name}` values; for task assignees, persist `{id, display_name}`. Capture display names at operation time and resolve legacy numeric-only snapshots when possible. Do not expose update/delete endpoints for audit rows.

3. **Separate list summaries from detailed values.** The list API returns time, actor snapshots, module/action, target label and changed-field names, sorted newest first. A detail API returns the corresponding before/after values. This keeps large or private content out of the list while allowing an administrator to inspect historical text when needed.

4. **Allow only the existing Web administrator to inspect records.** Provide dedicated read-only list/detail APIs and a separate Web navigation item. This narrowly exposes audit snapshots; it does not add task or log lists, detail pages, editing, or exports to the Web console.

5. **Prevent browser caching of audit responses.** List and detail responses contain historical business content and should set `Cache-Control: no-store`. Keep endpoint access behind existing Web administrator authentication and authorization.

## Risks / Trade-offs

- [Audit snapshots contain sensitive work content] → Restrict access to the Web administrator, expose values only on record detail, redact password/full ID/token fields, and avoid browser caching.
- [Audit data grows with each operation] → Add indexes for global time ordering and employee/time filtering; retention/cleanup policy remains to be decided before implementing automatic deletion.
- [A business write succeeds but its audit insert fails] → Persist the business change and audit row in the same transaction where possible; treat inability to record a required audit event as a failed transaction for supported writes.
- [Manual handling can still introduce another incorrect edit] → Show timestamps, actor and before/after values clearly; the history page itself remains read-only and records subsequent corrective App writes as new events.

## Migration Plan

1. Add the App operation audit table and indexes; no existing business rows need backfilling.
2. Add audit recording to the supported App write/login paths and add the administrator-only read APIs.
3. Add the Web history page and detail view after the API contract is available.
4. Deploy the database migration before code that writes to the new table. If rolling back application code, retain the audit table and records; do not drop captured history as part of a routine rollback.

## Open Questions

- Confirm the retention period. The current recommendation is to avoid automatic cleanup in MVP so records remain useful for later recovery.
- Confirm any non-password/non-ID fields that must be excluded from task/log snapshots if the business data can contain other sensitive information.
