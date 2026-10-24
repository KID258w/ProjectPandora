## PR 1 — Backend (complete first)

- [ ] 1.1 Extend `Department` and `Team` with activation state and update timestamps; preserve existing records and default existing/new organizations to enabled. Relax user placement constraints to support temporary nullable `manager_id` during leader vacancy while retaining zero-or-one active leader uniqueness.
- [ ] 1.2 Implement department/team detail and update behavior at `GET, PATCH /api/v1/admin/departments/{id}/` and `GET, PATCH /api/v1/admin/teams/{id}/`. Permit name/description edits or standalone activation updates, keep leaders/members read-only, keep team department immutable, and enforce active-descendant deactivation guards.
- [ ] 1.3 Implement employee filtering and detail/update behavior at `GET /api/v1/admin/users/` and `GET, PATCH /api/v1/admin/users/{id}/`. Support name/phone keyword, role, department, team, and activation filters; allow updates only to `business_role`, `department_id`, `team_id`, and `is_active`; exclude inactive assignment candidates.
- [ ] 1.4 Enforce zero-or-one active department manager and team leader, permit temporary vacancies, reject second active leaders, keep `manager_id` server-owned, and rebuild direct-report links after valid leader assignment.
- [ ] 1.5 Implement App self-service `PATCH /api/v1/app/me/` for `display_name`, phone in `username`, and `id_number`. Allow name and identity number to be submitted together or separately, persist a multi-field update atomically, update the unique phone login identifier, and reject role/organization/manager/activation writes.
- [ ] 1.6 Implement `POST /api/v1/app/me/password/` with current-password verification, confirmation match, configured password validators, password-change hashing, and secret redaction. Return only a success flag; use the current JWT lifetime without adding server-side multi-device revocation.
- [ ] 1.7 Implement the administrator audit entity/query and management-only dashboard boundaries. Record allowed organization/employee changes without plaintext passwords, full identity numbers, employee-log content, or business-task data.
- [ ] 1.8 Update `docs/backend/api-design.md`, `docs/backend/database-design.md`, and `docs/backend/account-provisioning.md`; update the relevant requirement and OpenSpec contracts. Verify migrations, API permissions, validation, atomicity, and redaction with backend tests and schema validation.

## PR 2 — Web management (may proceed in parallel with Android after backend contract is set)

- [ ] 2.1 Add department/team detail screens and list activation controls. Keep details read-only for leaders, members, and team parent; allow editing name/description only. Display “无” when an organization has no active leader.
- [ ] 2.2 Add employee filters and detail display for name, phone, masked identity number, role, organization, and status. Allow only role, department/team placement, and activation changes; do not add personal-data editing or password reset.
- [ ] 2.3 Add active-only employee and organization choices, show backend validation for leader conflicts and activation guards, and connect the management summary/audit view to their APIs without exposing tasks or employee logs.
- [ ] 2.4 Update `docs/web/account-management-ui.md`; verify the rendered field boundaries, list/detail interactions, and API request payloads.

## PR 3 — Android App self-service (may proceed in parallel with Web after backend contract is set)

- [ ] 3.1 Add a personal-profile form for the employee's own name, phone, and identity number; allow name and identity number to be saved together or independently in one form, with no automatic synchronization.
- [ ] 3.2 Show the identity number only in masked form when reading the profile; allow the employee to re-enter the full value to change it. Refresh displayed profile values after a successful update.
- [ ] 3.3 Add current-password, new-password, and confirmation inputs for self-service password change; show backend validation feedback and never persist the plaintext password.
- [ ] 3.4 Preserve the existing phone login request field `username`; after a phone update, verify subsequent login uses the new phone. Keep role, organization, manager, and activation read-only in the App.
- [ ] 3.5 Update `docs/android/account-login-ui.md`; verify profile edits, atomic name/identity updates, password validation, and phone login behavior.

## Acceptance across the three PRs

- [ ] 4.1 Verify department/team activation guards, leader vacancy and uniqueness, role-placement rules, and inactive-candidate filtering end to end.
- [ ] 4.2 Verify Web cannot change employee personal fields and App cannot change administrator-managed role/organization/status fields.
- [ ] 4.3 Verify a combined name/identity update either saves both values or neither; verify single-field updates preserve the other value.
- [ ] 4.4 Verify password values and full identity numbers never appear in prohibited responses, application logs, or audit records.
- [ ] 4.5 Run strict OpenSpec validation, synchronize the main spec after implementation, and archive the change when all three PRs and acceptance checks are complete.
