## Why

The current App exposes subordinate task and log browsing through separate selectors, so users must rediscover the same people in multiple modules. The Views page also needs a stable place for task and organization navigation while the work-view implementation is still pending.

## What Changes

- Place the Task Center action at the top right of the Views page and place the Organization Relationships action beside it for every authenticated App role.
- Place the Approval Center action at the top right of Task Center when the current user has an approval queue.
- Add a company-wide, expandable organization tree with search by member name or business role for every App user.
- Add a simplified read-only member profile with the account avatar, name, phone, role, department, and team; show task, log, and work-view shortcuts only when the corresponding existing work permissions allow them.
- Replace the task-center subordinate shortcut and the log-page subordinate selector with entry from the member profile page; subordinate month/week/day views use the same profile entry.
- Add App organization tree, member-profile, and protected member-avatar API contracts. Reuse current user and organization records; do not add database entities.

## Non-Goals

- Editing user, organization, role, account, or password data in the App.
- Changing task, approval, log, or account permission rules.
- Giving Web administrators access to business App endpoints.
- Implementing the month/week/day work views themselves.

## Impact

- Requirements: `docs/requirements/01-task-creation-and-acceptance.md`, `02-home-four-panels.md`, `03-daily-log-module.md`, `04-month-week-day-work-views.md`, and new `08-organization-relationships.md`.
- API contract: `docs/backend/api-design.md`.
- Android: Views entry controls, Task Center toolbar, organization tree/profile screens, and member-scoped task/log/work-view navigation.
- Backend: App-authenticated company roster/profile APIs built on `User`, `Department`, `Team`, and existing supervisor relationships; task/log/work-view permissions remain independently enforced.
- Database: no schema or migration change.
