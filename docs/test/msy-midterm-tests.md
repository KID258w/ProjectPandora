# 闵世宇：中期个人测试报告

## 1. 基本信息与测试环境

| 项目 | 内容 |
|---|---|
| 测试人员 | 闵世宇（msy410） |
| 关联 Issue | [#42 核心业务测试补充与测试报告整理](https://github.com/KID258w/ProjectPandora/issues/42) |
| 对应 PR | [#46](https://github.com/KID258w/ProjectPandora/pull/46)，目标分支 `dev` |
| 测试日期 | 2026-11-19 |
| 测试分支 | `test/10-midterm-content-audit-views` |
| 基准版本 | `bdff2db`（准备进行初发布版本测试） |
| 测试代码版本 | `445f79203e334f2ce2cdd1f0560b9a6babf0725c`；报告在后续提交更新 |
| 操作系统 | Windows 11，版本 10.0.26200 |
| Python / Django | Python 3.13.5 / Django 5.2.19 |
| 测试框架 | Django TestCase、DRF APITestCase |
| 数据库 | Django 创建的独立内存 SQLite 测试库；未连接部署数据库 |
| App / 浏览器 | 本次未执行人工客户端操作；分工范围为后端 API、业务 helper 和数据快照。Issue 中列出的拖动动画、通知跳转及个人资料退出等人工场景不属于本分工 |
| 需求依据 | Issue #42 闵世宇分工及验收约定；`docs/backend/api-design.md`；`docs/requirements/02-home-four-panels.md`；`docs/requirements/04-month-week-day-work-views.md`；`docs/requirements/05-web-admin-console.md` |

旧 `backend/.venv` 的 `pyvenv.cfg` 指向已不存在的 `D:\python.exe`。为保留原环境，使用 Python 3.13.5 建立独立的 `backend/.venv313` 并按 `requirements.txt` 安装依赖；该目录受 Git 忽略，不提交。

## 2. 范围与核心函数用例映射

以下列出分工表中的每个核心函数/方法和至少 3 个覆盖场景。一个 API 测试可在同一方法中执行多条互相独立的输入场景；场景数和 Django runner 的测试方法数分开统计。专项 `test_midterm_msy_*.py` 为本轮新增，`tests.py`、`test_app_operation_audit.py`、`test_work_views.py` 为保留的既有测试。

| 核心函数／方法 | 正常、边界、异常场景（至少 3） | 测试定位 |
|---|---|---|
| `CompanyItemListView.post()` | 管理员建草稿并写审计；业务账号越权拒绝；未知状态输入拒绝 | `apps.content.tests.CompanyItemApiTests.test_admin_creates_draft_and_business_user_cannot_see_it`、`test_business_user_cannot_manage_items_and_admin_cannot_use_app_api`、`test_unknown_field_and_missing_item` |
| `CompanyItemDetailView.patch()` | 修改字段并保留未提交字段；空 PATCH 拒绝；不支持字段拒绝 | `apps.content.tests.CompanyItemApiTests.test_patch_edits_fields_and_records_audit`、`test_unknown_field_and_missing_item` |
| `missing_publish_fields()` | 全字段完整；单字段为空；多字段空白字符 | `apps.content.test_midterm_msy_services.MidtermMsyContentServiceTests.test_publish_field_check_*`（3 项） |
| `publish_item()` | 发布并设置指定序号/快照；达到 10 条上限拒绝第 11 条；必填字段缺失或重复发布拒绝且不改状态 | `apps.content.tests.CompanyItemApiTests.test_publish_*`（必填、上限、元数据及重复发布） |
| `offline_item()` | 已发布事项下线并保存原因；草稿/已下线状态拒绝下线；下线后重新发布并清空下线信息 | `apps.content.tests.CompanyItemApiTests.test_offline_hides_item_and_keeps_record_for_republish`（包含三种状态迁移） |
| `reorder_items()` | 指定项连续重排；未给出的项按原顺序追加；重复/不存在 ID 拒绝 | `apps.content.test_midterm_msy_services.MidtermMsyContentServiceTests.test_reorder_items_*`（3 项） |
| `CompanyItemOrderView.put()` | 有效排序返回序号；重复/未知 ID 拒绝；空列表拒绝 | `apps.content.tests.CompanyItemApiTests.test_order_endpoint_renumbers_and_drives_app_list_order`、`test_order_rejects_duplicates_and_unknown_ids` |
| `redact()` | 普通对象原样保留；嵌套密码/身份信息脱敏；列表、元组和 None 安全处理 | `apps.accounts.test_midterm_msy_audit_helpers.MidtermMsyAuditHelperTests.test_redact_*`（3 项） |
| `record_audit()` | before/after 和数字 ID 落库；可选快照为空且可记录失败结果；嵌套敏感字段持久化前已脱敏 | `apps.accounts.test_midterm_msy_audit_helpers.MidtermMsyRecordAdminAuditTests.test_record_audit_*`（3 项） |
| `AuditLogListView.get_queryset()` | 按模块、操作者和日期组合筛选；首尾日期包含；无效日期、非正/非法操作者 ID 返回 400 | `apps.accounts.test_midterm_msy_audit_helpers.MidtermMsyAuditLogFilterTests.test_audit_log_queryset_*`（3 项） |
| `AppOperationLogListView.get_queryset()` | 不传操作者返回全部；有效 ID 筛选；0、负数、非数字 ID 拒绝 | `apps.accounts.test_app_operation_audit.AppOperationAuditApiTests.test_admin_list_and_detail_are_read_only_uncached_and_snapshotted` |
| `AppOperationLogDetailSerializer.to_representation()` | 空快照保持空；标签 ID 映射历史名称；负责人映射快照姓名且普通字段保留 | `apps.accounts.test_midterm_msy_audit_snapshots.MidtermMsyAuditSnapshotTests.test_detail_serializer_*`（3 项） |
| `AppOperationLogDetailView` | 未登录拒绝；非管理员拒绝；管理员读取详情、未知记录 404、写方法 405、响应 no-store | `apps.accounts.test_app_operation_audit.AppOperationAuditApiTests.test_admin_list_and_detail_are_read_only_uncached_and_snapshotted` |
| `published_items()` | 无发布项返回空；仅返回发布项并按序；limit 0/1 边界 | `apps.content.test_midterm_msy_services.MidtermMsyContentServiceTests.test_published_items_*`（3 项） |
| `AppCompanyItemListView.get()` | 无发布项；列表排序/发布状态过滤/10 条上限；未认证/管理员权限隔离 | `apps.content.test_midterm_msy_app_items.MidtermMsyAppCompanyItemsTests.test_app_item_list_*`（3 项） |
| `AppCompanyItemDetailView.get()` | 发布内容可读且不泄露管理字段；草稿/下线按不存在处理；未知 ID 404 | `apps.content.test_midterm_msy_app_items.MidtermMsyAppCompanyItemsTests.test_app_item_detail_*`（3 项） |
| `target_user()` | 缺省和本人 ID；授权上级查询下属；错误 ID、越权、无效成员分别拒绝 | `apps.tasks.test_work_views.WorkViewTests.test_permission_date_validation_and_pending_bar`、`test_actor_events_visibility_and_all_selected_days` |
| `month_data()` | 月首/跨月范围与持续任务；无任务日和 3 条溢出；待审批任务过滤及有/无标签颜色 | `apps.tasks.test_work_views.WorkViewTests.test_month_planned_and_original_start_overflow_and_color`、`test_selected_periods_include_completed_and_overdue_tasks_by_span`、`test_task_span_uses_inclusive_business_dates_across_utc_midnight` |
| `all_day_tasks()` | 任务跨选中区间裁剪；开始/截止日端点包含；无匹配/待审批/取消任务排除 | `apps.tasks.test_work_views.WorkViewTests.test_selected_periods_include_completed_and_overdue_tasks_by_span`、`test_task_span_uses_inclusive_business_dates_across_utc_midnight`、`test_permission_date_validation_and_pending_bar` |
| `bar()` | 正常任务条日期；估算开始时间缺省时取创建日期；显式截断日期及无标签默认色 | `apps.tasks.test_work_views.WorkViewTests.test_month_planned_and_original_start_overflow_and_color`、`test_selected_periods_include_completed_and_overdue_tasks_by_span` |
| `color_for()` | 有标签使用颜色；无标签使用中性色；停用标签仍可显示历史颜色 | `apps.tasks.test_work_views.WorkViewTests.test_month_planned_and_original_start_overflow_and_color`（停用标签、有/无标签断言） |
| `events()` | 实际创建/审批/进展/完成事件；未来日期空事件及选区边界；越权任务事件被 ACL 过滤 | `apps.tasks.test_work_views.WorkViewTests.test_actor_events_visibility_and_all_selected_days`、`test_resubmit_acceptance_self_completion_and_hidden_event`、`test_selected_periods_include_completed_and_overdue_tasks_by_span` |
| `calendar_days_for_range()` | 节假日首尾端点；命名节日优先级；调休、未配置年份和反向区间 | `apps.tasks.test_midterm_msy_calendar.MidtermMsyChinaCalendarTests.test_range_*`（3 项） |
| `record_app_operation()` | 自动计算前后差异并落库；显式字段去重；模块白名单/敏感值过滤及长度截断 | `apps.accounts.test_midterm_msy_audit_snapshots.MidtermMsyAuditSnapshotTests.test_record_app_operation_*`（3 项） |
| `actor_snapshot()` | 完整组织关系快照；空名称/无组织关系；姓名长度 100 边界 | `apps.accounts.test_midterm_msy_audit_snapshots.MidtermMsyAuditSnapshotTests.test_actor_snapshot_*`（3 项） |
| `is_sensitive_field()` | 常见凭据和身份字段；普通字段；大小写与子串匹配边界 | `apps.accounts.test_midterm_msy_audit_helpers.MidtermMsyAuditHelperTests.test_sensitive_field_*`（3 项） |
| `_sanitized_values()` | 允许字段与普通值；空值/未知模块；负责人/标签名称快照及敏感字段删除 | `apps.accounts.test_midterm_msy_audit_helpers.MidtermMsyAuditHelperTests.test_*snapshot*`、`apps.accounts.test_midterm_msy_audit_snapshots.MidtermMsyAuditSnapshotTests.test_sanitized_values_*` |
| `user_reference_snapshot()` | None 与存在用户；输入中优先保留历史姓名；不存在/非法用户 ID 回退未知成员 | `apps.accounts.test_midterm_msy_audit_snapshots.MidtermMsyAuditSnapshotTests.test_user_reference_snapshot_*`（3 项） |
| `tag_name_snapshots()` | 旧式 ID 查名且保持顺序；已有历史名称不被覆盖；未知/异常 ID 使用回退名称 | `apps.accounts.test_midterm_msy_audit_snapshots.MidtermMsyAuditSnapshotTests.test_tag_name_snapshots_*`（3 项） |

**复用的其他既有覆盖：** Web 公司事项所有管理写入口的审计一致性在 `apps.content.tests.CompanyItemApiTests.test_all_management_mutations_are_auditable`；App 操作日志登录、资料、任务、日志、标签快照和事务回滚在 `apps.accounts.test_app_operation_audit.AppOperationAuditApiTests`；月/周/日视图 ACL 和事件归属在 `apps.tasks.test_work_views.WorkViewTests`。

### 2.1 等价类与边界分析

- 事项状态：draft/published/offline；发布数量 9/10/11；必填字段正常、单空、多空白；排序正常、空、重复和不存在 ID。
- 审计筛选：模块与操作者匹配/不匹配；日期区间端点和非法日期；管理员/业务账号/未登录；快照为空/有值/含敏感键。
- 审计快照：白名单字段/未知字段；存在/不存在关联用户或标签；字符串 ID/非法 ID；历史名称与当前名称不同。
- 工作视图：本人/授权下属/越权目标；月首月尾、周首周尾、跨业务时区午夜；日期范围无交集/只交一端/覆盖全段；无任务/3 条以内/超出 3 条；有标签/无标签/停用标签。
- 节假日：已配置年份、未配置年份；休假首尾、节日专名、调休日期、反向区间、跨年。

## 3. 新增自动化用例

| 专项组 | 新增方法数 | 覆盖内容 |
|---|---:|---|
| `apps.tasks.test_midterm_msy_calendar` | 3 | 节假日区间、名称覆盖、未配置年份/反向区间 |
| `apps.content.test_midterm_msy_services` | 9 | 发布字段校验、已发布列表、事项重排，各 3 项 |
| `apps.accounts.test_midterm_msy_audit_helpers` | 17 | 脱敏、字段变化、白名单、管理员审计写入、管理员审计查询筛选 |
| `apps.accounts.test_midterm_msy_audit_snapshots` | 21 | 操作者/用户/标签快照、JSON 转换、App 审计写入、详情序列化 |
| `apps.content.test_midterm_msy_app_items` | 6 | App 公司事项列表与详情接口，每个入口 3 项 |

每个新测试文件均在独立 Django 测试数据库中执行。完整命令和最终实际统计在测试通过后写入 [执行证据](evidence/msy/msy-midterm-run.txt)。专项用例编号按上表分组顺序记为 `TC-MSY-001` 至 `TC-MSY-056`；在最终统计中同时给出新增设计方法数、专项场景数和 Django runner 的运行方法数，避免把一个测试方法中的 `subTest` 重复计为 runner 测试。

## 4. 执行过程和证据

环境修复与依赖准备见上文。所有自动化测试使用 Django 测试运行器创建的 SQLite 内存库；未对本地或部署数据库写入。

```powershell
cd backend
.\.venv313\Scripts\python.exe manage.py test apps.tasks.test_midterm_msy_calendar --noinput --verbosity 2
.\.venv313\Scripts\python.exe manage.py test apps.content.test_midterm_msy_services apps.content.test_midterm_msy_app_items apps.accounts.test_midterm_msy_audit_helpers apps.accounts.test_midterm_msy_audit_snapshots --noinput --verbosity 1
.\.venv313\Scripts\python.exe manage.py test --noinput --verbosity 1
```

结果证据：[msy-midterm-run.txt](evidence/msy/msy-midterm-run.txt)。

## 5. 结果、缺陷与限制

- 新增专项测试文件共 5 个，56 个 Django 测试方法；专项运行结果 56 通过、0 失败、0 错误、0 跳过。
- 完整后端测试套件发现并执行 153 个测试方法，153 通过、0 失败、0 错误、0 跳过；最终运行时间 32.352 秒，Django system check 无问题。
- 本次执行未单独计数每个测试方法中的 `subTest` 场景；每个分配到的核心函数／方法覆盖场景见第 2 节映射表。
- 手动客户端用例：0；本分工未包含 Issue 指定的拖动动画、通知跳转和资料退出登录场景。
- 业务实现没有修改；只新增测试文件、报告和执行证据。
- 本次范围内没有登记已复现的业务缺陷。专项用例首次运行中发现一条测试 fixture 违反用户组织关系数据库约束，已修正 fixture 并重跑通过，归类为测试准备问题。
- 完整测试运行中 drf-spectacular 输出重复 schema 名称 `CalendarDay` 警告，来源为工作视图和日志模块中同名 serializer。相关测试通过；该文档 schema 风险未在测试任务中擅自修复，需另行确认。
- 手动客户端测试未执行。Issue 指定的拖动动画、通知跳转和资料修改后退出登录不在闵世宇的分工范围；本报告不把后端自动化结果表述成客户端验收通过。
- 代码覆盖率未生成。本轮验收要求是函数与用例映射和测试执行结果，Issue 将覆盖率列为建议项。
- Issue #42 中央飞书中期报告由曲懿涵汇总，本 PR 只提交闵世宇个人报告及测试证据。

## 6. AI 辅助和人工复核记录

**工具：** Codex。**日期：** 2026-11-19。**用途：** 阅读 Issue #42 分工和验收要求，梳理后端实现及既有测试，辅助起草新增自动化测试与报告。

**实际任务提示词摘要：**“你帮我按照 issue 完成我的分工”；“那就继续完成，运行测试，看不到结果就去解决”；“你就全完成然后再提交”。依据 Issue #42 只新增测试，不修改业务实现。

人工逐项复核：测试 fixture 必须满足数据库中的有效账号组织约束；用户/标签回退值与快照输入按 helper 实现及 Issue 的历史快照要求核对；事项列表仅暴露 published 状态且摘要不包含正文/管理字段；工作视图日期范围使用业务时区端点；每个新断言检查返回值或数据库状态。初次失败的 fixture 已调整，修正后专项测试通过。

## 7. 结论与交接

测试结论以 [执行证据](evidence/msy/msy-midterm-run.txt) 的最终完整套件结果为准。PR #46 目标为 `dev`，包含新增自动化测试、个人报告和原始精简输出。报告不代表全组 Issue 关闭；其他三位成员的分工和飞书汇总由各自负责人完成。
