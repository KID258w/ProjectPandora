## 1. Task domain and authorization

- [ ] 1.1 Implement task hierarchy, direct-dispatch/assignee relations, task definition history, progress history, and acceptance/cancellation records.
- [ ] 1.2 Implement server-side role, organization, direct-dispatch, and task-tree access checks for all task APIs.
- [ ] 1.3 Implement creation and next-level assignment rules for Founder, Department Manager, Team Leader, and Employee.

## 2. Execution and lifecycle

- [ ] 2.1 Implement employee start/progress updates and manager first-open transition, with progress history.
- [ ] 2.2 Implement manager-owned child-task completion marker without percentage or self-acceptance.
- [ ] 2.3 Implement completion submission, direct-dispatcher accept/return, required return reason, and resubmission.
- [ ] 2.4 Implement task-definition editing restrictions and auditable change history.
- [ ] 2.5 Implement reason-required cancellation and atomic downward cancellation of unfinished descendants only.
- [ ] 2.6 Implement manual parent progress/finalization rules without automatic child-progress aggregation.
- [ ] 2.7 Implement in-app red-dot reminders for assignment, submission, acceptance/return, edits, and cancellation.

## 3. Android task workflows

- [ ] 3.1 Build employee task list/detail/start/progress/submit/history workflows.
- [ ] 3.2 Build Team Leader and Department Manager "My Tasks" and "Dispatched by Me" workflows, including self-owned child tasks.
- [ ] 3.3 Build Founder task creation/department assignment and direct acceptance workflows.
- [ ] 3.4 Display task return/cancellation reasons and unread indicators without exposing unauthorized descendant details.

## 4. Verification

- [ ] 4.1 Test role boundaries, next-level assignment, direct-dispatch acceptance, state transitions, and invalid transitions.
- [ ] 4.2 Test edit restrictions, required reasons, cancellation cascade, completed-descendant preservation, and parent/sibling isolation.
- [ ] 4.3 Test progress history, manual parent progress, self-owned child completion, reminders, and API denial for out-of-scope tasks.
- [ ] 4.4 Document and run an end-to-end task dispatch, execution, and acceptance scenario across role levels.
