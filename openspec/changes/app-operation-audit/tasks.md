## 1. Backend audit records

- [x] 1.1 Add the App operation audit entity, migration, indexes, actor snapshots and target label snapshots.
- [x] 1.2 Record successful login and supported App write events with allowlisted changed fields and before/after values in the business transaction.
- [x] 1.3 Exclude failed/read-only/exempt operations and redact passwords, full identity numbers, tokens and avatar binary data.
- [x] 1.4 Add backend tests for event coverage, snapshot accuracy, atomicity, redaction and failure paths.

## 2. Administrator API

- [x] 2.1 Add a paginated newest-first list endpoint with employee filtering and summary-only fields.
- [x] 2.2 Add a read-only detail endpoint that returns the selected record's before/after values; require Web administrator access and disable caching.
- [x] 2.3 Document the endpoints in OpenAPI/Swagger and verify non-administrator access is denied.

## 3. Web operation history page

- [ ] 3.1 Add a separate Employee App Operation History page with global latest records and employee filtering.
- [ ] 3.2 Add record detail showing actor/object snapshots and changed field values without restore, edit, or export actions.
- [ ] 3.3 Verify ordinary task and employee-log browsing remains unavailable in Web.

## 4. Contracts and verification

- [x] 4.1 Update the Web requirements, backend API contract and database design for the confirmed scope.
- [x] 4.2 Create the proposal, capability specification and technical design for this change.
- [ ] 4.3 Run relevant backend/Web checks and review access-control and data-retention behavior before release.

## 验证记录（PR 1 后端，2026-11-04）

- `python manage.py check`：通过。
- `python manage.py makemigrations --check --dry-run`：无漏写迁移；新增迁移 `accounts.0013_appoperationauditlog`。
- `python manage.py spectacular --file <tmp>`：Schema 生成成功，0 error（仅保留既有 `OrganizationTreeNodeSerializer.get_children` 类型提示告警）。
- `python manage.py test apps.accounts.test_app_operation_audit`：9 项通过，覆盖成功/失败登录、员工与组织快照、字段白名单与脱敏、任务/日志/标签事件、写入事务回滚、管理员访问控制、分页与员工筛选、`Cache-Control: no-store`。
- `python manage.py test`：85 项中 84 项通过；剩余 1 项 `apps.tasks.tests.TaskApiTests.test_subordinate_scope_cross_department_and_notification_read_range` 为 `dev` 基线既有失败（未改动任务通知逻辑前复现），不在本 change 范围内。
