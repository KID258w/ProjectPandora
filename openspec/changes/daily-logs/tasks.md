## 1. Log data and APIs

- [ ] 1.1 Add daily-log records, author/date uniqueness, status, and timestamps; do not create a log-to-task association.
- [ ] 1.2 Implement create/update/draft/submit APIs with required completed-work validation and natural-day write restrictions.
- [ ] 1.3 Implement author-only draft access and same-day edit/past-day read-only enforcement.
- [ ] 1.4 Implement manager subordinate-log queries for submitted records only, scoped through the organization hierarchy.
- [ ] 1.5 Ensure the log API and editor contain only today's completed work and optional next-day plan; omit task and issue/risk fields.

## 2. Android workflows

- [ ] 2.1 Make the month calendar and selected-date log detail the default Log tab screen; add previous/next month and quick year/month selection.
- [ ] 2.2 Add a personal-only circular plus action that opens today's log editor; support draft save, submit, same-day edit, and submitted-state presentation.
- [ ] 2.3 Build manager object selection and subordinate submitted-log view with organization path and return-to-self action.
- [ ] 2.4 Add keyword filtering by person name, team, or department within the authorized object selector; do not search log content.

## 3. Integration and verification

- [ ] 3.1 Provide a query/API for the home panel to read the current user's latest five submitted log summaries.
- [ ] 3.2 Verify view APIs do not read or expose log content, summaries, status, or associations.
- [ ] 3.3 Test uniqueness, required field, draft privacy, same-day edit, past-day freeze, year/month navigation, person search scope, role scopes, and forbidden operations.
- [ ] 3.4 Run an end-to-end create-draft-submit-edit-and-manager-read scenario, including denied draft access.
