## 1. Requirements and OpenSpec

- [x] 1.1 Add the personal daily-items product requirement and update the Home-panel/work-view boundaries.
- [x] 1.2 Define the owner-scoped item, date-range, color, overflow-list, detail-popup, edit/delete, drag, no-repeat, and audit-exclusion scenarios in OpenSpec.
- [x] 1.3 Align this change with Issue 8's production backend and Android implementation scope.

## 2. Compose Demo prototype

- [x] 2.1 Replace the Home “个人重点工作” panel and personal focus page with “每日事项” and a separate personal month calendar.
- [x] 2.2 Add in-memory personal-item samples and Home filtering for items covering the demo's current date, capped at five.
- [x] 2.3 Implement calendar bars, date/+N day list, colored cards, and the floating item detail card.
- [x] 2.4 Implement create/edit form with title, inclusive start/end dates, optional description, and ten visible color swatches.
- [x] 2.5 Implement confirmation before deletion and immediate UI updates after create/edit/delete.
- [x] 2.6 Implement long-press drag that shifts both dates by the same delta and preserves the original duration.
- [x] 2.7 Keep demo interactions local and outside company tasks, work views, and audit data.
- [x] 2.8 Add the quick year/month selector to the personal calendar header.
- [x] 2.9 Make dragged item bars visibly follow the pointer, lift during movement, and settle with a spring response.

## 3. Backend

- [x] 3.1 Add `HomePersonalDailyItem` and its migration with owner, title, inclusive dates, description, preset color, timestamps, constraints, and owner/date indexes.
- [x] 3.2 Implement owner-scoped date-range list, create, detail, partial update, hard delete, and duration-preserving move endpoints.
- [x] 3.3 Extend Home aggregation with up to five today's item summaries while keeping personal-focus data/routes compatible and unmigrated.
- [x] 3.4 Add backend tests for validation, date-overlap filtering, account isolation, Home preview, move idempotency/duration, and audit exclusion.
- [x] 3.5 Ensure OpenAPI/Swagger matches the endpoints and errors; keep API/database documents aligned with implementation.

## 4. Production Android App

- [x] 4.1 Replace Home's personal-focus and greeting cards with the API-backed full personal items calendar embedded beneath the company summary cards.
- [x] 4.2 Implement the embedded personal month calendar using the existing company Month view's grid layout and styling conventions, with date-range API loading and quick year/month navigation.
- [x] 4.3 Implement date/+N full-day lists, colored item cards, and the detail popup using server item data.
- [x] 4.4 Implement create/edit forms, ten palette samples, required/optional field validation, and confirmed deletion through the API.
- [x] 4.5 Persist drag moves through the move endpoint while showing the entire bar following the pointer and retaining its original duration.
- [x] 4.6 Handle loading, empty, retry, save/delete/move errors, and refresh Home/calendar state after successful mutations.
- [x] 4.7 Switch months smoothly when a dragged item reaches either calendar edge and keep the drag active across the transition.
- [x] 4.8 Remove the temporary Daily Greeting card and embed the complete daily-items experience directly in Home.
- [x] 4.9 Return configured holiday/rest/make-up-workday markers with the personal calendar date-range response and show them using the company month-view marker style.
- [x] 4.10 Confirm the personal calendar year/month control opens the shared quick selector and applies the selected year and month.
- [x] 4.11 Remove the Home greeting, employee position/organization, and date text while retaining the notification action.

## 5. Integration and review

- [x] 5.1 Verify fresh test-database migrations and backend check/test commands.
- [x] 5.2 Build Android and run instrumented tests for Home preview/navigation, date list/detail, item creation, and long-press drag.
- [x] 5.3 Manually verify month navigation, adjacent-month bars, crowded dates, edit/delete, and multi-day dragging on the emulator.
- [x] 5.4 Verify legacy personal-focus tables/data/routes remain untouched and the new Home flow does not display the old panel or call legacy routes.
- [x] 5.5 Review the final diff for Web/audit/task/work-view scope leaks and validate this OpenSpec change.
- [x] 5.6 Add instrumented coverage for dragging into both calendar edges and advancing to the adjacent month.
- [x] 5.7 Synchronize the personal calendar holiday-marker response contract in the API document, product requirement, and OpenSpec.

> Manual visual verification in 5.3 was completed on the emulator with no issues reported.
