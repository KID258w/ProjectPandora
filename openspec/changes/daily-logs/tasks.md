## 1. Log data and APIs

- [ ] 1.1 Add daily-log records, author/date uniqueness, status, timestamps, and optional many-to-many links to tasks.
- [ ] 1.2 Implement create/update/draft/submit APIs with required completed-work validation and natural-day write restrictions.
- [ ] 1.3 Implement author-only draft access and same-day edit/past-day read-only enforcement.
- [ ] 1.4 Implement manager subordinate-log queries for submitted records only, scoped through the organization hierarchy.
- [ ] 1.5 Implement optional task association validation without modifying task progress.

## 2. Android workflows

- [ ] 2.1 Build today's log editor, draft save, submit, same-day edit, and submitted-state presentation.
- [ ] 2.2 Build date-based personal log history and detail viewing.
- [ ] 2.3 Build manager object filter and subordinate submitted-log view with organization path and return-to-self action.
- [ ] 2.4 Add optional own-task selector and text-only log sections; omit comments, attachments, and search controls.

## 3. Integration and verification

- [ ] 3.1 Provide a query/API for the home panel to read the current user's latest ten submitted log summaries.
- [ ] 3.2 Verify view APIs do not read or expose log content, summaries, status, or associations.
- [ ] 3.3 Test uniqueness, required field, draft privacy, same-day edit, past-day freeze, role scopes, and forbidden operations.
- [ ] 3.4 Run an end-to-end create-draft-submit-edit-and-manager-read scenario, including denied draft access.
