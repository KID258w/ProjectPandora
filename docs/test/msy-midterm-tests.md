# 闵世宇：中期个人测试报告

## 1. 基本信息与测试环境

| 项目 | 内容 |
|---|---|
| 测试人员 | 闵世宇（msy410） |
| 关联 Issue | [#42 核心业务测试补充与测试报告整理](https://github.com/KID258w/ProjectPandora/issues/42) |
| 对应 PR | 待创建 |
| 测试日期 | 2026-11-19 |
| 测试分支 | `test/10-midterm-content-audit-views` |
| 被测版本 | `bdff2db`（准备进行初发布版本测试） |
| 测试代码版本 | 本地改动，尚未提交 |
| 操作系统 | Windows |
| Python / Django | Python 3.13.5 / Django 5.2.19 |
| 测试框架 | Django TestCase / DRF APITestCase |
| 数据库 | Django 自动创建的独立内存 SQLite 测试库 |
| App / 浏览器环境 | 本次未进行手动客户端测试 |
| 需求依据 | Issue #42；`docs/backend/api-design.md`；`docs/requirements/02-home-four-panels.md`；`docs/requirements/05-web-admin-console.md`；`docs/requirements/04-month-week-day-work-views.md` |

## 2. 测试范围与核心函数映射

本轮复用并审查已有接口测试，新增节假日范围用例。下表列出本次执行的测试文件与功能映射；现有测试以接口集成方式覆盖多层函数，尚未为分工表中每一个核心函数单独建立 3 条直接函数测试，见未覆盖说明。

| 功能 | 核心范围 | 测试层级与文件 | 代表性测试 |
|---|---|---|---|
| 公司事项创建/编辑/发布/下线/排序/App 可见性 | `content.views`、`content.services` | 接口集成：`backend/apps/content/tests.py` | draft、patch/audit、required fields、10 条上限、publish metadata、offline/re-publish、order、App list/limit/order |
| 管理审计 | `accounts.audit.redact/record_audit`、审计筛选 | 接口集成：`backend/apps/accounts/tests.py`、`backend/apps/content/tests.py` | `test_audit_logs_never_contain_sensitive_data`、`test_all_management_mutations_are_auditable`、内容修改前后审计 |
| App 操作审计 | `app_audit` 快照、白名单、脱敏、事务；管理员列表/详情 | 接口集成：`backend/apps/accounts/test_app_operation_audit.py` | 登录成功/失败、资料脱敏、任务/日志/标签审计、事务回滚、只读和管理员权限、分页与 no-store |
| 月/周/日工作视图 | `work_views.target_user/month_data/all_day_tasks/events/bar/color_for` | 接口集成：`backend/apps/tasks/test_work_views.py` | 日期范围相交、跨 UTC 午夜、溢出、颜色、动态归属/可见性、无权访问 |
| 节假日与调休 | `china_calendar.calendar_days_for_range` | 单元测试：`backend/apps/tasks/test_midterm_msy_calendar.py` | TC-MSY-001 至 TC-MSY-003 |
| 发布必填校验 | `content.services.missing_publish_fields` | 单元测试：`backend/apps/content/test_midterm_msy_services.py` | 完整内容、单字段缺失、空白多字段 |
| 已发布列表 | `content.services.published_items` | 同上 | 空列表、状态过滤/排序、limit 0/1 |
| 事项排序 | `content.services.reorder_items` | 同上 | 正常重排、遗漏项顺延、重复/无效 ID |
| 审计辅助函数 | `accounts.audit.redact`、`app_audit.is_sensitive_field`、`_changed_keys`、`_sanitized_values` | 单元测试：`backend/apps/accounts/test_midterm_msy_audit_helpers.py` | 每函数包含正常与边界/敏感输入断言 |

### 2.1 等价类与边界设计

- 公司事项：完整/缺失发布字段；已发布数小于/达到 10；正常、重复、缺失 ID 的顺序请求；已发布/下线/草稿状态。
- 审计：允许/敏感字段；成功/失败操作；有/无前后值；管理员/业务用户/匿名访问；存在/不存在记录。
- 工作视图：本人/授权下属/越权用户；日期边界内/外、跨月、跨业务时区日期；无任务/有任务/溢出；有标签/无标签。
- 节假日：节期首尾端点、命名节日在连续休息段内的标签优先级、调休日期、未配置年份和反向区间。

## 3. 新增自动化用例

| 用例 | 核心方法 | 类型 | 输入与预期 | 自动化定位 | 实际结果 |
|---|---|---|---|---|---|
| TC-MSY-001 | `calendar_days_for_range` | 边界 | 查询 2026 元旦首尾，结果包含 1 月 1–3 日且按日期排序 | `MidtermMsyChinaCalendarTests.test_range_includes_both_holiday_endpoints` | 通过 |
| TC-MSY-002 | `calendar_days_for_range` | 正常/优先级 | 查询 2025-10-05 至 10-07；10-06 显示中秋节专名，前后休息日仍为休息标记 | `MidtermMsyChinaCalendarTests.test_named_holiday_overrides_generic_rest_day_label` | 通过 |
| TC-MSY-003 | `calendar_days_for_range` | 异常/空结果边界 | 未配置年份及反向日期区间返回空列表 | `MidtermMsyChinaCalendarTests.test_range_returns_empty_for_unconfigured_year_and_reversed_interval` | 通过 |

## 4. 执行过程与证据

旧 `backend/.venv` 的 `pyvenv.cfg` 指向已不存在的 `D:\python.exe`，导致 `ModuleNotFoundError: config`。为避免覆盖旧环境，我创建独立 `backend/.venv313` 并按 `requirements.txt` 安装依赖。该虚拟环境已被仓库忽略，不纳入提交。

```powershell
cd backend
.\.venv313\Scripts\python.exe manage.py test apps.tasks.test_midterm_msy_calendar --noinput --verbosity 2
# Ran 3 tests — OK

.\.venv313\Scripts\python.exe manage.py test apps.content apps.accounts.tests apps.accounts.test_app_operation_audit apps.tasks.test_work_views apps.tasks.test_midterm_msy_calendar --noinput --verbosity 2
# Found 65 test(s); Ran 65 tests — OK; System check identified no issues.
```

执行摘要：[msy-midterm-run.txt](evidence/msy/msy-midterm-run.txt)

自动化统计：本人范围相关测试发现并执行 65 条，65 通过，0 失败，0 错误，0 跳过。新增设计用例 21 条（节假日 3、内容服务 9、审计辅助 9），均通过。随后运行完整后端套件：118 条通过，0 失败，0 错误，0 跳过。

## 5. 结果与问题

- 本次执行范围内，公司事项状态/发布上限/排序/权限、审计敏感信息与事务、工作视图日期和权限等已有集成测试通过；完整后端测试套件也通过。
- Swagger schema 检查测试通过，但输出 `CalendarDay` serializer 名称冲突警告（与 `apps.logs.serializers.CalendarDaySerializer` 重名）。这是测试发现的文档风险，尚未登记为业务缺陷，也未修改业务代码。
- 尚未为分工表中每个 API view 方法、工作视图 helper 和全部 App 事项/审计入口逐一映射 3 条独立正常、边界、异常测试；现有接口集成测试覆盖其中多项，但尚未完成逐函数计数。这是剩余验收缺口，不能视为全部通过。
- 手动客户端用例未执行；没有可用的统一部署版本/测试账号流程记录供本次复现。
- App 操作审计/工作视图的专项测试由已有模块测试覆盖，但未对所有列出的共享纯函数逐项进行 3 场景直测。

## 6. 结论与交接

本轮复用的 65 条后端范围测试、新增 21 条 helper 用例和完整后端 118 条测试均通过。剩余 API/helper 的逐函数场景映射、手动客户端验证和 Swagger 告警处理仍待补充。业务代码未修改，发现的问题按 Issue 要求记录，未擅自修复。

### AI 辅助与人工复核

- 工具：Codex。日期：2026-11-19。用途：整理 Issue 分工、起草节假日单元测试与报告。
- 人工复核：逐项核对 `calendar_days_for_range` 的实际常量和分支，确认用例输入属于已配置节期、未配置年份及反向区间；断言验证日期、名称、标签和空结果。
- 本报告中的 65 条测试数和命令结果来自本轮实际执行输出；未执行内容明确标记为未完成。
