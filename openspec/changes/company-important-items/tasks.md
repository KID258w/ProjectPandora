## 1. Data and backend

- [ ] 1.1 Add company-important-item records, publication state, publication metadata, manual order, and optional attachment references.
- [ ] 1.2 Implement administrator-only create/edit/draft/publish/unpublish/reorder endpoints with required-field and ten-published-item validation.
- [ ] 1.3 Implement the mobile read endpoint that returns only published items in administrator-defined order, capped at ten.
- [ ] 1.4 Record item mutations and ordering changes in administrator audit records.

## 2. Client workflows

- [ ] 2.1 Build Web list, create/edit, draft, publish/unpublish, ordering, and mobile-preview workflows.
- [ ] 2.2 Build mobile company-important-item list and read-only detail/attachment presentation.

## 3. Verification

- [ ] 3.1 Test administrator authorization, required-field validation, draft privacy, ten-item cap, ordering, and unpublish behavior.
- [ ] 3.2 Test that all mobile roles receive the same published list and no task controls or unpublished content.
- [ ] 3.3 Verify management mutations create auditable records and OpenAPI contracts match both client APIs.
