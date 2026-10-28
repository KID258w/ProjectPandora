## 1. Log data and APIs

- [x] 1.1 Add daily-log records, author/date uniqueness, status, and timestamps; do not create a log-to-task association.
- [x] 1.2 Implement create/update/draft/submit APIs with required completed-work validation and business-timezone three-date write restrictions.
- [x] 1.3 Implement author-only draft access and three-date edit/frozen-history enforcement.
- [x] 1.4 Implement manager subordinate-log queries for submitted records only, scoped through the organization hierarchy.
- [ ] 1.5 Ensure the log API and editor contain only completed work for the selected log date and optional next-day plan; omit task and issue/risk fields. Backend API complete; Android editor pending.

## 2. Android workflows

- [ ] 2.1 Make the month calendar and selected-date log detail the default Log tab screen; add previous/next month and quick year/month selection.
- [ ] 2.2 Add a personal-only circular plus/edit action for the selected date when it is today, yesterday, or the day before yesterday; support draft save, submit, edits within the window, and frozen-history presentation.
- [ ] 2.3 Build manager object selection and subordinate submitted-log view with organization path and return-to-self action.
- [ ] 2.4 Add keyword filtering by person name, team, or department within the authorized object selector; do not search log content.

## 3. Integration and verification

- [x] 3.1 Provide a query/API for the home panel to read the current user's latest five submitted log summaries. Backend query: `apps.logs.services.recent_submitted_logs`; aggregation is Issue #22.
- [ ] 3.2 Verify view APIs do not read or expose log content, summaries, status, or associations.
- [ ] 3.3 Test uniqueness, required field, draft privacy, edits/backfill within the three-date window, freeze after the window, year/month navigation, person search scope, role scopes, and forbidden operations.
- [ ] 3.4 Run an end-to-end create-draft-submit-edit-and-manager-read scenario, including denied draft access.

## 4. Prototype scope

- [x] 4.1 Update the local Android demo so the selected date drives the log editor; allow the author to create/edit today, yesterday, and the day before yesterday, and show older logs read-only while keeping subordinate logs read-only.
