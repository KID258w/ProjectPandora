## Why

Departments need a controlled way to request work from one another. Product-owner review settled on using the existing task-creation and approval flow: a Department Manager may assign an ordinary task to a Department Manager in another department. This is not a separate task type or request/acceptance workflow.

## What Changes

- Extend the Department Manager's permitted assignee choices to include other Department Managers, while retaining self and same-department Team Leader choices.
- Create and approve these assignments through the ordinary task endpoint and the creator's direct-supervisor approval flow.
- After approval, show the task in the creator's “我创建的” and the assignee's “我的任务”; no second acceptance is needed.
- Preserve direct-dispatcher acceptance and prevent the creator from inspecting the receiving department's internal tasks.
- Do not add a cross-department task flag, special entry, special filter, separate API endpoint, or physical link to internal tasks.

## Capabilities

### New Capabilities
- `cross-department-coordination`: Permission and visibility rules for ordinary task assignments between Department Managers in different departments.

### Modified Capabilities
- `task-dispatch-and-acceptance`: Department Manager assignee scope includes other Department Managers, using the unified task flow.
- `home-dashboard-panels`: Tasks assigned by other Department Managers use the ordinary “他人派发” source category, not a cross-department category.
- `month-week-day-work-views`: Month-view colors distinguish “他人派发” and “本人创建” only.

## Impact

- Source requirement: `docs/requirements/01-task-creation-and-acceptance.md`.
- Related requirements: `docs/requirements/02-home-four-panels.md` and `docs/requirements/04-month-week-day-work-views.md`.
- Affects ordinary task assignee validation, creator-supervisor approval, task visibility, task-center lists, home ordering, and month-view source colors.
- Does not introduce a separate task model, API, or state machine.
