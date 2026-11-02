## 1. Log data and APIs

- [x] 1.1 Add daily-log records, author/date uniqueness, status, and timestamps; do not create a log-to-task association.
- [x] 1.2 Implement create/update/draft/submit APIs with required completed-work validation and business-timezone three-date write restrictions.
- [x] 1.3 Implement author-only draft access and three-date edit/frozen-history enforcement.
- [x] 1.4 Implement manager subordinate-log queries for submitted records only, scoped through the organization hierarchy.
- [ ] 1.5 Ensure the log API and editor contain only completed work for the selected log date and optional next-day plan; omit task and issue/risk fields. Backend API complete; Android editor pending.
- [x] 1.6 Add the separate `DailyLogComment` entity and migration, plus visibility-scoped comment list/create APIs with commenter profile and log-author marker.

## 2. Android workflows

- [ ] 2.1 Make the month calendar and selected-date log detail the default Log tab screen; add previous/next month and side-by-side year/month scroll wheels.
- [ ] 2.2 Show a personal-only circular plus button for eligible empty/draft dates; use a detail entry for submitted logs that opens edit or read-only detail according to the date window and viewer.
- [ ] 2.3 Build manager object selection and subordinate submitted-log view with organization path and return-to-self action.
- [ ] 2.4 Add keyword filtering by person name, team, or department within the authorized object selector; do not search log content.
- [x] 2.5 Refine selected-date actions: show plus for empty/draft personal dates, open submitted logs in detail first with a conditional Modify action, and replace text year/month fields with side-by-side vertical wheels.
- [x] 2.6 Hide the main bottom navigation while a log editor or read-only detail is open, then restore it on return to the calendar.
- [x] 2.7 Show the comment list immediately after log detail, add a fixed input-style entry that opens the comment form, and keep the conditional fixed Modify action; display commenter role and the “日志本人” label.

## 3. Integration and verification

- [x] 3.1 Provide a query/API for the home panel to read the current user's latest five submitted log summaries. Backend query: `apps.logs.services.recent_submitted_logs`; aggregation is Issue #22.
- [ ] 3.2 Verify view APIs do not read or expose log content, summaries, status, or associations.
- [ ] 3.3 Test uniqueness, required field, draft privacy, edits/backfill within the three-date window, freeze after the window, year/month navigation, person search scope, role scopes, and forbidden operations.
- [ ] 3.4 Run an end-to-end create-draft-submit-edit-and-manager-read scenario, including denied draft access.
- [x] 3.5 Update the daily-log requirement, API/database documentation, OpenSpec proposal/design/spec, and this task list for log comments.

## 4. Prototype scope

- [x] 4.1 Update the local Android demo so the selected date drives the log editor; allow the author to create/edit today, yesterday, and the day before yesterday, and show older logs read-only while keeping subordinate logs read-only.

## 5. Verification record

- `python manage.py check` — passed.
- `python manage.py makemigrations --check --dry-run` — passed; the new comment migration matches the model.
- `:app:assembleDebug` — passed.
- `:app:compileDebugAndroidTestKotlin` — passed (source compilation only; test cases were not run).
- `openspec validate daily-logs --strict` — passed.
- `git diff --check` — passed; Git reported only existing LF-to-CRLF normalization warnings.
