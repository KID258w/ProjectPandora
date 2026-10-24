## Context

Issue #6 has implemented the first administrator/business authentication split, department/team/user creation APIs, and Android token/session integration. Those interfaces still reflect the initial implementation: business users can be created with a supplied password, personal fields are exposed differently from the agreed Web/App boundary, and organization details and employee updates are incomplete. Issue2 aligns those foundations and adds the remaining account and organization workflows.

## Goals / Non-Goals

**Goals:**
- Finish the agreed Issue #1 Web and Android account/login residuals.
- Let administrators inspect and maintain departments, teams, and employee roles/organization/activation state.
- Enforce the same rules in APIs and the database where possible, rather than relying on disabled or hidden UI controls.
- Let employees update their own personal profile and credentials in the App.
- Protect identity numbers and ensure a generated initial password is exposed only once.

**Non-Goals:**
- SMS delivery or verification, password recovery, and Web password reset.
- Editing employee personal fields in Web after account creation.
- Changing a team's parent department after creation.
- Automatic leader succession, multiple active leaders in one organization, or direct client control of `manager_id`.
- Task/log administration, organization transfer history tables, or the company-important-item workflow owned by another change.
- Final Android interaction design for personal-profile editing; the Android implementation PR can settle those presentation details without changing the field ownership rules here.

## Decisions

- **Business login identifier:** Keep the existing globally unique `username` column. Store a business user's phone number there; keep administrator usernames unchanged. The App token request continues to send `username`, whose business-account value is the current phone number. Do not add a duplicate `account` or phone column solely for login.
- **New account creation:** The Web sends phone (`username`), required `id_number`, `display_name`, business role, and valid organization placement. It does not send a password, activation flag, or manager ID. The backend generates a cryptographically random strong initial password, saves only the password hash, enables the account, and returns the plaintext only in that successful creation response. The Web result dialog shows the phone and password once, does not persist them, and clears the plaintext when dismissed. The response must not be cached; application, proxy, and audit logs must redact the password.
- **Identity-number handling:** Encrypt the full identity number in persistent storage. Return a masked value to ordinary administrator list/detail views and never return the full value from `/app/me/`. Require it for new accounts. Existing rows can remain null until real values are verified and backfilled; do not infer values from logs or fabricate them. Audit allowlists must omit the full identity number.
- **Web employee-management boundary:** After creation, Web can read the employee name, phone, masked identity number, role, organization, and activation state. Web updates only business role, department/team placement, and activation state. Reject name, phone, identity-number, password, and `manager_id` writes; do not add a Web password-reset route.
- **Organization details and edits:** Department detail returns its name, description, activation state, current active manager or null, subordinate teams, and members. Team detail returns its name, description, activation state, parent department, active leader or null, and members. Organization detail screens edit only name and description. Activation switches live in organization lists and submit only the activation change. Team parent department is immutable after creation.
- **Leader cardinality and transitions:** A department may have zero or one active Department Manager; a team may have zero or one active Team Leader. A leader can be revoked or deactivated, leaving a temporary vacancy. The administrator then adjusts the former and successor employees through employee management. Reject a second active leader; do not auto-select a successor or overwrite the current leader. The backend owns `manager_id`: clear affected direct-report links while a leader is absent and recalculate them when a valid active leader is assigned. A disabled employee, department, or team is not an assignment candidate, though it remains visible in management lists.
- **Activation safeguards:** Retain disabled records. Reject department deactivation while active subordinate teams or members remain, and reject team deactivation while active members remain. Creating or assigning an employee requires active target organizations.
- **App self-service:** An authenticated employee can edit their own name, phone, identity number, and password in the App. A phone update checks global uniqueness and updates the existing `username` login identifier. It does not change the employee's business role, organization, manager, or activation state. SMS verification is outside MVP. The password-change API path and credential-check fields must be settled in API implementation design; password recovery remains open.
- **Audit:** Retain the existing management-audit requirement for successful administrative mutations. Record actor, time, module, action, target, result, and allowed before/after management values. Never record plaintext passwords, full identity numbers, or employee-log content.
- **API compatibility:** Keep current endpoint paths where possible. Issue2 changes the payload/response contract of user creation, employee updates, and `/app/me/`; preserve the App login field name `username`. Update OpenAPI and client contracts in the same implementation work. Current Issue #6 behavior remains the baseline until each change is implemented.

## Implementation Work Areas

PR count, boundaries, sequence, and dependencies are not decided in this change. The confirmed PR-related constraint is that backend API work may be combined into one PR. The implementation tasks remain split by capability for tracking and acceptance, not to prescribe PR boundaries.

- Backend work covers Issue #1 account-create alignment and the Issue2 organization, employee-management, and App self-service APIs with the associated model and validation changes. The exact App password route and credential contract must be settled before implementing that endpoint.
- Web work covers the Issue #1 menu/form residuals, department/team maintenance, and employee management.
- Android work covers the Issue #1 phone-number login wording and Issue2 self-service profile editing.

These work areas do not establish which client changes share a PR or the order in which PRs are opened. Documentation for this work includes backend API/database docs, requirements, this OpenSpec change, and the backend account-provisioning guide. The Web and Android UI guides record only their respective Issue #1 residual fixes; they do not own backend or cross-platform requirements. Include focused automated verification with implementation. No PR or branch is created by this design document.

## Alternatives Considered

- Removing the `username` column and creating a separate phone login field was rejected because the current App login contract already uses `username`; reusing it avoids duplicate login identifiers and keeps the token request shape stable.
- Generating the initial password only in Web was rejected because the backend is the trusted source of password generation and must store only the password hash. Returning it once allows the creating administrator to hand it to the employee.
- Web-based password reset and SMS delivery were not selected. The employee manages their own password in the App; recovery is not yet specified.
- Editing leader relationships directly on department/team pages was rejected. Role and placement changes happen on the employee record so the same validations apply to every change.
- Automatic leader succession was rejected. A department or team can remain without an active leader while administrators resolve the transition.

## Risks / Trade-offs

- [Legacy usernames may not be phone numbers or may collide after verified phone backfill] → Verify existing contact data before migration, report collisions, and resolve them with an administrator; never invent or silently rewrite phone numbers.
- [Returning a one-time password creates a short-lived exposure window] → Return it only on create, prohibit caching, redact it from logs/audit, display it in a one-time dialog, and clear it on dismissal.
- [A full identity number is sensitive personal data] → Encrypt at rest, mask standard reads, and keep it out of logs and audit payloads.
- [Leader role transitions can temporarily leave employees without a direct manager] → Allow nullable manager links during the explicit vacancy state and recalculate them only after a valid active successor is assigned.
- [App phone changes can break future sign-in if propagated inconsistently] → Update the single unique login identifier atomically and report duplicate-phone validation errors before saving.

## Migration Plan

1. Add encrypted identity-number storage as nullable for existing records and required for new business-account creation.
2. Verify real phone and identity values for existing business accounts; identify phone collisions before updating usernames. Backfill only verified values.
3. Add organization activation fields with the agreed enabled default, preserving existing organizations as enabled after verifying current records.
4. Relax direct-manager constraints to support an allowed leader vacancy, then add service-level checks and database uniqueness where consistent with that transition. Preserve user, organization, and task/log history.
5. Deploy backend API changes and OpenAPI contract with clients coordinated to the new create response and allowed update fields. Do not claim the changed contract is active until the corresponding server deployment is complete.

Rollback must preserve already collected identity data securely and must not log or export plaintext passwords. Existing records must not be physically deleted.

## Open Questions

- What precise phone-number validation format and identity-number validation policy should the backend enforce?
- Which password-check credential and exact path will the App password-change endpoint use?
- What is the verified-data workflow and responsible operator for existing account phone/identity backfill?
- Which simple Android profile-edit screen and interaction pattern should be used? The allowed fields and ownership are settled; presentation is left to the Android implementation design.
