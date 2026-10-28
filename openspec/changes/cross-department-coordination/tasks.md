## 1. Ordinary task creation and approval

- [x] 1.1 Extend the unified assignable-user query for Department Managers to include other Department Managers in the same company.
- [x] 1.2 Validate at the server that a Department Manager may assign only to themself, a Team Leader in their department, or a Department Manager in another department; reject same-department peers and out-of-scope roles.
- [x] 1.3 Use the existing task-create API and creator's direct-supervisor approval flow; do not add a dedicated endpoint or task-type field.
- [x] 1.4 After approval, expose the task in the creator's “我创建的” and assignee's “我的任务”; prevent assignee visibility/execution before approval and do not require a second acceptance.

## 2. Execution and access boundaries

- [x] 2.1 Reuse common text updates, completion submission, and direct-dispatcher acceptance/required-reason return.
- [x] 2.2 Allow assignee's internal work to use ordinary task creation and its own approval; do not create physical task links or expose internal task details to the requester.
- [x] 2.3 Enforce requester, assignee, creator-supervisor, and read-only hierarchical access rules in APIs.
- [x] 2.4 Use creator, assignee, and organization details for task context; do not persist or expose a cross-department task marker or filter.
- [ ] 2.5 Add in-app reminders for approval, assignment availability, result submission, and acceptance/return.
- [x] 2.6 Implement backend lifecycle notifications for cross-department result submission, acceptance, return, and cancellation through the shared task notification model.

## 3. Android workflows

- [ ] 3.1 Use the ordinary task-creation form and assignee picker for Department Manager-to-Department Manager assignment; remove dedicated entry/filter and marker display.
- [ ] 3.2 Display approved assignments in ordinary “我的任务” and “我创建的” lists.
- [ ] 3.3 Reuse standard detail/update/complete/accept/return screens.

## 4. Verification

- [ ] 4.1 Test the permitted other-department manager assignment, reject same-department peers and out-of-scope roles, and verify creator-supervisor approval and pre-approval invisibility.
- [ ] 4.2 Test assignee execution/delegation, requester isolation from internal tasks, direct-dispatcher acceptance, and return reason.
- [ ] 4.3 Run an end-to-end A → Founder approval → B execution/delegation → A acceptance scenario.

## 5. Prototype scope

- [x] 5.1 Update the local Android prototype to use one task-creation entry for all roles, permit a Department Manager to select another department's manager, apply the ordinary approval flow, and remove cross-department type/filter markers. This does not count as production implementation of sections 1–4.
