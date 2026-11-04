## 1. Backend audit records

- [ ] 1.1 Add the App operation audit entity, migration, indexes, actor snapshots and target label snapshots.
- [ ] 1.2 Record successful login and supported App write events with allowlisted changed fields and before/after values in the business transaction.
- [ ] 1.3 Exclude failed/read-only/exempt operations and redact passwords, full identity numbers, tokens and avatar binary data.
- [ ] 1.4 Add backend tests for event coverage, snapshot accuracy, atomicity, redaction and failure paths.

## 2. Administrator API

- [ ] 2.1 Add a paginated newest-first list endpoint with employee filtering and summary-only fields.
- [ ] 2.2 Add a read-only detail endpoint that returns the selected record's before/after values; require Web administrator access and disable caching.
- [ ] 2.3 Document the endpoints in OpenAPI/Swagger and verify non-administrator access is denied.

## 3. Web operation history page

- [x] 3.1 Add a separate Employee App Operation History page with global latest records and employee filtering.
- [x] 3.2 Add record detail showing actor/object snapshots and changed field values without restore, edit, or export actions.
- [x] 3.3 Verify ordinary task and employee-log browsing remains unavailable in Web.

## 4. Contracts and verification

- [x] 4.1 Update the Web requirements, backend API contract and database design for the confirmed scope.
- [x] 4.2 Create the proposal, capability specification and technical design for this change.
- [ ] 4.3 Run relevant backend/Web checks and review access-control and data-retention behavior before release.

## 验证记录（PR 2 Web，2026-11-04）

- `cd web && npm run build`：通过（Vite 提示主 bundle 超过 500 kB）。
- `git diff --check`：通过。
- 路由审查：仅增加受管理员路由保护的员工 App 操作记录页面；未增加任务/日志浏览、写入、导出或恢复入口。
- OpenSpec 严格校验：未完成，npm registry 域名解析失败（`ENOTFOUND`）。
- 真实 API 联调：待后端 PR #32 合入并启动后端后完成；当前 PR #32 尚未合入 `dev`。
