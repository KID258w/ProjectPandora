## 1. Request domain and access control

- [ ] 1.1 Add cross-department request, status-history, reason, target/requester, and target-work linkage records.
- [ ] 1.2 Implement server authorization so only the two participating Department Managers can read or operate on a request.
- [ ] 1.3 Implement send, accept, reason-required reject, and reason-required pre-acceptance withdrawal; prohibit edits and post-acceptance cancellation.

## 2. Execution and acceptance integration

- [ ] 2.1 On acceptance, create/link target-department cross-department work in the manager's "My Tasks" flow.
- [ ] 2.2 Implement target department result submission and requester acceptance/required-reason return loop.
- [ ] 2.3 Ensure requester can read request state/result but cannot read target department employee/task details.
- [ ] 2.4 Add in-app reminders for delivery, acceptance, rejection, withdrawal, submission, and return events.

## 3. Android workflows

- [ ] 3.1 Build "Requests I Sent" and "Requests for My Department" lists and request detail states.
- [ ] 3.2 Build create/send, accept/reject, withdraw, submit-result, and accept/return interactions with reason validation.
- [ ] 3.3 Display accepted work through the existing target-department task workflow without duplicating request records.

## 4. Verification

- [ ] 4.1 Test each request-state transition, reason requirement, immutable sent scope, and no-cancel-after-acceptance rule.
- [ ] 4.2 Test role/participant isolation and ensure no cross-department task-tree data is returned to the requester.
- [ ] 4.3 Run an end-to-end two-department request, internal execution, result return/revision, and final acceptance scenario.
