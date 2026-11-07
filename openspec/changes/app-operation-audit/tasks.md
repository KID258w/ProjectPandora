## 1. Backend audit records

- [x] 1.1 Add the App operation audit entity, migration, indexes, actor snapshots and target label snapshots.
- [x] 1.2 Record successful login and supported App write events with allowlisted changed fields and before/after values in the business transaction.
- [x] 1.3 Exclude failed/read-only/exempt operations and redact passwords, full identity numbers, tokens and avatar binary data.
- [x] 1.4 Add backend tests for event coverage, snapshot accuracy, atomicity, redaction and failure paths.
- [x] 1.5 Preserve tag association IDs and operation-time names in App audit before/after snapshots, and resolve legacy numeric-only values on read.
- [x] 1.6 Preserve task assignee IDs and operation-time names in App audit before/after snapshots, and resolve legacy numeric-only values on read.

## 2. Administrator API

- [x] 2.1 Add a paginated newest-first list endpoint with employee filtering and summary-only fields.
- [x] 2.2 Add a read-only detail endpoint that returns the selected record's before/after values; require Web administrator access and disable caching.
- [x] 2.3 Document the endpoints in OpenAPI/Swagger and verify non-administrator access is denied.

## 3. Web operation history page

- [x] 3.1 Add a separate Employee App Operation History page with global latest records and employee filtering.
- [x] 3.2 Add record detail showing actor/object snapshots and changed field values without restore, edit, or export actions.
- [x] 3.3 Verify ordinary task and employee-log browsing remains unavailable in Web.
- [x] 3.4 Render audit tag-association values as names with IDs in the Web record detail.
- [x] 3.5 Render task assignee audit values as names with IDs in the Web record detail.

## 4. Contracts and verification

- [x] 4.1 Update the Web requirements, backend API contract and database design for the confirmed scope.
- [x] 4.2 Create the proposal, capability specification and technical design for this change.
- [ ] 4.3 Run relevant backend/Web checks and review access-control and data-retention behavior before release.

## 验证记录（PR 1 后端，2026-11-04）

- `python manage.py check`：通过。
- `python manage.py makemigrations --check --dry-run`：无漏写迁移；新增迁移 `accounts.0013_appoperationauditlog`。
- `python manage.py spectacular --file <tmp>`：Schema 生成成功，0 error（仅保留既有 `OrganizationTreeNodeSerializer.get_children` 类型提示告警）。
- `python manage.py test apps.accounts.test_app_operation_audit`：9 项通过，覆盖成功/失败登录、员工与组织快照、字段白名单与脱敏、任务/日志/标签事件、写入事务回滚、管理员访问控制、分页与员工筛选、`Cache-Control: no-store`。
- `python manage.py test`：与 `dev` 合并后 89 项全部通过。

## 验证记录（PR 2 Web，2026-11-04）

- `cd web && npm run build`：通过（Vite 提示主 bundle 超过 500 kB）。
- `git diff --check`：通过。
- 路由审查：仅增加受管理员路由保护的员工 App 操作记录页面；未增加任务/日志浏览、写入、导出或恢复入口。
- OpenSpec 严格校验：未完成，npm registry 域名解析失败（`ENOTFOUND`）。
- 真实 API 联调：随后端 PR #36 合入 `dev` 后完成；后端实现已包含该 PR。
