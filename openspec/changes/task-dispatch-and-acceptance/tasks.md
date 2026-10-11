## 1. Task domain and authorization

- [ ] 1.1 Implement standalone task records with one creator/assignee, required title/details/deadline, optional expected-deliverable text, and optional non-relational association note.
- [ ] 1.2 Implement server-side assignment validation for Founder, Department Manager, Team Leader, and Employee scopes.
- [ ] 1.3 Implement direct-supervisor lookup and approval records, including pending queues, required return reason, edit/resubmit, and reapproval for key changes.
- [ ] 1.4 Enforce creator, assignee, direct-supervisor, direct-dispatcher, and read-only hierarchical visibility for task APIs.

## 2. Execution and lifecycle

- [ ] 2.1 Implement binary completion status and append-only text updates with author/timestamp; do not implement percentage progress or parent-task aggregation.
- [ ] 2.2 Implement the separate completion-submission form and record; validate deliverable information only when the task specified expected deliverables.
- [ ] 2.3 Implement direct-dispatcher accept/return, required return reason, and resubmission; self-assigned tasks are completed by their assignee without self-acceptance.
- [ ] 2.4 Implement task-definition editing, reapproval for key post-approval changes, and auditable edit history.
- [ ] 2.5 Implement reason-required cancellation of only the direct-dispatcher's unfinished task; preserve the record and do not cascade.
- [ ] 2.6 Implement in-app reminders for approval, return, assignment, completion submission, acceptance/return, and cancellation.

## 3. Android task workflows

- [ ] 3.1 Build ordinary task-center lists for approved “我的任务” and “我创建的”; keep approval queues out of these lists.
- [ ] 3.2 Build unified creation form with role-bounded single assignee and optional association note; allow employees to create self-assigned work.
- [ ] 3.3 Build a separate Approval Center with role-appropriate queues, reasoned return, edit/resubmit, and key-change reapproval interactions.
- [ ] 3.4 Build task detail, text-update history, completion submission, direct-dispatcher acceptance/return, and self-completion flows.
- [ ] 3.5 Build read-only subordinate-task inspection without granting workflow actions; limit selectable objects to authorized descendants.
- [ ] 3.6 Add unfinished/completed filters only to execution/inspection lists and an authorized-descendant object selector with employee-name search to subordinate-task inspection; show tasks assigned to the selected person only, and do not show completion filters in approval queues.

## 4. Verification

- [ ] 4.1 Test assignment matrix, creator-supervisor approval, Founder bypass, pre-approval visibility, self-assignment, and out-of-scope access denial.
- [ ] 4.2 Test text-only updates, distinct create/complete forms, deliverable validation, return reason, acceptance, and self-completion.
- [ ] 4.3 Test key-change reapproval, cancellation reason, no cancellation cascade, and retention of audit history.
- [ ] 4.4 Run end-to-end Founder → Department Manager → Team Leader → Employee approval, execution, and acceptance scenarios.

## 5. Prototype scope

- [x] 5.1 Update the local Android demo task center to demonstrate the confirmed task lists, approval, unified creation, text association, execution update, completion submission, and cross-department entry. This prototype item does not count as production implementation of sections 1–4.
- [x] 5.2 Update the local Android demo task lists with completion filters and subordinate object selection with employee-name search; show assigned tasks only. This prototype item does not count as production implementation of sections 1–4.
- [x] 5.3 Separate the prototype Approval Center from ordinary task lists, hide completion filters there, and show creator tasks in “我创建的” only after approval (Founder bypasses approval). This prototype item does not count as production implementation of sections 1–4.
