## 1. Aggregation and personal ordering

- [x] 1.1 Implement an authenticated home aggregation query that reads company items, current-user tasks, personal ordering, and submitted log summaries from their source modules.
- [x] 1.2 Implement Company Top Ten task filtering, source/deadline/creation ordering, binary status display, and exclusion of completed/cancelled-archived/non-owned tasks.
- [x] 1.3 Add per-user Personal Top Ten selection/order and one-time prefill state; remove ineligible records while preserving remaining order.
- [x] 1.4 Implement latest-ten eligible task/item secondary lists and latest-five submitted-log summary plus published-company-item preview queries.
- [x] 1.5 Enforce response-field limits, current-user scoping, and no progress percentages on Home task summaries.

## 2. Android home experience

- [ ] 2.1 Build the four-panel home layout with at most five preview rows per card and concise loading, empty, and error states.
- [ ] 2.2 Build personal task selection capped at ten and long-press drag-handle reordering without exposing task-definition edits.
- [ ] 2.3 Add “view all” navigation to secondary lists; keep up to ten company items/tasks/personal tasks there and route selected tasks to their authorized task detail.
- [ ] 2.4 Keep full log editing/history and full task actions in their dedicated modules.
- [ ] 2.5 Add a Home task-notification bell and notification list with unread red-dot behavior and navigation to the relevant task detail.

## 3. Verification

- [x] 3.1 Test role-specific current-user scoping, source/deadline/creation ordering, binary task statuses, and task exclusion rules.
- [ ] 3.2 Test first-use prefill, subsequent independence, per-user isolation, max-ten validation, drag ordering, and automatic ineligible-item removal.
- [ ] 3.3 Test five-row Home previews, up-to-ten secondary lists, task-detail navigation, latest-five submitted logs, draft exclusion, common published items, and short/empty states.
- [x] 3.4 Verify Home APIs do not expose subordinate tasks/logs or enable full task/log mutations.
- [ ] 3.5 Verify approval and approved-assignment notification triggers, read-state behavior, authorized task navigation, and that viewing a notification does not change task state.

## 4. Prototype scope

- [x] 4.1 Update the local Android demo with a Home notification bell, red dot for unread approval/incoming-assignment notices, and a notification list that opens the related task detail. This prototype item does not count as production implementation of sections 1–3.
