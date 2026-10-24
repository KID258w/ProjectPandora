## Context

Issue #1 account creation and phone-login residuals are complete. Issue2 adds the organization, employee-management, and App self-service behavior that was intentionally left out of the first delivery. The current backend has `User`, `Department`, and `Team` models and the initial list/create/login APIs; Issue2 must extend them without changing the completed account-creation contract.

## Goals / Non-Goals

**Goals:**
- Let Web administrators view and maintain department, team, and employee records within the agreed field boundaries.
- Enforce activation, organization-placement, and zero-or-one active-leader rules in the backend.
- Let an employee update only their own personal profile and password through the Android App.
- Keep ordinary reads and audit records free of full identity numbers and plaintext passwords.
- Complete the backend API/database/provisioning docs, requirements, Web/App UI docs, and OpenSpec artifacts needed for implementation.

**Non-Goals:**
- Reopening Issue #1 account creation, initial-password delivery, Android login wording, or Web navigation layout.
- Web editing of employee name, phone, identity number, or password; Web password reset; SMS verification; or password recovery.
- Editing a team's department after creation, automatic leader succession, organization-transfer history, task/log administration, or company-important-item management.

## Decisions

- **Entities:** Continue using `accounts.User`, `accounts.Department`, and `accounts.Team`; do not add separate Employee, DepartmentManager, or TeamLeader entities. User's existing `password` stores the Django hash. Add the administrator audit model/table for management-operation records.
- **Business login identifier:** Keep the globally unique `User.username`; its value is the current phone number for business accounts. App login continues to send that value in the `username` JSON field.
- **Identity-number uniqueness:** `id_number` is encrypted at rest, so a database unique constraint cannot work on plaintext. Enforce uniqueness with a keyed HMAC-SHA256 `id_number_hash` column (server-held key) that is unique; application validation returns a clear duplicate error, and the unique index guards concurrent writes.
- **Organization maintenance:** Department and team details show name, description, activation state, active leader or null, and read-only member information; department details also show subordinate teams and team details show the parent department. Detail edits accept only `name` and `description`; list activation controls update only `is_active`. A team's department is immutable after creation.
- **Employee administration:** Admin lists support name/phone keyword, role, department, team, and activation filters, and return masked identity numbers. Admin PATCH accepts only `business_role`, `department_id`, `team_id`, and `is_active`; it rejects personal fields, password, and client-supplied `manager_id`.
- **Leader cardinality and transitions:** A department may have zero or one active Department Manager; a team may have zero or one active Team Leader. Administrators change leaders through employee role/placement changes. Reject a second active leader and do not auto-appoint a successor. `manager_id` remains backend-owned; affected direct-report links may be null during vacancy and are recalculated when a valid leader is enabled.
- **Activation safeguards:** Preserve disabled records. Reject department deactivation while active subordinate teams or members remain, and reject team deactivation while active members remain. Disabled employees and organizations remain visible in management lists but are excluded from role/placement candidates. Employee creation or reassignment requires active target organizations.
- **App profile:** `GET /api/v1/app/me/` returns a masked identity number. `PATCH /api/v1/app/me/` accepts any supported subset of `display_name`, phone in `username`, and `id_number`; it does not accept role, organization, manager, activation, or password writes. Name and identity number appear in the same editable profile form and may be submitted together or separately. They are independent fields, with no automatic real-name synchronization. Save the request atomically so any validation failure rolls back all submitted profile changes. Phone changes atomically update the unique login identifier.
- **App password:** `POST /api/v1/app/me/password/` accepts `current_password`, `new_password`, and `new_password_confirm`. Verify the current password, require matching confirmation and a value different from the current password, apply configured Django validators, and update only the current user's password hash. Return a success flag only; never log or echo password values. Do not add server-side multi-device JWT revocation in this MVP; issued tokens expire under their current lifetime. SMS verification and recovery are outside scope.
- **Audit and admin data boundary:** Record successful organization and employee-management changes with an allowlist of before/after fields. Exclude plaintext passwords, full identity numbers, and employee-log content. The administrator workbench contains management summaries only; administrators cannot read business tasks or employee logs.
- **Compatibility:** Keep existing endpoint paths where possible. The Issue1 account-create contract remains unchanged. Update Swagger and client contracts with Issue2 implementation; existing Issue #6 behavior remains until the relevant Issue2 backend PR is deployed.

## PR Plan

Issue2 has three PRs, all targeting `dev`:

1. **Backend first:** model/migration changes, Web and App APIs, validation, authorization, audit behavior, tests, and backend API/database/account-provisioning docs; update the requirements and OpenSpec contract.
2. **Web:** organization and employee maintenance screens, admin workbench/audit view as specified, API integration, and `docs/web/account-management-ui.md`.
3. **Android:** employee profile and password self-service, API integration, and `docs/android/account-login-ui.md`.

The backend PR establishes the API contract first. Web and Android PRs may then be developed and integrated in parallel. No member assignment or branch name is set by this design.

## Alternatives Considered

- Adding a separate phone field was rejected because `username` is already globally unique and is the App login field; storing the same value twice would introduce synchronization risk.
- Allowing Web to edit personal information was rejected because employees own their name, phone, identity number, and password in the App.
- Direct leader editing on organization detail pages was rejected; leader changes use the same employee role/placement validations as other staff changes.
- Automatic successor selection was rejected; organizations may remain without an active leader until an administrator updates an employee.
- Coupling name and identity-number values through assumed external identity verification was rejected because the MVP has no verification or OCR integration. The App can submit both fields in one transaction without automatically deriving one from the other.

## Risks / Trade-offs

- [Legacy usernames may not be verified phone numbers or may collide] -> Verify existing contact data and resolve collisions before any backfill; do not invent or silently rewrite values.
- [Identity numbers are sensitive] -> Encrypt at rest, mask standard reads, and exclude full values from logs and audit data.
- [Leader transitions can temporarily leave employees without a direct manager] -> Allow nullable manager links during the explicit vacancy and rebuild valid links when a new leader is enabled.
- [Phone changes affect subsequent login] -> Validate global uniqueness and update the login identifier in the same transaction as the profile change.
- [Multiple profile fields may be submitted together] -> Validate and persist the full PATCH atomically so a failed field cannot leave a partial update.

## Migration Plan

1. Add `is_active` to departments and teams with an enabled default; preserve existing records and verify the default against current data.
2. Relax the user role/placement database check as needed to permit a temporary nullable `manager_id` during leader vacancy; retain conditional uniqueness for one active founder, department manager, and team leader.
3. Add the administrator audit table/model and indexes for time, actor, and module. Audit payload allowlists must omit passwords and full identity numbers.
4. Keep existing encrypted identity values and password hashes. Do not delete accounts or organizations; do not backfill unverified personal data.
5. Deploy the backend contract first, then integrate Web and Android against the agreed endpoints.

Rollback must preserve existing accounts, organization data, identity-number encryption keys, and audit history. It must not export or log plaintext credentials.

## Open Questions

- What exact phone-number format and identity-number validation rules should the backend enforce?
- Who verifies and backfills phone/identity data for legacy accounts, and what is the approved workflow?
- Which final layout and interaction details will the Web and Android implementations choose within the documented field boundaries?
