## 1. Issue #1 residual alignment: business account creation

These tasks are separated by capability for tracking and acceptance, not by PR boundary. The confirmed PR-related constraint is that backend API work may be combined into one PR; PR count, boundaries for other work, and ordering are undecided.

- [ ] 1.1 Extend the backend business-account model and migration for encrypted identity number; keep existing records nullable for verified backfill. Verify migration against an empty database and representative legacy rows, and update the backend database design.
- [ ] 1.2 Change business account creation to accept phone in `username`, require identity number, reject client-supplied password/status/manager, generate a strong random password, save only its hash, enable the account, and return the password only once. Verify validation, uniqueness, hashing, no-store headers, and secret-free logs/audit with focused backend tests; update backend API/database docs and the account-provisioning guide.
- [ ] 1.3 Align the Web creation form with phone-as-login, required identity number, no password/status input, and a one-time phone/password result dialog. Fix the organization navigation disclosure icon. Verify field boundaries, dialog dismissal/secret clearing, and icon alignment; update the requirements document, Web UI guide, and this OpenSpec change.
- [ ] 1.4 Change Android login labels, hints, and validation copy from account to phone number while continuing to send the phone value in `username`. Verify the request shape and visible login copy; update the requirements document, Android UI guide, and this OpenSpec change.

## 2. New Issue2 capability: organization API and rules

- [ ] 2.1 Add activation state and department/team detail responses for leader, members, and subordinate teams; implement name/description edits and list-triggered activation updates. Keep team department immutable after creation. Verify detail/edit boundaries and activation safeguards with backend API tests; update Swagger and database/API docs.
- [ ] 2.2 Enforce enabled-descendant and enabled-member checks before deactivation, and exclude inactive departments/teams from assignment choices. Verify rejected and accepted transitions through backend API tests; document user-facing validation outcomes.
- [ ] 2.3 Enforce zero-or-one active manager per department and zero-or-one active leader per team. Support temporary vacancy, reject a second active leader, keep `manager_id` server-owned, and recalculate direct-report links on valid transitions. Verify vacancy, conflict, and reassignment scenarios with backend tests; update the organization rules documentation.

## 3. New Issue2 capability: Web organization maintenance

- [ ] 3.1 Implement department/team list and detail screens with activation switches in lists and name/description-only editing in details; display parent/leader/member information as read-only. Verify the UI boundaries, empty-leader display, and rejected deactivation feedback; update Web documentation.

## 4. New Issue2 capability: employee management API

- [ ] 4.1 Add employee filtering by name/phone keyword, role, department, team, and activation state; return masked identity number only. Verify filters and sensitive-field redaction through API tests; update Swagger and API docs.
- [ ] 4.2 Allow administrator updates only to business role, department/team placement, and activation state. Reject personal-field, password, and client `manager_id` writes; exclude inactive selection candidates. Verify allowed/rejected fields, role-placement compatibility, and leader uniqueness with API tests; update account-management docs.

## 5. New Issue2 capability: Web employee management

- [ ] 5.1 Implement employee list filters and read-only name/phone/masked identity display. Implement only role, department/team, and activation controls; do not provide personal-data edits or password reset. Verify the rendered controls and API payload boundaries; update Web documentation.

## 6. New Issue2 capability: App self-service API

- [ ] 6.1 Let an authenticated employee update their own name, phone, and identity number; atomically update the unique `username` login identifier on phone change and prevent editing role, organization, manager, or activation state. Verify ownership, uniqueness, validation, and full-ID response redaction with API tests; update Swagger and API docs.
- [ ] 6.2 Settle the App password-change route and credential-check contract, then allow employees to change only their own password. Verify credential validation, password hashing, and that plaintext credentials are absent from responses/logs; update API documentation.

## 7. New Issue2 capability: Android App self-service

- [ ] 7.1 Implement employee self-edit for name, phone, identity number, and password. Finalize simple screen interaction during implementation while keeping organization/role/manager/activation read-only. Verify successful updates, duplicate phone feedback, and subsequent sign-in with the new phone; update Android documentation.

## 8. Integration and acceptance

- [ ] 8.1 Verify the account-create flow end to end: required phone/identity inputs, automatic activation, one-time initial-password display, and no secret persistence or subsequent disclosure. Record backend, Web, and API schema verification in the implementation PR.
- [ ] 8.2 Verify organization/member/leader management end to end, including inactive candidate filtering, zero-or-one active leaders, temporary vacancy, server-maintained direct reports, and deactivation guards. Record backend/Web verification in the implementation PR.
- [ ] 8.3 Verify employee App profile changes and phone login end to end, confirm the App cannot modify Web-owned role/organization/status, and confirm Web cannot edit App-owned personal fields. Record backend/Web/Android verification in the implementation PR.

## 9. Administrator authorization, audit, and workbench

- [ ] 9.1 Verify that only administrators can use management APIs and that administrators cannot read business tasks or employee logs. Add focused permission tests and document the enforced boundaries.
- [ ] 9.2 Persist successful organization/account management operations with an allowlist of before-and-after fields. Exclude plaintext passwords, full identity numbers, and employee-log content. Verify audit records with backend tests and update the audit contract.
- [ ] 9.3 Provide filtered administrator audit queries and the Web audit-record view. Verify filters, permission checks, and sensitive-data redaction; update API and Web documentation.
- [ ] 9.4 Keep the administrator workbench limited to system-management summaries. Verify that task/log metrics and content are absent from dashboard responses and presentation; update the workbench contract.

## 10. Android App account integration (Issue #6, PR 6; completed)

- [x] 10.1 Integrate the App-scoped login, refresh, and current-user APIs using the documented response envelope and backend error responses.
- [x] 10.2 Securely persist tokens with Android Keystore, restore the session from the current-user endpoint, retry once after access-token expiry, retain sessions on transient failures, and clear tokens on logout or rejected refresh.
- [x] 10.3 Replace demo authentication/profile data with the backend user response and handle credential, disabled-account, expired-session, and network errors.
- [x] 10.4 Replace adaptive and legacy launcher icons with the supplied Pandora logo; update Android account-flow documentation from the verified contract and test results.
- [x] 10.5 Offer retry after temporary startup session-validation failures; preserve a prior token pair until replacement credentials and profile are verified.
