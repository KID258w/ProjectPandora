## 1. Aggregation and personal ordering

- [ ] 1.1 Implement an authenticated home aggregation query that reads company items, current-user tasks, personal ordering, and submitted log summaries from their source modules.
- [ ] 1.2 Implement Company Top Ten task filtering, source ordering, tie-break ordering, and exclusion of completed/cancelled/non-owned tasks.
- [ ] 1.3 Add per-user Personal Top Ten selection/order and one-time prefill state; remove ineligible records while preserving remaining order.
- [ ] 1.4 Implement latest-ten submitted-log summary and published-company-item panel queries.
- [ ] 1.5 Enforce response-field limits, current-user scoping, and no progress percentages on Home task summaries.

## 2. Android home experience

- [ ] 2.1 Build the four-panel home layout with concise loading, empty, and error states.
- [ ] 2.2 Build personal task selection/reordering capped at ten without exposing task-definition edits.
- [ ] 2.3 Add navigation from item summaries to authorized task, log, or company-item details.
- [ ] 2.4 Keep full log editing/history and full task actions in their dedicated modules.

## 3. Verification

- [ ] 3.1 Test role-specific current-user scoping, source priority, deadline/creation ordering, and task exclusion rules.
- [ ] 3.2 Test first-use prefill, subsequent independence, per-user isolation, max-ten validation, and automatic ineligible-item removal.
- [ ] 3.3 Test latest-ten submitted logs, draft exclusion, common published items, and no-data/less-than-ten states.
- [ ] 3.4 Verify Home APIs do not expose subordinate tasks/logs or enable full task/log mutations.
