## Context

Task Center and Logs currently provide independent subordinate selectors. The user wants a single organization entry beside Task Center, and a member profile that can route to the existing read-only subordinate task and submitted-log flows. The feature must preserve current role boundaries.

## Goals / Non-Goals

**Goals:** Provide a collapsible company-wide organization tree for every App user, name/role search, read-only member profiles, and navigation into existing task/log views where existing work permissions allow it. Position Task Center and Organization Relationships at the top right of Views; position Approval Center at the top right of Task Center.

**Non-Goals:** Mutating organization/person data; exposing full identity numbers; changing task/log response semantics or permissions; introducing organization history or new persistence.

## Decisions

- **Authorization:** Every authenticated business App user receives the company tree and can read enabled business member profiles. Web administrators do not use these App endpoints. The profile response includes `can_view_tasks` and `can_view_logs`, computed from the existing permissions for each feature; every task/log endpoint continues enforcing its existing authorization independently.
- **Tree structure:** The active Founder and all active departments are sibling root nodes; if there is no active Founder, departments are the root nodes. Department nodes contain the department manager and teams; team nodes contain the Team Leader and team employees. Person nodes include department/team names. Visible member nodes load their stored avatars through the member-avatar endpoint and show an initial when no avatar exists. With a `keyword`, the API returns a flat list of matching members by name or business-role label, without department/team nodes; results show department/team under the name except for Founder, and the role on the right. Clearing the keyword restores the organization tree.
- **Member details:** `GET /api/v1/app/organization/members/{id}/` returns only the fields shown on the simplified profile: name, phone, business role, department, team, and existing task/log access flags. It does not return national ID, supervisor, or account status. `GET /api/v1/app/organization/members/{id}/avatar/` returns the member's stored avatar image or 404 when no avatar is set.
- **Task/log navigation:** The task shortcut opens the selected user's existing subordinate task list, defaulting to assigned tasks and retaining the assigned/created switch and existing filters. The log shortcut opens the selected user's existing calendar and submitted-log details. Those APIs continue enforcing scope and read-only rules.
- **Navigation:** Views shows Task Center at top right and Organization Relationships adjacent for every App role. Task Center shows Approval Center at top right only for roles with an approval queue. Remove the separate subordinate-task shortcut from Task Center and the separate subordinate-person selector from Logs.
- **Persistence:** No new database models or fields. Use `User.manager`, `Department`, and `Team` as the source of truth.

## API and Failure Handling

- `GET /api/v1/app/organization/tree/?keyword=...` returns the full enabled company tree to any authenticated App user. Unknown or non-matching keywords return an empty tree result.
- `GET /api/v1/app/organization/members/{id}/` returns the read-only profile of an active business member to any authenticated App user. Missing, inactive, or non-business IDs return 404. `can_view_tasks` and `can_view_logs` indicate existing access separately and do not grant new access.
- `GET /api/v1/app/organization/members/{id}/avatar/` returns a private, no-store image response for an active business member; missing members or avatars return 404.
- Existing subordinate task and log endpoints remain the source of task/log content. A direct route to a member does not bypass their current authorization checks.
- Loading errors show retry actions. No result shows an empty state. Search does not change the authenticated user's scope.

## Risks / Trade-offs

- The company-wide profile exposes phone and avatar to all authenticated App users; identity numbers are omitted. Existing task and log permissions remain independently enforced.
- A nested tree is simple for the current small organization; if the roster grows substantially, the endpoint can later support lazy loading without changing the role rules.

## Migration Plan

No database migration. Deploy backend read endpoints and API contract together with the Android client. Until both are available, the App should show a recoverable loading/error state rather than treating an API failure as an empty organization.
