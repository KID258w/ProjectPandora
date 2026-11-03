## 1. Backend audit records

- [ ] 1.1 Add the App operation audit entity, migration, indexes, actor snapshots and target label snapshots.
- [ ] 1.2 Record successful login and supported App write events with allowlisted changed fields and before/after values in the business transaction.
- [ ] 1.3 Exclude failed/read-only/exempt operations and redact passwords, full identity numbers, tokens and avatar binary data.
- [ ] 1.4 Add backend tests for event coverage, snapshot accuracy, atomicity, redaction and failure paths.

## 2. Administrator API

- [ ] 2.1 Add a paginated newest-first list endpoint with employee filtering and summary-only fields.
- [ ] 2.2 Add a read-only detail endpoint that returns the selected record's before/after values; require Web administrator access and disable caching.
- [ ] 2.3 Document the endpoints in OpenAPI/Swagger and verify non-administrator access is denied.

## 3. Web operation history page

- [ ] 3.1 Add a separate Employee App Operation History page with global latest records and employee filtering.
- [ ] 3.2 Add record detail showing actor/object snapshots and changed field values without restore, edit, or export actions.
- [ ] 3.3 Verify ordinary task and employee-log browsing remains unavailable in Web.

## 4. Contracts and verification

- [x] 4.1 Update the Web requirements, backend API contract and database design for the confirmed scope.
- [x] 4.2 Create the proposal, capability specification and technical design for this change.
- [ ] 4.3 Run relevant backend/Web checks and review access-control and data-retention behavior before release.
