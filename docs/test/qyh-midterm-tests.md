# 曲懿涵中期测试报告

## 1. 基本信息与测试环境

| 项目 | 内容 |
| --- | --- |
| 测试人员 | 曲懿涵（@AsuBTTS）。Codex 辅助实现与执行，人工逐条复核待完成。 |
| 关联 Issue | [测试10（#42）](https://github.com/KID258w/ProjectPandora/issues/42) |
| 对应 PR | [PR #45](https://github.com/KID258w/ProjectPandora/pull/45) |
| 测试日期 | 2026-11-19，Asia/Taipei |
| 测试分支 | `test/10-midterm-logs-tags-personal-items` |
| 被测基准 | `dev` 的 `bdff2dba6984042d64bf0f2ef99130ba3552a1b9`。开始时核对该 SHA 与远端一致，团队共同验收版本待统一。 |
| 测试代码版本 | `da96a8fc67fdfe010fc1a115b9f6e5d40918f466`。运行时新增用例在工作树中，最终提交的测试文件内容与通过的版本一致。 |
| 操作系统 | Windows 11 家庭版，10.0.26200 |
| Python / Django / DRF | Python 3.11.9，Django 5.2.19，DRF 3.18.3 |
| 测试框架 | Django / DRF `APITestCase`，含直接函数或序列化器调用和真实 Django 路由/API 请求。新增用例用 `force_authenticate` 注入身份，现有回归另有 JWT 测试。 |
| 数据库 | SQLite 3.45.1，Django 为每轮创建并销毁独立内存测试库。 |
| Android 环境 | Medium_Phone 模拟器，Android SDK 37。Android 证据见 §4.3。 |
| 需求依据 | [后端 API](../backend/api-design.md)、[首页](../requirements/02-home-four-panels.md)、[日志与评论](../requirements/03-daily-log-module.md)、[标签](../requirements/07-tag-system.md)、[每日事项](../requirements/09-personal-daily-items.md)。 |

## 2. 测试范围与核心函数映射

| 功能编号 | 端与业务功能 | 后端模块 | 源文件 | 核心函数／方法 | 测试层级 | 测试文件 | 用例编号 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A01 | App：首页聚合与任务看版 | `home` | `backend/apps/home/services.py` | `candidate_tasks()` | 单元 | [test_midterm_qyh_home.py](../../backend/apps/home/test_midterm_qyh_home.py) | TC-QYH-001、TC-QYH-002、TC-QYH-003 |
| A02 | App：首页聚合与任务看版 | `home` | `backend/apps/home/services.py` | `ordered_candidates()` | 单元 | [test_midterm_qyh_home.py](../../backend/apps/home/test_midterm_qyh_home.py) | TC-QYH-004、TC-QYH-005、TC-QYH-006 |
| A03 | App：首页聚合与任务看版 | `home` | `backend/apps/home/services.py` | `company_top_tasks()` | 单元 | [test_midterm_qyh_home.py](../../backend/apps/home/test_midterm_qyh_home.py) | TC-QYH-007、TC-QYH-008、TC-QYH-009 |
| A04 | App：首页聚合与任务看版 | `home` | `backend/apps/home/services.py` | `home_payload()` | 单元／接口集成 | [test_midterm_qyh_home.py](../../backend/apps/home/test_midterm_qyh_home.py) | TC-QYH-010、TC-QYH-011、TC-QYH-012 |
| A05 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `parse_date()` | 单元 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-013、TC-QYH-014、TC-QYH-015 |
| A06 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `editable()` | 单元 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-016、TC-QYH-017、TC-QYH-018 |
| A07 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `require_editable()` | 单元 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-019、TC-QYH-020、TC-QYH-021 |
| A08 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `LogMineView.put()` | 接口集成 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-022、TC-QYH-023、TC-QYH-024 |
| A09 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `LogSubmitView.post()` | 接口集成 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-025、TC-QYH-026、TC-QYH-027 |
| A10 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `target_user()` | 单元 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-028、TC-QYH-029、TC-QYH-030 |
| A11 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `visible_log()` | 单元 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-031、TC-QYH-032、TC-QYH-033 |
| A12 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `LogListView.get()` | 接口集成 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-034、TC-QYH-035、TC-QYH-036 |
| A13 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `LogCalendarView.get()` | 接口集成 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-037、TC-QYH-038、TC-QYH-039 |
| A14 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `LogCommentListCreateView.post()` | 接口集成 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-040、TC-QYH-041、TC-QYH-042 |
| A15 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `LogCommentListCreateView.get()` | 接口集成 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-043、TC-QYH-044、TC-QYH-045 |
| A16 | App：日志与评论 | `logs` | `backend/apps/logs/views.py` | `log_comment_data()` | 单元 | [test_midterm_qyh_logs.py](../../backend/apps/logs/test_midterm_qyh_logs.py) | TC-QYH-046、TC-QYH-047、TC-QYH-048 |
| A17 | App：标签 | `tags` | `backend/apps/tags/views.py` | `founder_only()` | 单元／接口集成 | [test_midterm_qyh_tags.py](../../backend/apps/tags/test_midterm_qyh_tags.py) | TC-QYH-049、TC-QYH-050、TC-QYH-051 |
| A18 | App：标签 | `tags` | `backend/apps/tags/views.py` | `TagListView.get()` | 接口集成 | [test_midterm_qyh_tags.py](../../backend/apps/tags/test_midterm_qyh_tags.py) | TC-QYH-052、TC-QYH-053、TC-QYH-054 |
| A19 | App：标签 | `tags` | `backend/apps/tags/views.py` | `TagListView.post()` | 接口集成 | [test_midterm_qyh_tags.py](../../backend/apps/tags/test_midterm_qyh_tags.py) | TC-QYH-055、TC-QYH-056、TC-QYH-057 |
| A20 | App：标签 | `tags` | `backend/apps/tags/views.py` | `TagDetailView.patch()` | 接口集成 | [test_midterm_qyh_tags.py](../../backend/apps/tags/test_midterm_qyh_tags.py) | TC-QYH-058、TC-QYH-059、TC-QYH-060 |
| A21 | App：标签 | `tags` | `backend/apps/tags/views.py` | `TagDetailView.delete()` | 接口集成 | [test_midterm_qyh_tags.py](../../backend/apps/tags/test_midterm_qyh_tags.py) | TC-QYH-061、TC-QYH-062、TC-QYH-063 |
| A22 | App：标签 | `tags` | `backend/apps/tags/services.py` | `validate_tag_ids()` | 单元／接口集成 | [test_midterm_qyh_tags.py](../../backend/apps/tags/test_midterm_qyh_tags.py) | TC-QYH-064、TC-QYH-065、TC-QYH-066 |
| A23 | App：每日事项 | `home` | `backend/apps/home/serializers.py` | `PersonalDailyItemSerializer.validate()` | 单元 | [test_midterm_qyh_personal_items.py](../../backend/apps/home/test_midterm_qyh_personal_items.py) | TC-QYH-067、TC-QYH-068、TC-QYH-069 |
| A24 | App：每日事项 | `home` | `backend/apps/home/serializers.py` | `PersonalDailyItemRangeSerializer.validate()` | 单元 | [test_midterm_qyh_personal_items.py](../../backend/apps/home/test_midterm_qyh_personal_items.py) | TC-QYH-070、TC-QYH-071、TC-QYH-072 |
| A25 | App：每日事项 | `home` | `backend/apps/home/views.py` | `PersonalDailyItemListCreateView.get()` | 接口集成 | [test_midterm_qyh_personal_items.py](../../backend/apps/home/test_midterm_qyh_personal_items.py) | TC-QYH-073、TC-QYH-074、TC-QYH-075 |
| A26 | App：每日事项 | `home` | `backend/apps/home/views.py` | `PersonalDailyItemListCreateView.post()` | 接口集成 | [test_midterm_qyh_personal_items.py](../../backend/apps/home/test_midterm_qyh_personal_items.py) | TC-QYH-076、TC-QYH-077、TC-QYH-078 |
| A27 | App：每日事项 | `home` | `backend/apps/home/views.py` | `PersonalDailyItemDetailView.get_queryset()` | 接口集成 | [test_midterm_qyh_personal_items.py](../../backend/apps/home/test_midterm_qyh_personal_items.py) | TC-QYH-079、TC-QYH-080、TC-QYH-081 |
| A28 | App：每日事项 | `home` | `backend/apps/home/views.py` | `PersonalDailyItemDetailView.patch()` | 接口集成 | [test_midterm_qyh_personal_items.py](../../backend/apps/home/test_midterm_qyh_personal_items.py) | TC-QYH-082、TC-QYH-083、TC-QYH-084 |
| A29 | App：每日事项 | `home` | `backend/apps/home/views.py` | `PersonalDailyItemDetailView.delete()` | 接口集成 | [test_midterm_qyh_personal_items.py](../../backend/apps/home/test_midterm_qyh_personal_items.py) | TC-QYH-085、TC-QYH-086、TC-QYH-087 |
| A30 | App：每日事项 | `home` | `backend/apps/home/views.py` | `PersonalDailyItemMoveView.post()` | 接口集成 | [test_midterm_qyh_personal_items.py](../../backend/apps/home/test_midterm_qyh_personal_items.py) | TC-QYH-088、TC-QYH-089、TC-QYH-090 |

### 2.1 已有测试复用情况

| 已有测试文件及方法 | 覆盖的业务规则 | 本轮补充 |
| --- | --- | --- |
| `home/tests.py` 中 `test_company_top_tasks_orders_by_source_then_due_and_excludes_ineligible`、`test_company_top_tasks_capped_at_ten` | 任务看版顺序、可见性、数量 | 直接函数验证、临界条数、同截止时间比较创建时间 |
| `home/tests.py` 中 `test_home_payload_limits_fields_and_scoping`、`test_home_does_not_expose_subordinate_tasks_or_logs` | 首页内容精简及范围限制 | 首页结果与通知接口对齐、固定日期两侧的每日事项、日志摘要 |
| `home/tests.py` 中 `test_personal_items_list_returns_inclusive_overlaps_for_current_user_only`、`test_personal_item_move_preserves_span_and_is_idempotent`、`test_personal_item_mutations_do_not_create_app_audit_records` | 区间相交、拖动跨度与审计隔离 | 补充跨年、闰日、部分编辑与各接口失败路径 |
| `logs/tests.py` 中 `test_three_date_window_uniqueness_submit_and_freeze`、`test_draft_privacy_subordinate_scope_and_manager_read_only` | 日志窗口、唯一性、草稿和下属权限 | 独立辅助函数测试、回滚、评论作者与接口长度临界值 |
| `logs/tests.py` 中 `test_tag_creation_colors_soft_delete_and_atomic_log_update` | 标签校验、停用和日志关联 | 注入标签审计写入错误，验证回滚；日志和任务分别验证移除后禁止重新关联 |
| `tasks/tests.py` 中 `test_task_tag_history_atomic_validation_and_all_scope_deduplication` | 任务标签集成 | 本轮仅通过任务入口交叉验证共享的停用标签校验 |

### 2.2 未覆盖或暂不能测试的内容

旧版个人重点接口、预留附件和 AI 占位能力按 Issue 排除。统一版本尚未合入四份个人材料。Android 手工联调环境与证据见 §3.4。

## 3. 用例设计

### 3.1 等价类划分

| 对应规则 | 输入或状态维度 | 有效等价类 | 无效等价类 | 代表输入或状态 | 对应用例编号 |
| --- | --- | --- | --- | --- | --- |
| 任务看版 | 归属及状态 | 本人承担，审批通过、未完成、未取消 | 待审批、退回、他人承担、结束 | 本人派给他人，任务完成或取消 | TC-QYH-001～003 |
| 日志查看 | 组织范围及状态 | 本人，或授权下属已提交日志 | 同级、停用下属、授权下属草稿 | 经理读取下属私有草稿 | TC-QYH-028～033 |
| 评论 | 日志可见性及内容 | 作者或可见者，修剪后非空且不超过 2000 字 | 日志不可见、空白或超长 | 经理评论已提交日志、同级打开已提交日志 | TC-QYH-040～045 |
| 标签 | 角色及关联历史 | 创始人管理、员工选择启用项、保留旧的停用关联 | 员工管理、重复 ID、无历史关系的停用项 | 删除后尝试加回停用标签 | TC-QYH-049～054、064～066 |
| 每日事项 | 事项归属和日期 | 归属本人、闭区间相交且起止合法 | 他人事项、区间倒序、字段错误 | 查询触及窗口末日的事项 | TC-QYH-070～075、079～081 |

### 3.2 边界值分析

| 对应规则 | 边界定义 | 边界前／下界外 | 边界值 | 边界后／上界外 | 预期处理 | 对应用例编号 |
| --- | --- | --- | --- | --- | --- | --- |
| 任务看版与首页摘要 | 完整榜单 10、首页摘要 5 | 2 条榜单 | 10 条榜单或 5 条摘要 | 11 条榜单、6 条摘要候选 | 按排序截断 | TC-QYH-007～008、011 |
| 日志时间窗口 | 自前天到今天 | 前三天 | 前天、今天 | 明天 | 只开放窗口内编辑 | TC-QYH-016～021 |
| 评论长度 | 修剪后 1～2000 | 0 | 1、2000 | 2001 | 仅上下界内可保存 | TC-QYH-041～042 |
| 标签名长度 | 修剪后 1～100 | 0 | 100 | 101 | 100 有效；0、101 拒绝 | TC-QYH-056～057 |
| 事项标题长度 | 修剪后 1～200 | 0 | 200 | 201 | 200 有效；0、201 拒绝 | TC-QYH-077～078 |
| 事项时间区间 | 起止闭区间及移动后的跨度 | 结束比开始早一天 | 结束等于开始、相交仅共享端点 | 整段平移后跨月或跨年 | 拒绝倒序，包含端点并保持跨度 | TC-QYH-067～075、088～090 |

### 3.3 自动化用例清单

测试定位中的类和方法位于 §2 映射的文件。统计以独立测试方法为单位。一个方法内的同类输入通过 `subTest` 检查，子输入不额外计数。

| 用例编号 | 核心函数／方法 | 场景类型 | 前置条件 | 输入／操作 | 预期结果与关键断言 | 自动化测试定位 | 实际结果 | 状态 | 缺陷编号 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-QYH-001 | `candidate_tasks()` | 正常 | 本人有自建和他人派发任务 | 查询本人候选 | 包含两种来源 | `HomeMidtermTests.test_candidates_include_both_origins` | 断言全部满足 | 通过 | 无 |
| TC-QYH-002 | `candidate_tasks()` | 边界 | 本人有一条通过审批及一条退回任务 | 查询本人候选 | 仅返回通过审批的一条 | `HomeMidtermTests.test_candidates_only_one_eligible` | 断言全部满足 | 通过 | 无 |
| TC-QYH-003 | `candidate_tasks()` | 异常 | 存在派给他人、待审批、取消及完成任务 | 查询本人候选 | 四类记录均排除 | `HomeMidtermTests.test_candidates_exclude_other_and_invalid_states` | 断言全部满足 | 通过 | 无 |
| TC-QYH-004 | `ordered_candidates()` | 正常 | 自建任务最早到期，另有两个派发任务 | 查询排序 | 先派发，派发内截止时间升序 | `HomeMidtermTests.test_candidate_order_source_then_due` | 断言全部满足 | 通过 | 无 |
| TC-QYH-005 | `ordered_candidates()` | 边界 | 同来源同截止日期，创建时间不同 | 查询排序 | 先创建的在前 | `HomeMidtermTests.test_candidate_order_uses_creation_time_on_equal_due` | 断言全部满足 | 通过 | 无 |
| TC-QYH-006 | `ordered_candidates()` | 异常 | 待审批派发任务最早到期 | 查询排序 | 仅返回有效自建任务 | `HomeMidtermTests.test_candidate_order_ignores_better_ranked_ineligible_rows` | 断言全部满足 | 通过 | 无 |
| TC-QYH-007 | `company_top_tasks()` | 正常 | 有少于十条候选 | 读取默认榜单 | 完整按排序返回 | `HomeMidtermTests.test_top_tasks_keeps_order_below_limit` | 断言全部满足 | 通过 | 无 |
| TC-QYH-008 | `company_top_tasks()` | 边界 | 有十条有序候选 | 查十条，再加入一条最早到期任务重查 | 始终最多十条，第十一名被挤出 | `HomeMidtermTests.test_top_tasks_exact_and_over_default_limit` | 断言全部满足 | 通过 | 无 |
| TC-QYH-009 | `company_top_tasks()` | 异常 | 唯一任务已完成 | 查默认榜单 | 返回空榜单 | `HomeMidtermTests.test_top_tasks_excludes_ineligible_only_data` | 断言全部满足 | 通过 | 无 |
| TC-QYH-010 | `home_payload()` | 正常 | 有任务、已发布公司事项、本人日志与每日事项 | 读取首页聚合 | 返回四个来源的标识、日志摘要和事项简要字段 | `HomeMidtermTests.test_home_payload_uses_authoritative_sources` | 断言全部满足 | 通过 | 无 |
| TC-QYH-011 | `home_payload()` | 边界 | 有六条任务、六条覆盖今天的事项、两条相邻日期事项 | 读取首页聚合 | 只返回各排序前五条 | `HomeMidtermTests.test_home_payload_caps_board_and_today_items_at_five` | 断言全部满足 | 通过 | 无 |
| TC-QYH-012 | `home_payload()` | 异常 | 有本人草稿、他人日志事项、已读与已解决通知 | 读取首页及未读数接口 | 排除无权数据，两处未读数均为 1 | `HomeMidtermTests.test_home_payload_excludes_private_sources_and_matches_unread_endpoint` | 断言全部满足 | 通过 | 无 |
| TC-QYH-013 | `parse_date()` | 正常 | 无数据依赖 | 解析 2026-11-01 | 返回对应 date 对象 | `LogsMidtermTests.test_parse_date_valid_iso_day` | 断言全部满足 | 通过 | 无 |
| TC-QYH-014 | `parse_date()` | 边界 | 闰年 | 解析 2028-02-29 | 闰日有效 | `LogsMidtermTests.test_parse_date_accepts_real_leap_day` | 断言全部满足 | 通过 | 无 |
| TC-QYH-015 | `parse_date()` | 异常 | 无数据依赖 | 提交非补零日期、平年闰日及非字符串 | 都按调用方指定字段报错 | `LogsMidtermTests.test_parse_date_rejects_invalid_shape_and_day` | 断言全部满足 | 通过 | 无 |
| TC-QYH-016 | `editable()` | 正常 | 业务日期 11-01 | 检查 10-31 | true | `LogsMidtermTests.test_editable_accepts_yesterday` | 断言全部满足 | 通过 | 无 |
| TC-QYH-017 | `editable()` | 边界 | 业务日期 11-01 | 检查 10-30 和 11-01 | 前天和今天都可编辑 | `LogsMidtermTests.test_editable_includes_both_window_edges` | 断言全部满足 | 通过 | 无 |
| TC-QYH-018 | `editable()` | 异常 | 业务日期 11-01 | 检查 10-29 和 11-02 | 两侧越界均 false | `LogsMidtermTests.test_editable_excludes_day_before_window_and_tomorrow` | 断言全部满足 | 通过 | 无 |
| TC-QYH-019 | `require_editable()` | 正常 | 业务日期 11-01 | 校验 10-31 | 不抛异常 | `LogsMidtermTests.test_require_editable_allows_yesterday` | 断言全部满足 | 通过 | 无 |
| TC-QYH-020 | `require_editable()` | 边界 | 业务日期 11-01 | 校验 10-30 | 不抛异常 | `LogsMidtermTests.test_require_editable_allows_earliest_day` | 断言全部满足 | 通过 | 无 |
| TC-QYH-021 | `require_editable()` | 异常 | 业务日期 11-01 | 校验 10-29、11-02 | 返回 log_date 校验错误 | `LogsMidtermTests.test_require_editable_rejects_outside_window` | 断言全部满足 | 通过 | 无 |
| TC-QYH-022 | `LogMineView.put()` | 正常 | 作者无今日日志，有启用标签 | 保存带空白计划及标签 | 创建草稿和关联，记录一次创建审计 | `LogsMidtermTests.test_log_put_creates_draft_with_association_and_audit` | 断言全部满足 | 通过 | 无 |
| TC-QYH-023 | `LogMineView.put()` | 边界 | 作者前天已有带标签草稿 | 两次保存只发正文 | 复用同一 ID，标签不变，重复同值不新增审计 | `LogsMidtermTests.test_log_put_reuses_earliest_day_row_and_preserves_omitted_tags` | 断言全部满足 | 通过 | 无 |
| TC-QYH-024 | `LogMineView.put()` | 异常 | 草稿有旧标签 | 更换正文及标签，注入关联写入错误 | 400，正文与旧关联回滚，新关联及审计无记录 | `LogsMidtermTests.test_log_put_rolls_back_body_and_links_when_link_write_fails` | 断言全部满足 | 通过 | 无 |
| TC-QYH-025 | `LogSubmitView.post()` | 正常 | 今天有非空草稿 | 提交日志 | 更新状态与提交时间，记录一次提交审计 | `LogsMidtermTests.test_log_submit_persists_transition_and_audit` | 断言全部满足 | 通过 | 无 |
| TC-QYH-026 | `LogSubmitView.post()` | 边界 | 前天已有提交时间的已提交日志 | 重复提交 | 保持原时间，无额外审计 | `LogsMidtermTests.test_log_submit_earliest_day_repeated_call_is_idempotent` | 断言全部满足 | 通过 | 无 |
| TC-QYH-027 | `LogSubmitView.post()` | 异常 | 今天有空白草稿，昨天无记录，三天前有草稿 | 分别提交三天的日志 | 均 400，不改变状态或审计 | `LogsMidtermTests.test_log_submit_rejects_blank_missing_and_out_of_window` | 断言全部满足 | 通过 | 无 |
| TC-QYH-028 | `target_user()` | 正常 | 已认证员工 | 不传或明确传本人 ID | 返回本人 | `LogsMidtermTests.test_target_user_defaults_or_explicitly_selects_self` | 断言全部满足 | 通过 | 无 |
| TC-QYH-029 | `target_user()` | 边界 | 部门经理与间接下属 | 显式查询该下属 | 返回下属账号 | `LogsMidtermTests.test_target_user_allows_indirect_active_report` | 断言全部满足 | 通过 | 无 |
| TC-QYH-030 | `target_user()` | 异常 | 同组同事、停用下属及非数字输入 | 分别查询这三个对象 | 两种无授权对象返回 403，格式错误返回 400 | `LogsMidtermTests.test_target_user_rejects_peer_inactive_or_bad_identifier` | 断言全部满足 | 通过 | 无 |
| TC-QYH-031 | `visible_log()` | 正常 | 间接下属已提交日志 | 经理获取日志 ID | 返回正确日志 | `LogsMidtermTests.test_visible_log_allows_submitted_report` | 断言全部满足 | 通过 | 无 |
| TC-QYH-032 | `visible_log()` | 边界 | 本人有编辑期外草稿 | 按 ID 读取 | 日志仍可读 | `LogsMidtermTests.test_visible_log_allows_author_historical_draft` | 断言全部满足 | 通过 | 无 |
| TC-QYH-033 | `visible_log()` | 异常 | 作者有草稿及已提交日志 | 经理读草稿、同事读已提交、本人读缺失 ID | 均抛出 NotFound | `LogsMidtermTests.test_visible_log_conceals_foreign_draft_and_missing_id` | 断言全部满足 | 通过 | 无 |
| TC-QYH-034 | `LogListView.get()` | 正常 | 本人有草稿及已提交日志 | 查询本人列表 | 草稿可见，按日期倒序 | `LogsMidtermTests.test_log_list_returns_author_draft_and_newest_first` | 断言全部满足 | 通过 | 无 |
| TC-QYH-035 | `LogListView.get()` | 边界 | 授权下属有草稿及已提交日志 | 经理按精确日区间过滤 | 仅边界日的已提交日志 | `LogsMidtermTests.test_log_list_manager_gets_submitted_on_closed_range` | 断言全部满足 | 通过 | 无 |
| TC-QYH-036 | `LogListView.get()` | 异常 | 同级员工各有账号 | 请求非法 start_date 或同级账号列表 | 字段错误 400；无授权 403 | `LogsMidtermTests.test_log_list_rejects_bad_date_or_outside_scope` | 断言全部满足 | 通过 | 无 |
| TC-QYH-037 | `LogCalendarView.get()` | 正常 | 下属有十月已提交、草稿和十一月已提交 | 经理查下属十月 | 仅在十月已提交日期上标记 | `LogsMidtermTests.test_log_calendar_marks_only_submitted_report_days` | 断言全部满足 | 通过 | 无 |
| TC-QYH-038 | `LogCalendarView.get()` | 边界 | 业务日期 11-01，10-31 已提交，11-01 为草稿 | 不传年月查询 | 返回 2026-11 空标记 | `LogsMidtermTests.test_log_calendar_defaults_to_business_month_even_with_adjacent_results` | 断言全部满足 | 通过 | 无 |
| TC-QYH-039 | `LogCalendarView.get()` | 异常 | 已登录同级员工 | 分别传月份 13、年份 0 或同级 user_id | 400 年月错误或 403 | `LogsMidtermTests.test_log_calendar_rejects_invalid_month_or_unauthorized_target` | 断言全部满足 | 通过 | 无 |
| TC-QYH-040 | `LogCommentListCreateView.post()` | 正常 | 授权下属历史已提交日志 | 经理发表评论 | 评论归经理，无正文、状态或更新时间变更，写评论审计 | `LogsMidtermTests.test_comment_post_manager_can_write_on_old_submitted_log` | 断言全部满足 | 通过 | 无 |
| TC-QYH-041 | `LogCommentListCreateView.post()` | 边界 | 本人草稿 | 分别提交修剪后 1 字及 2000 字评论 | 两个边界都保存，作者标记 true | `LogsMidtermTests.test_comment_post_accepts_one_and_2000_chars_on_own_draft` | 断言全部满足 | 通过 | 无 |
| TC-QYH-042 | `LogCommentListCreateView.post()` | 异常 | 有草稿、已提交日志及两个其他角色 | 提交空白／2001 字评论，经理评草稿，同级评已提交 | 分别 400/404；没有评论或审计写入 | `LogsMidtermTests.test_comment_post_rejects_blank_overlong_and_hidden_log` | 断言全部满足 | 通过 | 无 |
| TC-QYH-043 | `LogCommentListCreateView.get()` | 正常 | 目标日志有先后两条评论，另一日志也有评论 | 经理打开目标日志评论 | 按时间升序，只返回目标日志评论 | `LogsMidtermTests.test_comment_list_preserves_creation_order_and_parent_scope` | 断言全部满足 | 通过 | 无 |
| TC-QYH-044 | `LogCommentListCreateView.get()` | 边界 | 本人有无评论草稿 | 获取评论第一页 | 返回 count 0 和空列表 | `LogsMidtermTests.test_comment_list_empty_for_visible_draft` | 断言全部满足 | 通过 | 无 |
| TC-QYH-045 | `LogCommentListCreateView.get()` | 异常 | 下属草稿已有评论，另有已提交日志 | 经理查草稿，同事查已提交 | 两者 404 且无评论载荷 | `LogsMidtermTests.test_comment_list_hides_report_draft_and_peer_log` | 断言全部满足 | 通过 | 无 |
| TC-QYH-046 | `log_comment_data()` | 正常 | 员工评论本人日志，账号属于部门团队 | 序列化评论 | 姓名、身份、组织、标记及创建时间正确 | `LogsMidtermTests.test_comment_data_marks_author_and_includes_role_and_team` | 断言全部满足 | 通过 | 无 |
| TC-QYH-047 | `log_comment_data()` | 边界 | 创始人没有姓名、部门和团队 | 序列化创始人评论 | 用户名回退，组织两项为 null，非作者 | `LogsMidtermTests.test_comment_data_falls_back_without_org_or_display_name` | 断言全部满足 | 通过 | 无 |
| TC-QYH-048 | `log_comment_data()` | 异常 | 评论人与日志作者同显示名但 ID 不同 | 序列化评论 | 作者比较凭 ID，且不披露敏感身份字段 | `LogsMidtermTests.test_comment_data_does_not_classify_same_display_name_as_author` | 断言全部满足 | 通过 | 无 |
| TC-QYH-049 | `founder_only()` | 正常 | 有创始人 | 直接调用管理校验 | 无权限异常 | `TagsMidtermTests.test_founder_gate_accepts_founder` | 断言全部满足 | 通过 | 无 |
| TC-QYH-050 | `founder_only()` | 边界 | 合法部门经理和团队长 | 分别请求包含停用标签的列表 | 两者均 403，不泄露标签列表 | `TagsMidtermTests.test_founder_gate_denies_each_managerial_role` | 断言全部满足 | 通过 | 无 |
| TC-QYH-051 | `founder_only()` | 异常 | 普通员工有一个可选启用标签 | 尝试创建、改名、删除 | 三项均 403，数据与审计不变 | `TagsMidtermTests.test_founder_gate_blocks_employee_mutations_without_side_effects` | 断言全部满足 | 通过 | 无 |
| TC-QYH-052 | `TagListView.get()` | 正常 | 普通员工，有启用匹配、停用匹配及无关标签 | 按去空白关键字搜索 | 仅启用匹配项 | `TagsMidtermTests.test_tag_list_employee_searches_only_active_tags` | 断言全部满足 | 通过 | 无 |
| TC-QYH-053 | `TagListView.get()` | 边界 | 创始人、只有一个停用标签 | 先默认查询，再显式包含停用 | 默认空，管理查询含停用状态 | `TagsMidtermTests.test_tag_list_founder_switch_includes_inactive_with_status` | 断言全部满足 | 通过 | 无 |
| TC-QYH-054 | `TagListView.get()` | 异常 | 普通员工已登录 | 传非法布尔值及管理过滤 | 400 字段错误、403 权限错误 | `TagsMidtermTests.test_tag_list_rejects_bad_flag_and_employee_management_query` | 断言全部满足 | 通过 | 无 |
| TC-QYH-055 | `TagListView.post()` | 正常 | 创始人，有不同颜色可选 | 创建带空白名称及第十个预设色 | 名称去空白，颜色落库和审计记录正确 | `TagsMidtermTests.test_tag_create_trims_name_and_records_selected_color` | 断言全部满足 | 通过 | 无 |
| TC-QYH-056 | `TagListView.post()` | 边界 | 100 字名称存在停用历史 | 用完全相同名称创建启用标签 | 201，新 ID，历史记录保持停用 | `TagsMidtermTests.test_tag_create_accepts_100_chars_and_reuses_inactive_name` | 断言全部满足 | 通过 | 无 |
| TC-QYH-057 | `TagListView.post()` | 异常 | 已有一个启用名称 | 提交重复、非法颜色、空白、101 字 | 409/400，保持一条记录，没有审计 | `TagsMidtermTests.test_tag_create_rejects_duplicate_bad_color_empty_and_oversize_name` | 断言全部满足 | 通过 | 无 |
| TC-QYH-058 | `TagDetailView.patch()` | 正常 | 启用标签同时关联日志和任务 | 改名称、颜色 | 原关联仍指向同一 ID，读到新颜色，记录审计 | `TagsMidtermTests.test_tag_patch_updates_existing_links_without_replacing_tag` | 断言全部满足 | 通过 | 无 |
| TC-QYH-059 | `TagDetailView.patch()` | 边界 | 启用标签已有同名值 | 只提交外围带空白的原名称 | 成功但时间戳不变，没有审计 | `TagsMidtermTests.test_tag_patch_same_normalized_value_is_noop` | 断言全部满足 | 通过 | 无 |
| TC-QYH-060 | `TagDetailView.patch()` | 异常 | 两个启用标签及一个停用标签 | 重复名同时改色、改停用项、模拟改色时审计写入失败 | 冲突 409、停用 400；模拟写入失败时返回错误，名称、颜色、审计均不变 | `TagsMidtermTests.test_tag_patch_leaves_data_intact_on_conflict_or_audit_failure` | 断言全部满足 | 通过 | 无 |
| TC-QYH-061 | `TagDetailView.delete()` | 正常 | 启用标签有关联日志及任务 | 停用标签并读取本人日志详情 | 标签行及关联保留，历史详情显示，写一条停用审计 | `TagsMidtermTests.test_tag_delete_disables_but_keeps_historical_links` | 断言全部满足 | 通过 | 无 |
| TC-QYH-062 | `TagDetailView.delete()` | 边界 | 标签原本启用 | 对同一标签停用两次 | 相同响应，时间戳及审计数不再变化 | `TagsMidtermTests.test_tag_delete_repeated_request_does_not_write_twice` | 断言全部满足 | 通过 | 无 |
| TC-QYH-063 | `TagDetailView.delete()` | 异常 | 有一个保留标签 | 停用不存在的 ID | 400，已有标签正常，无审计 | `TagsMidtermTests.test_tag_delete_rejects_unknown_id_without_writes` | 断言全部满足 | 通过 | 无 |
| TC-QYH-064 | `validate_tag_ids()` | 正常 | 两个启用标签 | 反向传入两个 ID | 输出保持请求顺序 | `TagsMidtermTests.test_tag_ids_preserve_input_order_for_active_tags` | 断言全部满足 | 通过 | 无 |
| TC-QYH-065 | `validate_tag_ids()` | 边界 | 一个停用标签已有历史关联 | 校验空数组及保留既有 ID | 均有效，已停用标签保留 | `TagsMidtermTests.test_tag_ids_allow_empty_and_retention_of_existing_inactive` | 断言全部满足 | 通过 | 无 |
| TC-QYH-066 | `validate_tag_ids()` | 异常 | 启用和停用标签；同一停用标签曾关联日志及任务 | 校验重复、缺失和停用 ID；通过日志和任务接口移除再加回 | 三类输入报错，接口拒绝重新关联 | `TagsMidtermTests.test_tag_ids_reject_duplicates_missing_and_readding_inactive` | 断言全部满足 | 通过 | 无 |
| TC-QYH-067 | `PersonalDailyItemSerializer.validate()` | 正常 | 已选预设色 | 校验 2026-12-31 至 2028-02-29 | 日期解析后保留完整范围 | `PersonalItemsMidtermTests.test_item_validation_accepts_multi_year_range` | 断言全部满足 | 通过 | 无 |
| TC-QYH-068 | `PersonalDailyItemSerializer.validate()` | 边界 | 已选预设色 | 起止均为闰日 | 单日有效 | `PersonalItemsMidtermTests.test_item_validation_accepts_equal_dates` | 断言全部满足 | 通过 | 无 |
| TC-QYH-069 | `PersonalDailyItemSerializer.validate()` | 异常 | 事项于 2026-10-06 结束 | 只把开始改为 10-07 | 结束日期错误，实例未变 | `PersonalItemsMidtermTests.test_item_validation_compares_partial_patch_to_saved_date` | 断言全部满足 | 通过 | 无 |
| TC-QYH-070 | `PersonalDailyItemRangeSerializer.validate()` | 正常 | 无数据依赖 | 验证跨年查询区间 | 返回解析后的起止日期 | `PersonalItemsMidtermTests.test_range_validation_accepts_cross_year` | 断言全部满足 | 通过 | 无 |
| TC-QYH-071 | `PersonalDailyItemRangeSerializer.validate()` | 边界 | 无数据依赖 | 提交同一天的上下界 | 通过且两端相等 | `PersonalItemsMidtermTests.test_range_validation_accepts_single_day` | 断言全部满足 | 通过 | 无 |
| TC-QYH-072 | `PersonalDailyItemRangeSerializer.validate()` | 异常 | 无数据依赖 | 结束比开始早一天 | end_date 校验错误 | `PersonalItemsMidtermTests.test_range_validation_rejects_inverted_range` | 断言全部满足 | 通过 | 无 |
| TC-QYH-073 | `PersonalDailyItemListCreateView.get()` | 正常 | 本人事项与查询相交，另有他人的同日事项 | 查询国庆起始日及次日 | 仅本人事项，返回正确节日标记，不分页 | `PersonalItemsMidtermTests.test_item_list_returns_own_overlap_and_holiday_annotation` | 断言全部满足 | 通过 | 无 |
| TC-QYH-074 | `PersonalDailyItemListCreateView.get()` | 边界 | 两条本人事项分别接触窗口首日与末日，另有不相交项 | 查询 10-06 至 10-08 | 两端均包含，外侧排除 | `PersonalItemsMidtermTests.test_item_list_includes_each_closed_interval_endpoint` | 断言全部满足 | 通过 | 无 |
| TC-QYH-075 | `PersonalDailyItemListCreateView.get()` | 异常 | 本人有事项 | 分别查询缺末日、坏日期、倒序 | 400 及对应字段错误 | `PersonalItemsMidtermTests.test_item_list_rejects_missing_bad_or_reversed_dates` | 断言全部满足 | 通过 | 无 |
| TC-QYH-076 | `PersonalDailyItemListCreateView.post()` | 正常 | 登录本人，期间已有重叠事项 | POST 合法颜色及带空白标题 | 创建新条目、归属本人、不写审计 | `PersonalItemsMidtermTests.test_item_create_saves_trimmed_payload_without_audit` | 断言全部满足 | 通过 | 无 |
| TC-QYH-077 | `PersonalDailyItemListCreateView.post()` | 边界 | 登录本人 | 提交 200 字标题及同日起止 | 保存全部字符和单日日期 | `PersonalItemsMidtermTests.test_item_create_accepts_200_characters_and_single_day` | 断言全部满足 | 通过 | 无 |
| TC-QYH-078 | `PersonalDailyItemListCreateView.post()` | 异常 | 登录本人 | 提交空／201 字标题、非预设色或 owner_id | 400，零新增、零审计 | `PersonalItemsMidtermTests.test_item_create_rejects_bad_fields_and_owner_override` | 断言全部满足 | 通过 | 无 |
| TC-QYH-079 | `PersonalDailyItemDetailView.get_queryset()` | 正常 | 本人及他人有同名事项 | GET 本人 ID | 返回本人完整字段 | `PersonalItemsMidtermTests.test_item_detail_returns_owned_item_full_fields` | 断言全部满足 | 通过 | 无 |
| TC-QYH-080 | `PersonalDailyItemDetailView.get_queryset()` | 边界 | 两个账号各有一条事项 | 切换到第二个账号读取两个 ID | 第二账号本人可读，原账号 ID 返回 404 | `PersonalItemsMidtermTests.test_item_detail_tracks_current_authenticated_owner` | 断言全部满足 | 通过 | 无 |
| TC-QYH-081 | `PersonalDailyItemDetailView.get_queryset()` | 异常 | 有他人事项 | GET 他人及不存在 ID | 均 404，错误体相同 | `PersonalItemsMidtermTests.test_item_detail_foreign_and_missing_share_404_contract` | 断言全部满足 | 通过 | 无 |
| TC-QYH-082 | `PersonalDailyItemDetailView.patch()` | 正常 | 已有本人事项 | PATCH 说明及预设色 | 仅改指定字段，日期和标题保留，无审计 | `PersonalItemsMidtermTests.test_item_patch_updates_only_sent_fields_without_audit` | 断言全部满足 | 通过 | 无 |
| TC-QYH-083 | `PersonalDailyItemDetailView.patch()` | 边界 | 本人事项多日 | 只提交与起始相同的结束日期 | 保存单日范围 | `PersonalItemsMidtermTests.test_item_patch_can_shrink_to_one_day` | 断言全部满足 | 通过 | 无 |
| TC-QYH-084 | `PersonalDailyItemDetailView.patch()` | 异常 | 本人和外部各有事项 | 倒序更新本人，再更新他人说明 | 分别 400/404，两个对象保持 | `PersonalItemsMidtermTests.test_item_patch_rejects_reversed_and_foreign_updates` | 断言全部满足 | 通过 | 无 |
| TC-QYH-085 | `PersonalDailyItemDetailView.delete()` | 正常 | 本人和他人有同名事项 | DELETE 本人 ID | 删除指定对象，保留他人项且无审计 | `PersonalItemsMidtermTests.test_item_delete_removes_only_requested_owned_item` | 断言全部满足 | 通过 | 无 |
| TC-QYH-086 | `PersonalDailyItemDetailView.delete()` | 边界 | 本人仅有一项 | 对同一 ID 连续删除 | 首次 200，第二次 404 | `PersonalItemsMidtermTests.test_item_delete_repeated_id_returns_not_found` | 断言全部满足 | 通过 | 无 |
| TC-QYH-087 | `PersonalDailyItemDetailView.delete()` | 异常 | 他人有一项 | DELETE 他人 ID | 404，事项仍存在 | `PersonalItemsMidtermTests.test_item_delete_cannot_touch_other_account` | 断言全部满足 | 通过 | 无 |
| TC-QYH-088 | `PersonalDailyItemMoveView.post()` | 正常 | 本人有五日事项 | 先移往月末，再移往年末 | 正确跨月跨年，跨度和说明不变，无审计 | `PersonalItemsMidtermTests.test_item_move_across_month_and_year_preserves_duration` | 断言全部满足 | 通过 | 无 |
| TC-QYH-089 | `PersonalDailyItemMoveView.post()` | 边界 | 本人有单日事项 | 移动到闰日并重复发送 | 起止相同，重复不改 updated_at | `PersonalItemsMidtermTests.test_item_move_single_day_and_repeated_target_are_idempotent` | 断言全部满足 | 通过 | 无 |
| TC-QYH-090 | `PersonalDailyItemMoveView.post()` | 异常 | 本人和他人各有事项 | 提交不存在日期及他人 ID | 400/404，原日期不变 | `PersonalItemsMidtermTests.test_item_move_rejects_invalid_date_and_foreign_item` | 断言全部满足 | 通过 | 无 |

### 3.4 客户端手动验证

| 用例编号 | App 功能 | 角色及前置条件 | 操作步骤 | 预期界面行为 | 实际结果 | 状态 | 证据／缺陷编号 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MAN-QYH-001 | 日志详情与编辑入口 | 作者有今天和三天前的日志，经理可读取已提交日志 | 作者依次打开两日详情，经理打开授权下属的已提交详情 | 先显示详情，作者仅近三日可见修改入口，经理无修改入口 | 未进行端到端手工联调 | 未执行 | 无 |
| MAN-QYH-002 | 评论头像 | 一名评论者有头像，另一名无头像 | 打开含两人评论的日志详情，核对头像、资料和作者标记 | 正常头像和缺省头像均正确显示，作者评论有本人标记 | 未进行端到端手工联调 | 未执行 | 无 |
| MAN-QYH-003 | 每日事项长按拖动 | 本人月历有跨日事项 | 将事项拖到换月区域，松手，刷新并进入详情 | 拖动期间能翻月，松手后日期持久化，跨度不变 | 未进行端到端手工联调 | 未执行 | 无 |
| MAN-QYH-004 | 日历点击和月切换 | 本人有重叠事项，区间含节假日标记 | 翻月并点击重叠日期，从列表打开一条详情 | 节假日与事项正常显示，日期列表及详情匹配 | 未进行端到端手工联调 | 未执行 | 无 |

## 4. 测试执行过程

### 4.1 准备与执行命令

```powershell
cd backend
$env:SQLITE_PATH = Join-Path $env:TEMP 'pandora-qyh-midterm.sqlite3'
$env:DJANGO_DEBUG = 'True'
$env:DJANGO_SECRET_KEY = 'qyh-midterm-local-test-only-key-32chars'
.\.venv\Scripts\python.exe manage.py test apps.home.test_midterm_qyh_home apps.home.test_midterm_qyh_personal_items apps.logs.test_midterm_qyh_logs apps.tags.test_midterm_qyh_tags --noinput --verbosity 2
.\.venv\Scripts\python.exe manage.py test apps.home apps.logs apps.tags --noinput --verbosity 2
.\.venv\Scripts\python.exe manage.py test --noinput --verbosity 2
```

### 4.2 执行记录

| 轮次 | 完成时间（UTC+8） | 版本及范围 | 发现数 | 实际结果 | 证据 |
| --- | --- | --- | ---: | --- | --- |
| 1 | 16:43:34 | 基准 + 首版新增测试，运行 4 个文件 | 90 | 执行 54 个方法，另有 2 个测试类初始化错误 | [初次输出节选](evidence/qyh/01-fixture-retry.txt) |
| 2 | 16:44:08 | 基准 + 修正后的新增测试，代码对应 da96a8f | 90 | 90 通过，0 失败／错误／跳过 | [新增测试输出](evidence/qyh/02-added-tests.txt) |
| 3 | 16:44:31 | 同轮次 2，运行 home、logs、tags 三个模块 | 115 | 115 通过，0 失败／错误／跳过 | [模块回归输出](evidence/qyh/03-module-regression.txt) |
| 4 | 16:44:49 | 同轮次 2，运行完整 Django 后端测试 | 187 | 187 通过，0 失败／错误／跳过 | [全量输出](evidence/qyh/04-full-backend.txt) |

### 4.3 原始输出与证据

Django 三轮最终测试分别报告 `Ran 90 tests`、`Ran 115 tests`、`Ran 187 tests`，均返回 `OK`。每轮使用 `file:memorydb_default?mode=memory&cache=shared` 独立测试库。

Android 使用 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` 构建，随后在 Medium_Phone 模拟器上以 `adb shell am instrument` 运行现有用例。测试替身提供接口数据，六条每日事项 UI 用例全部通过。11 条中有 9 条通过、2 条失败；单独复跑两条仍失败。见 [Android 界面回归](evidence/qyh/05-android-ui.txt)和[失败复跑](evidence/qyh/06-android-ui-retry.txt)。

## 5. 测试结果统计

### 5.1 设计用例统计

| 类别 | 计划用例数 | 已执行 | 通过 | 失败 | 阻塞 | 未执行 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 新增后端自动化 | 90 | 90 | 90 | 0 | 0 | 0 |
| 客户端手动验证 | 4 | 0 | 0 | 0 | 0 | 4 |

### 5.2 自动化工具统计说明

90 个新方法分别对应 §3.3 中的 90 条用例。同一方法内的 `subTest` 输入与既有用例、回归重跑及 Android UI 回归分开统计。

### 5.3 函数覆盖检查

| 核心函数／方法 | 正常用例编号 | 边界用例编号 | 异常用例编号 | 是否满足至少 3 条 | 未满足原因 |
| --- | --- | --- | --- | --- | --- |
| `candidate_tasks()` | TC-QYH-001 | TC-QYH-002 | TC-QYH-003 | 是 | 不适用 |
| `ordered_candidates()` | TC-QYH-004 | TC-QYH-005 | TC-QYH-006 | 是 | 不适用 |
| `company_top_tasks()` | TC-QYH-007 | TC-QYH-008 | TC-QYH-009 | 是 | 不适用 |
| `home_payload()` | TC-QYH-010 | TC-QYH-011 | TC-QYH-012 | 是 | 不适用 |
| `parse_date()` | TC-QYH-013 | TC-QYH-014 | TC-QYH-015 | 是 | 不适用 |
| `editable()` | TC-QYH-016 | TC-QYH-017 | TC-QYH-018 | 是 | 不适用 |
| `require_editable()` | TC-QYH-019 | TC-QYH-020 | TC-QYH-021 | 是 | 不适用 |
| `LogMineView.put()` | TC-QYH-022 | TC-QYH-023 | TC-QYH-024 | 是 | 不适用 |
| `LogSubmitView.post()` | TC-QYH-025 | TC-QYH-026 | TC-QYH-027 | 是 | 不适用 |
| `target_user()` | TC-QYH-028 | TC-QYH-029 | TC-QYH-030 | 是 | 不适用 |
| `visible_log()` | TC-QYH-031 | TC-QYH-032 | TC-QYH-033 | 是 | 不适用 |
| `LogListView.get()` | TC-QYH-034 | TC-QYH-035 | TC-QYH-036 | 是 | 不适用 |
| `LogCalendarView.get()` | TC-QYH-037 | TC-QYH-038 | TC-QYH-039 | 是 | 不适用 |
| `LogCommentListCreateView.post()` | TC-QYH-040 | TC-QYH-041 | TC-QYH-042 | 是 | 不适用 |
| `LogCommentListCreateView.get()` | TC-QYH-043 | TC-QYH-044 | TC-QYH-045 | 是 | 不适用 |
| `log_comment_data()` | TC-QYH-046 | TC-QYH-047 | TC-QYH-048 | 是 | 不适用 |
| `founder_only()` | TC-QYH-049 | TC-QYH-050 | TC-QYH-051 | 是 | 不适用 |
| `TagListView.get()` | TC-QYH-052 | TC-QYH-053 | TC-QYH-054 | 是 | 不适用 |
| `TagListView.post()` | TC-QYH-055 | TC-QYH-056 | TC-QYH-057 | 是 | 不适用 |
| `TagDetailView.patch()` | TC-QYH-058 | TC-QYH-059 | TC-QYH-060 | 是 | 不适用 |
| `TagDetailView.delete()` | TC-QYH-061 | TC-QYH-062 | TC-QYH-063 | 是 | 不适用 |
| `validate_tag_ids()` | TC-QYH-064 | TC-QYH-065 | TC-QYH-066 | 是 | 不适用 |
| `PersonalDailyItemSerializer.validate()` | TC-QYH-067 | TC-QYH-068 | TC-QYH-069 | 是 | 不适用 |
| `PersonalDailyItemRangeSerializer.validate()` | TC-QYH-070 | TC-QYH-071 | TC-QYH-072 | 是 | 不适用 |
| `PersonalDailyItemListCreateView.get()` | TC-QYH-073 | TC-QYH-074 | TC-QYH-075 | 是 | 不适用 |
| `PersonalDailyItemListCreateView.post()` | TC-QYH-076 | TC-QYH-077 | TC-QYH-078 | 是 | 不适用 |
| `PersonalDailyItemDetailView.get_queryset()` | TC-QYH-079 | TC-QYH-080 | TC-QYH-081 | 是 | 不适用 |
| `PersonalDailyItemDetailView.patch()` | TC-QYH-082 | TC-QYH-083 | TC-QYH-084 | 是 | 不适用 |
| `PersonalDailyItemDetailView.delete()` | TC-QYH-085 | TC-QYH-086 | TC-QYH-087 | 是 | 不适用 |
| `PersonalDailyItemMoveView.post()` | TC-QYH-088 | TC-QYH-089 | TC-QYH-090 | 是 | 不适用 |

## 6. 问题与缺陷记录

### TEST-QYH-001　首页两组测试数据违反创始人唯一约束（已修正）

第一次运行按两个创始人准备跨账号用例，数据库禁止第二个启用创始人，导致两个测试类在执行断言前报 `IntegrityError`。测试数据调整为同一组织里的创始人与直属部门经理，保留跨账号权限断言。修正后 90 条新增测试全部通过。见[初次输出](evidence/qyh/01-fixture-retry.txt)及[重跑结果](evidence/qyh/02-added-tests.txt)。

### COORD-QYH-001　中期报告汇总负责人待确认

Issue 分工表将汇总写在闵世宇名下，正文「汇总与验收材料」指定曲懿涵。当前个人报告已准备好交接，四份报告和统一版本结果齐备后再出团队汇总。

### UI-QYH-001　首页旧测试的精确文本匹配失败

- **复现**　运行 `BusinessUiTest.homeLoadsAndDisplaysDashboardDataFromApi`。替身返回标题 `Home item alpha`，等待首页出现事项。
- **预期**　首页可展示接口提供的事项标题。
- **实际**　等待精确文本 10 秒超时。当前卡片代码将标题显示为带 `• ` 前缀的文本，旧断言只匹配裸标题。
- **影响与状态**　现有 UI 回归红灯，当前证据指向断言与显示格式不一致。Android 代码及用例与基准一致。本轮登记。[复跑输出](evidence/qyh/06-android-ui-retry.txt)

### UI-QYH-002　日志旧测试找不到查看对象选择入口

- **复现**　运行 `BusinessUiTest.selectedLogPersonIncludesDepartmentAndTeam`，用默认参数加载 `LogsScreen` 并查找「选择查看对象」。
- **预期**　能打开授权成员日志，并展示成员部门和团队。
- **实际**　未找到该控件。当前导航从组织关系的成员详情进入下属日志，并传入 `initialPersonId`。
- **影响与状态**　旧测试覆盖的入口与当前导航不同。现有自动化结果无法确认新入口的成员资料显示，仍需按当前导航复核。[复跑输出](evidence/qyh/06-android-ui-retry.txt)

本次后端已执行范围内未发现业务缺陷。

## 7. 代码覆盖率

本轮使用 §5.3 的函数到用例映射核对需求覆盖。没有采集语句或分支覆盖率，现有本地虚拟环境未安装 `coverage`。

## 8. 测试结论与汇总交接

个人范围的 30 个函数均有 3 条新增用例并通过，涉及排序、输入校验、权限、幂等、标签历史及失败回滚。相关模块 115 条和个人版本完整后端 187 条均通过。团队汇总需要其余三个测试分支齐备，并在统一版本重跑完整后端；§3.4 仍需客户端手工联调。交接材料为本报告、个人分支 PR 和 [qyh 执行证据](evidence/qyh/)。

## 附录 A：AI 辅助记录与人工复核

### A.1 使用记录

- 工具为 Codex（GPT-6），使用日期为 2026-11-19。
- 输入为 Issue #42、相关需求、API 文档、本分支被测函数与现有测试。
- 输出为 TC-QYH-001～090 的设计、测试代码、运行记录和报告。

实际用户提示词：

```text
请你阅读最新的issue10, 我们需要完成的是有标记"曲懿涵"或"Asu_BTTS"的部分。
这次主要的任务是测试，请你先阅读后，简要告诉我你怎么测试。

你需要交付什么？

OK，按照你说的，自动化完成吧。
```

### A.2 人工逐条复核

| 对应用例 | 核对内容 | 人工复核状态 | 人工调整及理由 | 复核人 |
| --- | --- | --- | --- | --- |
| TC-QYH-001～090 | 按 §3.3 逐条核对需求、前置条件、输入、预期结果与实际断言 | 待人工复核 | 尚无人工调整；测试数据修正记录见 TEST-QYH-001 | 待记录 |

## 附录 B：提交前自查

- [x] 30 个核心函数全部映射到正常、边界和异常用例。
- [x] 现有业务代码及现有测试保留。
- [x] 已记录版本、命令、发现数量和可复现证据。
- [x] 初次测试错误和手工验证未执行项均已记录。
- [x] 个人报告的证据链接指向实际文件。
- [ ] 90 条用例完成人工逐条复核。
- [ ] Android 手工联调、团队统一版本回归和飞书共享归档。
