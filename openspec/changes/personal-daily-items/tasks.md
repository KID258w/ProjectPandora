## 1. Requirements and OpenSpec

- [x] 1.1 Add the personal daily-items product requirement and update the Home-panel/work-view boundaries.
- [x] 1.2 Define the owner-scoped item, date-range, color, overflow-list, detail-popup, edit/delete, drag, no-repeat, and audit-exclusion scenarios in OpenSpec.

## 2. Compose Demo prototype

- [x] 2.1 Replace the Home “个人重点工作” panel and personal focus page with “每日事项” and a separate personal month calendar.
- [x] 2.2 Add in-memory personal-item samples and Home filtering for items covering the demo's current date, capped at five.
- [x] 2.3 Implement calendar bars, date/+N day list, colored cards, and the floating item detail card.
- [x] 2.4 Implement create/edit form with title, inclusive start/end dates, optional description, and ten visible color swatches.
- [x] 2.5 Implement confirmation before deletion and immediate UI updates after create/edit/delete.
- [x] 2.6 Implement long-press drag that shifts both dates by the same delta and preserves the original duration.
- [x] 2.7 Keep daily-item interactions local to the demo and outside company tasks, work views, and audit data.
- [x] 2.8 Add the quick year/month selector to the personal calendar header.
- [x] 2.9 Make dragged item bars visibly follow the pointer, lift during movement, and settle with a spring response.

## 3. Review

- [ ] 3.1 Review the prototype on the Home and personal calendar paths, including empty and crowded dates, edits, deletion cancellation/confirmation, and moving a multi-day item.
- [x] 3.2 Review the Compose demo changes to ensure no production Android, Web, backend, or API contract files were modified.
