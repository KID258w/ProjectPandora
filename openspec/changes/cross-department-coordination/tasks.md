## 1. Cross-department task creation and approval

- [ ] 1.1 Add the dedicated Department Manager creation entry using the common standalone task fields and cross-department flag.
- [ ] 1.2 Validate that creator and assignee are Department Managers in different departments.
- [ ] 1.3 Route the task through the creator's direct-supervisor approval and prevent target visibility/execution before approval.
- [ ] 1.4 After approval, expose the same task in the creator's “我创建的” and target's “我的任务—跨部门工作”; do not require target-side acceptance.

## 2. Execution and access boundaries

- [ ] 2.1 Reuse common text updates, completion submission, and direct-dispatcher acceptance/required-reason return.
- [ ] 2.2 Allow target-manager internal work to use ordinary task creation and its own approval; do not create physical task links or expose internal task details to the requester.
- [ ] 2.3 Enforce requester, target assignee, creator-supervisor, and read-only hierarchical access rules in APIs.
- [ ] 2.4 Add in-app reminders for approval, assignment availability, result submission, and acceptance/return.

## 3. Android workflows

- [ ] 3.1 Build Department Manager “创建跨部门任务” form and cross-department filter/list.
- [ ] 3.2 Display approved cross-department work in the target manager's “我的任务—跨部门工作”.
- [ ] 3.3 Reuse standard detail/update/complete/accept/return screens and distinguish the cross-department marker.

## 4. Verification

- [ ] 4.1 Test cross-department role/department validation, supervisor approval, and no target visibility before approval.
- [ ] 4.2 Test target-side execution/delegation, requester isolation from internal tasks, direct-dispatcher acceptance, and return reason.
- [ ] 4.3 Run an end-to-end A → Founder approval → B execution/delegation → A acceptance scenario.

## 5. Prototype scope

- [x] 5.1 Add the local Android prototype's dedicated cross-department task entry, other-department manager selection, common approval flow, and cross-department marker. This does not count as production implementation of sections 1–4.
