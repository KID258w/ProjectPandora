## 1. Backend organization endpoints

- [x] 1.1 Implement an authenticated company organization endpoint for every App role, returning sibling Founder/department roots for the tree and flat matching members for name or role search.
- [x] 1.2 Implement a read-only profile endpoint and avatar endpoint for active business members, with phone, role, department/team, and separate existing task/log access indicators; omit national ID.
- [x] 1.3 Confirm existing subordinate task and log APIs continue to enforce member scope, approved-task-only results, and submitted-log-only results when called from the new routes.
- [x] 1.4 Update `docs/backend/api-design.md` for the profile and avatar endpoints; confirm no database model or migration is required.

## 2. Android navigation and screens

- [x] 2.1 Place Task Center at the top right of Views and Organization Relationships beside it for every App role.
- [x] 2.2 Place Approval Center at the top right of Task Center for roles with approval queues and remove the separate subordinate-task shortcut.
- [x] 2.3 Build the expandable company organization tree and flat name/role search results with avatar, name, department/team (except Founder), right-side role, loading, empty, and error states.
- [x] 2.4 Build a simplified read-only member profile with avatar, name, phone, role, department/team, and permission-aware task/log shortcuts.
- [x] 2.5 Route selected members to existing task and log screens; retain member context, read-only behavior, and return navigation.
- [x] 2.6 Remove the separate subordinate selector from the regular Logs tab while keeping it on the current user's own calendar.

## 3. Requirements and verification

- [x] 3.1 Update requirements 01, 02, 03, and 04 and add requirement 08 for organization relationships.
- [x] 3.2 Validate that all App roles can read company organization data while task/log access remains independently permission-scoped, exposing only approved tasks and submitted logs.
- [x] 3.3 Run Django system checks, Android debug assembly, and strict OpenSpec validation; record outcomes.
- [x] 3.4 Simplify the member profile presentation, omit national ID from its API response, and expose uploaded member avatars.
- [x] 3.5 Show Founder and departments as sibling roots; show search matches as flat person rows with their department/team except for Founder.

### Verification record

- `python manage.py check` — passed.
- `python manage.py makemigrations --check --dry-run` — passed; no schema changes detected.
- `:app:assembleDebug` — passed.
- `:app:compileDebugAndroidTestKotlin` — passed (source compilation only; test cases were not run).
- `openspec validate mobile-organization-relationships --strict` — passed.
- `git diff --check` — passed; Git reported only existing LF-to-CRLF normalization warnings.
