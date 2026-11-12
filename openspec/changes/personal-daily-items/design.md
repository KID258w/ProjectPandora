## Context

The existing Home panel called “个人十大重点工作” is a manually ordered subset of company tasks. The product decision replaces it with personal memo items, so the item model and calendar must remain separate from `Task`, work-view events, logs, and audit records.

## Goals / Non-Goals

**Goals:** Define an owner-scoped daily-item experience; show date-range bars in a month calendar; make crowded dates discoverable; demonstrate create, edit, delete, detail, and drag interactions in the local Compose prototype.

**Non-Goals:** Production APIs or database schema, server persistence, cross-device sync, Web management, recurrence, task workflow states, tags, or changes to company task/work-view behavior.

## Decisions

- A personal item has a required title, inclusive start and end dates, optional description, and one editable color from the existing ten-color tag palette. The end date must not precede the start date.
- The item is owned by the authenticated account. The eventual production service must enforce owner scoping, but this proposal does not invent API paths or database columns.
- Home previews up to five of the current account's items whose date range includes today. The Home panel only navigates to the personal calendar; creation begins on that calendar.
- The personal calendar is a separate page from company Month/Week/Day work views. A long item is rendered in date-range bars, split visually at week boundaries. A crowded date shows “+N项”; tapping either the date or that affordance opens a list of all items covering the date.
- Both a calendar bar and a date-list card open a floating details card. Editing reuses the item form. Deletion requires confirmation.
- Dragging anchors on the date under the initial touch. On drop, both endpoints shift by the same day delta. Dragging does not resize an item.
- Daily items have no completion/overdue/approval state and do not repeat automatically. Their creation, edits, and deletion do not create employee App or Web management audit records.
- The Compose demo stores changes in local in-memory state only. It demonstrates the UI flow; production persistence, server authorization, and multi-device behavior are outside this prototype.

## Alternatives Considered

- Reusing company `Task` records was rejected because daily items are self-created memos without company assignment, approval, completion, or acceptance workflow.
- Reusing the company work-view calendar was rejected because those views present authorized company task periods and task events; a personal memo calendar has different ownership and content.
- Automatic recurrence was excluded from the first version; multi-day duration is represented by one inclusive date range.

## Risks / Trade-offs

- [Users may confuse daily items with company tasks] → label the Home panel and month page “每日事项” and keep its data, calendar, and interactions separate.
- [Crowded month cells can hide items] → keep visible bars plus “+N项”; both that affordance and the date open the full day list.
- [Drag can be mistaken for resizing] → move both endpoints by one shared date delta and explain that duration changes happen in the edit form.
- [Local demo state is not durable] → label this as prototype behavior; production persistence requires a separately scoped backend implementation.

## Migration Plan

The Compose demo replaces its old personal-task-ordering panel with personal daily items backed by in-memory sample state. This does not migrate production personal-task ordering, company tasks, or database data. Production data/API migration is not part of this prototype change.
