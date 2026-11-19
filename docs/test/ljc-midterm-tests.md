# 梁家程中期个人测试报告

## 1. 基本信息与测试环境

| 项目 | 内容 |
| --- | --- |
| 测试人员 | 梁家程（@Huahua0717） |
| 关联 Issue | [Issue #42 · 核心业务测试补充与测试报告整理](https://github.com/KID258w/ProjectPandora/issues/42) |
| 测试分支与 PR | `test/10-midterm-tasks-notifications` → `dev`。[查看该分支的 PR](https://github.com/KID258w/ProjectPandora/pulls?q=is%3Apr+head%3Atest%2F10-midterm-tasks-notifications) |
| 执行日期 | 2026-11-19（Asia/Shanghai） |
| 团队统一测试基准 | [`bdff2dba6984042d64bf0f2ef99130ba3552a1b9`](https://github.com/KID258w/ProjectPandora/commit/bdff2dba6984042d64bf0f2ef99130ba3552a1b9)，`dev` 上的「准备进行初发布版本测试」，与账号组 [PR #43](https://github.com/KID258w/ProjectPandora/pull/43) 的报告一致 |
| 测试代码版本 | `049dc6a55f88d49f46058100baa8412093961e12`，在团队基准上仅增加本报告对应的测试文件 |
| 本地环境 | Windows 11 10.0.26200，Python 3.12.14，Django 5.2.18，DRF 3.18.3，SQLite 3.53.1 |
| 测试工具 | Django 测试运行器、DRF `APITestCase`、真实 ORM 与基于本地合成账号的 App JWT |
| 数据库 | Django 创建并自动销毁的测试数据库（SQLite 内存库） |
| App 环境 | 当前工作站未找到可用 ADB 或已连接的 Android 测试设备，手动验证状态见 3.4 |
| 需求依据 | [需求 01 · 任务创建、审批与验收](../requirements/01-task-creation-and-acceptance.md) 第 2–8 节、[后端 API 设计](../backend/api-design.md) 任务与通知章节、Issue #42 第 3 节 |

## 2. 测试范围与核心函数映射

30 个函数或方法均有对应测试，代码位于 [`test_midterm_ljc_tasks_notifications.py`](../../backend/apps/tasks/test_midterm_ljc_tasks_notifications.py)。函数级测试直接调用函数并核对返回值、异常或数据库状态。接口集成测试走真实 URL、认证、序列化、视图和数据库，按场景核对审批、通知和审计。首页未读数用例同时对照通知接口。

| 序号 | 核心函数／方法 | 作用 | 层级 | 用例 |
| --- | --- | --- | --- | --- |
| 01 | [`assignable_users()`](../../backend/apps/tasks/services.py) | 根据创建人的角色、组织关系和账号状态计算负责人候选集。 | 函数级 | [TC-LJC-001 至 TC-LJC-003](#case-01) |
| 02 | [`validate_assignee()`](../../backend/apps/tasks/services.py) | 将可指派的用户 ID 解析为用户对象，拒绝无权限或停用的负责人。 | 函数级 | [TC-LJC-004 至 TC-LJC-006](#case-02) |
| 03 | [`validate_task_schedule()`](../../backend/apps/tasks/views.py) | 验证选填的预计开始时间不晚于截止时间。 | 函数级 | [TC-LJC-007 至 TC-LJC-009](#case-03) |
| 04 | [`TaskListView.post()`](../../backend/apps/tasks/views.py) | 创建任务，保存标签、变更历史及审计，按角色触发审批或派发。 | 接口集成 | [TC-LJC-010 至 TC-LJC-012](#case-04) |
| 05 | [`TaskListView.get()`](../../backend/apps/tasks/views.py) | 分页返回指定范围内本人已审批任务。 | 接口集成 | [TC-LJC-013 至 TC-LJC-015](#case-05) |
| 06 | [`filtered_tasks()`](../../backend/apps/tasks/views.py) | 按完成、归档状态及业务时区日期区间过滤任务。 | 函数级 | [TC-LJC-016 至 TC-LJC-018](#case-06) |
| 07 | [`visible_task()`](../../backend/apps/tasks/services.py) | 按创建人、负责人、审批人和下属关系校验详情访问。 | 函数级 | [TC-LJC-019 至 TC-LJC-021](#case-07) |
| 08 | [`TaskPinView.post()`](../../backend/apps/tasks/views.py) | 为本人任务中心的已审批任务写入账号级置顶。 | 接口集成 | [TC-LJC-022 至 TC-LJC-024](#case-08) |
| 09 | [`TaskPinView.delete()`](../../backend/apps/tasks/views.py) | 取消当前账号的任务置顶并保持幂等。 | 接口集成 | [TC-LJC-025 至 TC-LJC-027](#case-09) |
| 10 | [`PinnedTaskListView.get()`](../../backend/apps/tasks/views.py) | 分页读取当前用户仍在授权范围内的已审批置顶任务。 | 接口集成 | [TC-LJC-028 至 TC-LJC-030](#case-10) |
| 11 | [`TaskDetailView.patch()`](../../backend/apps/tasks/views.py) | 修改未完成任务，记录变更与审计，关键字段修改后按需重新送审。 | 接口集成 | [TC-LJC-031 至 TC-LJC-033](#case-11) |
| 12 | [`TaskResubmitView.post()`](../../backend/apps/tasks/views.py) | 创建人修订被退回任务后重新提交直属上级审批。 | 接口集成 | [TC-LJC-034 至 TC-LJC-036](#case-12) |
| 13 | [`TaskHistoryView.get()`](../../backend/apps/tasks/views.py) | 读取授权任务的变更历史，保持名称快照并兼容旧 ID 类型。 | 接口集成 | [TC-LJC-037 至 TC-LJC-039](#case-13) |
| 14 | [`create_approval_round()`](../../backend/apps/tasks/services.py) | 为直属上级创建下一审批轮次和对应通知。 | 函数级 | [TC-LJC-040 至 TC-LJC-042](#case-14) |
| 15 | [`ApprovalInboxView.get()`](../../backend/apps/tasks/views.py) | 读取待当前审批人处理的活动任务。 | 接口集成 | [TC-LJC-043 至 TC-LJC-045](#case-15) |
| 16 | [`ApprovalOutboxView.get()`](../../backend/apps/tasks/views.py) | 读取当前创建人待上级审批或退回待编辑的活动任务。 | 接口集成 | [TC-LJC-046 至 TC-LJC-048](#case-16) |
| 17 | [`TaskApprovalView.post()`](../../backend/apps/tasks/views.py) | 由本轮直属审批人通过或退回，通知创建人并在通过后派发给负责人。 | 接口集成 | [TC-LJC-049 至 TC-LJC-051](#case-17) |
| 18 | [`require_executable()`](../../backend/apps/tasks/services.py) | 确保操作者是负责人且任务已审批、未完成、未取消。 | 函数级 | [TC-LJC-052 至 TC-LJC-054](#case-18) |
| 19 | [`TaskUpdateView.post()`](../../backend/apps/tasks/views.py) | 负责人追加文字进展并记录审计。 | 接口集成 | [TC-LJC-055 至 TC-LJC-057](#case-19) |
| 20 | [`TaskSubmissionView.post()`](../../backend/apps/tasks/views.py) | 由负责人提交成果并触发待验收，自派任务提交时直接完成。 | 接口集成 | [TC-LJC-058 至 TC-LJC-060](#case-20) |
| 21 | [`TaskReviewView.post()`](../../backend/apps/tasks/views.py) | 创建人验收或退回指定的待验收提交，并通知负责人。 | 接口集成 | [TC-LJC-061 至 TC-LJC-063](#case-21) |
| 22 | [`TaskCancelView.post()`](../../backend/apps/tasks/views.py) | 创建人取消未完成任务，归档并按审批阶段处理通知。 | 接口集成 | [TC-LJC-064 至 TC-LJC-066](#case-22) |
| 23 | [`create_task_notification()`](../../backend/apps/tasks/services.py) | 为指定人和事件写入通知，同一事件键只保留一条。 | 函数级 | [TC-LJC-067 至 TC-LJC-069](#case-23) |
| 24 | [`resolve_task_notifications()`](../../backend/apps/tasks/services.py) | 按任务、类型和提交范围关闭仍有效的通知，保留通知和已读状态。 | 函数级 | [TC-LJC-070 至 TC-LJC-072](#case-24) |
| 25 | [`send_assignment()`](../../backend/apps/tasks/services.py) | 向非创建人的负责人发送已派发提醒，按审批轮次去重。 | 函数级 | [TC-LJC-073 至 TC-LJC-075](#case-25) |
| 26 | [`notification_data()`](../../backend/apps/tasks/views.py) | 将通知转换为接口字段，保留原审批轮次、原因和任务当前状态。 | 函数级 | [TC-LJC-076 至 TC-LJC-078](#case-26) |
| 27 | [`NotificationListView.get()`](../../backend/apps/tasks/views.py) | 分页读取本人通知，可仅显示未读且未结束的通知。 | 接口集成 | [TC-LJC-079 至 TC-LJC-081](#case-27) |
| 28 | [`NotificationUnreadCountView.get()`](../../backend/apps/tasks/views.py) | 返回当前账号有效且未读的任务通知数。 | 接口集成 | [TC-LJC-082 至 TC-LJC-084](#case-28) |
| 29 | [`NotificationMarkReadView.post()`](../../backend/apps/tasks/views.py) | 将明确指定且属于当前帐号的未读通知标记为已读。 | 接口集成 | [TC-LJC-085 至 TC-LJC-087](#case-29) |
| 30 | [`unread_notification_count()`](../../backend/apps/home/services.py) | 为首页统计当前用户有效未读通知，并与通知接口使用同样的口径。 | 函数级与接口对照 | [TC-LJC-088 至 TC-LJC-090](#case-30) |

### 2.1 现有测试与补充重点

| 现有测试 | 已覆盖 | 本轮补充 |
| --- | --- | --- |
| [任务既有测试](../../backend/apps/tasks/tests.py) 的 `test_assignment_matrix_pending_visibility_and_notifications`、`test_approval_return_resubmit_reapproval_and_tag_only_edit` | 基本派发矩阵、待审可见性、退回与重审、标签单独修改 | 停用候选、标题和日期边界、新审批结果通知、停用标签整体拒绝、独立的历史快照与旧 ID 读取 |
| 同文件的 `test_execution_acceptance_return_self_completion_and_cancel`、`test_subordinate_scope_cross_department_and_notification_read_range` | 基本执行、验收和取消流程、组织隔离与通知读取 | 审计触发、再次提交、重复验收、不同审批阶段的取消接收人、通知结束与已读状态的独立性 |
| [首页既有测试](../../backend/apps/home/tests.py) | 首页集成和聚合数据 | 首页的未读统计服务直接调用，并与任务通知接口对照 |
| [Android 既有界面测试](../../android/app/src/androidTest/java/com/projectpandora/app/BusinessUiTest.kt) | 源码已有通知跳转参数和审批权限测试 | 本轮提供真实客户端手动场景。现有 Android 测试也未在本机执行 |

### 2.2 未覆盖或暂不能测试的内容

- Android 通知跳转、列表刷新和红点显示需要可用设备及独立测试数据，见 3.4。
- 数据库并发、多设备同步和压测未执行。置顶重复提交与通知事件键去重已按顺序执行验证。
- 认证链、标签校验和审计脱敏的共享函数由对应成员负责。本报告验证任务入口调用后的实际结果。

## 3. 用例设计

### 3.1 等价类划分

| 维度 | 正常类 | 异常类 | 代表用例 |
| --- | --- | --- | --- |
| 角色与授权 | 同组可选负责人、本轮审批人、直接派发者、本人通知 | 他部门员工、停用员工、只读上级、匿名用户等 | TC-LJC-001 至 006、TC-LJC-049 至 051、TC-LJC-061 至 063、TC-LJC-079 至 081 |
| 任务状态 | 已审批且未完成的任务可执行，待验收成果可由派发人处理 | 待审批、已完成、已取消、已处理的提交 | TC-LJC-052 至 054、TC-LJC-061 至 066 |
| 通知状态 | 本人有效未读，明确选中的本人通知 | 他人通知、已结束通知、已读通知、非法 ID | TC-LJC-076 至 090 |

### 3.2 边界值分析

| 边界 | 代表取值及期望 | 用例 |
| --- | --- | --- |
| 预计开始时间相对截止时间 | 早 1 秒或等于截止都通过，晚 1 秒拒绝。省略预计开始也允许 | TC-LJC-007 至 009 |
| 任务标题 | 200 字有效，成功入库 | TC-LJC-011 |
| 日期筛选含端点 | 上海时间目标日 00:00 入选，前一天 23:59 和次日 00:00 不入选 | TC-LJC-017 |
| 置顶、队列与未读数量 | 空、单项、重复和过去有效但现失效状态 | TC-LJC-023、TC-LJC-025 至 030、TC-LJC-044、TC-LJC-047、TC-LJC-077、TC-LJC-083、TC-LJC-086 |
| 最短非空文本 | 一字退回意见和一字进展有效，纯空白进展拒绝 | TC-LJC-050、TC-LJC-056 至 057 |

### 3.3 自动化用例清单

每个编号对应一个独立 Django 测试方法。执行日志逐条列出方法名及结果，见 [新增用例执行证据](evidence/ljc/1-automated-cases.txt)。

<a id="case-01"></a>
#### 01 `assignable_users()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-001 | 正常 | 两个部门均有经理、组长和员工 | 研发经理调用函数 | 恰好返回本人、本部门组长和另一部门经理 | `test_assignable_manager` | 通过，断言均满足 |
| TC-LJC-002 | 边界 | 普通员工和组长均启用 | 普通员工调用函数 | 结果只有本人 | `test_assignable_employee_self` | 通过，断言均满足 |
| TC-LJC-003 | 异常 | 创始人存在，同组一名员工已停用 | 创始人调用函数 | 启用员工可选，停用员工不在结果中 | `test_assignable_excludes_disabled` | 通过，断言均满足 |

<a id="case-02"></a>
#### 02 `validate_assignee()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-004 | 正常 | 组长与同组员工均启用 | 组长、同组员工 ID | 返回该员工对象 | `test_assignee_same_team` | 通过，断言均满足 |
| TC-LJC-005 | 边界 | 普通员工仅能自派 | 员工、本人 ID | 返回本人对象 | `test_assignee_self` | 通过，断言均满足 |
| TC-LJC-006 | 异常 | 他部门员工和停用员工均存在 | 组长分别校验两人 ID | 均抛 ValidationError，含 assignee_id 字段 | `test_assignee_denied` | 通过，断言均满足 |

<a id="case-03"></a>
#### 03 `validate_task_schedule()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-007 | 正常 | 截止时间已知 | 开始时间比截止早 1 秒 | 返回 None，不报错 | `test_schedule_before_due` | 通过，断言均满足 |
| TC-LJC-008 | 边界 | 截止时间已知 | 开始等于截止，或未填写开始 | 两种输入均返回 None | `test_schedule_equal_due` | 通过，断言均满足 |
| TC-LJC-009 | 异常 | 截止时间已知 | 开始时间比截止晚 1 秒 | 抛 ValidationError，含 estimated_start_at | `test_schedule_after_due` | 通过，断言均满足 |

<a id="case-04"></a>
#### 04 `TaskListView.post()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-010 | 正常 | 研发组长给同组员工派发 | POST 合法字段并关联启用标签 | 201，待审批，历史、标签和审计入库，仅审批人接到待审通知 | `test_create_pending_audit` | 通过，断言均满足 |
| TC-LJC-011 | 边界 | 创始人免审批，负责人为员工 | POST 200 字标题，未填预计开始时间 | 201，原标题入库，已审批且无审批轮次，产生派发通知 | `test_create_founder_max_title` | 通过，断言均满足 |
| TC-LJC-012 | 异常 | 停用标签未曾关联，组长有指派权 | POST tag_ids 含停用标签 ID | 400，无任务、历史、审批、通知或审计写入 | `test_create_rejects_inactive_tag` | 通过，断言均满足 |

<a id="case-05"></a>
#### 05 `TaskListView.get()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-013 | 正常 | 有已审批本人任务、待审批本人任务、他人任务 | GET tasks/ 默认 mine | 仅返回本人已审批记录 | `test_list_mine_only` | 通过，断言均满足 |
| TC-LJC-014 | 边界 | 本人同时为创建人和负责人 | GET scope=all | 交集中的任务只出现一次 | `test_list_union_deduplicates` | 通过，断言均满足 |
| TC-LJC-015 | 异常 | 员工已登录 | GET scope=department | 400，包含 scope 错误 | `test_list_rejects_invalid_scope` | 通过，断言均满足 |

<a id="case-06"></a>
#### 06 `filtered_tasks()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-016 | 正常 | 有已完成、未完成、已取消任务 | completion_status=completed，默认 lifecycle=active | 仅返回活动且已完成的任务 | `test_filter_statuses` | 通过，断言均满足 |
| TC-LJC-017 | 边界 | 截止时间位于 UTC 11 月 19 日 15:59、16:00 和 11 月 20 日 16:00 | due_from=due_to=2026-11-20 | 只返回上海时区当天 00:00 的记录 | `test_filter_inclusive_local_day` | 通过，断言均满足 |
| TC-LJC-018 | 异常 | 有普通任务 | estimated_start_from=2026-11-21，to=2026-11-20 | 抛 ValidationError，标明结束日期参数 | `test_filter_rejects_reversed_dates` | 通过，断言均满足 |

<a id="case-07"></a>
#### 07 `visible_task()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-019 | 正常 | 员工是已审批任务负责人 | 用员工身份查询任务 ID | 返回对应 Task | `test_visible_approved_assignee` | 通过，断言均满足 |
| TC-LJC-020 | 边界 | 任务刚送审，负责人尚不可看 | 当前轮审批人请求任务 ID | 返回待审批 Task | `test_visible_pending_approver` | 通过，断言均满足 |
| TC-LJC-021 | 异常 | 有一项待审和一项已审批任务 | 待审批负责人、无关员工或不存在的 ID | 均抛 NotFound | `test_visible_denies_pending_and_unrelated` | 通过，断言均满足 |

<a id="case-08"></a>
#### 08 `TaskPinView.post()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-022 | 正常 | 已审批的本人任务 | POST pin/ 空请求体 | 201，写入一条置顶，任务、通知和审计不变 | `test_pin_creates_no_side_effects` | 通过，断言均满足 |
| TC-LJC-023 | 边界 | 本人已审批但已取消任务 | 连续两次 POST pin/ | 首次 201，再次 200，数据库只有一条置顶 | `test_pin_repeat_and_cancelled` | 通过，断言均满足 |
| TC-LJC-024 | 异常 | 有可只读查看的下属任务、待审本人任务 | 上级置顶下属任务，负责人置顶待审任务 | 均 403，无置顶记录 | `test_pin_rejects_readonly_pending` | 通过，断言均满足 |

<a id="case-09"></a>
#### 09 `TaskPinView.delete()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-025 | 正常 | 创建人与负责人分别置顶同一任务 | 负责人 DELETE pin/ | 200/pinned=false，仅删除负责人记录 | `test_unpin_keeps_other_account` | 通过，断言均满足 |
| TC-LJC-026 | 边界 | 本人没有置顶记录 | 连续两次 DELETE pin/ | 均为 200/pinned=false，置顶表仍空 | `test_unpin_absent_is_idempotent` | 通过，断言均满足 |
| TC-LJC-027 | 异常 | 已有一条置顶 | DELETE pin/ 携带 user_id | 400，原置顶记录仍存在 | `test_unpin_rejects_body` | 通过，断言均满足 |

<a id="case-10"></a>
#### 10 `PinnedTaskListView.get()`

测试类为 `MidtermTaskQueryTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-028 | 正常 | 账号置顶已完成及已取消任务 | GET pinned/ 带 completion_status=unfinished | 200，返回两条本人已审批置顶 | `test_pinned_ignores_normal_filters` | 通过，断言均满足 |
| TC-LJC-029 | 边界 | 仅另一个任务参与者已置顶 | GET pinned/ | 200，count=0/results=[] | `test_pinned_empty_other_account` | 通过，断言均满足 |
| TC-LJC-030 | 异常 | 置顶后任务被重新送审或改派 | GET 原负责人 pinned/ | 200，不返回待审或改派任务 | `test_pinned_hides_stale` | 通过，断言均满足 |

<a id="case-11"></a>
#### 11 `TaskDetailView.patch()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-031 | 正常 | 组长创建的任务已审批，旧派发通知有效 | PATCH 截止时间延后一天 | 200，任务待审、创建新审批轮次，旧派发通知失效并新增审计 | `test_edit_key_field_reopens_approval` | 通过，断言均满足 |
| TC-LJC-032 | 边界 | 任务已审批 | PATCH 仅关联一个有效标签 | 200，标签生效，原审批状态不变且不新增审批轮次 | `test_edit_tag_only_preserves_approval` | 通过，断言均满足 |
| TC-LJC-033 | 异常 | 有活动、已完成、已取消任务 | 负责人改活动任务，创建人改已完成或已取消任务 | 分别 403 和 400，原标题及历史均不变 | `test_edit_denies_assignee_and_closed` | 通过，断言均满足 |

<a id="case-12"></a>
#### 12 `TaskResubmitView.post()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-034 | 正常 | 第 1 轮被退回，创建人已修改 | POST resubmit/ 空请求体 | 200，返回第 2 轮，任务待审，审计与待审通知已写入 | `test_resubmit_after_edit` | 通过，断言均满足 |
| TC-LJC-035 | 边界 | 第 1 轮退回，尚未修改 | 创建人 POST resubmit/ | 400，仍为退回，只有原轮次 | `test_resubmit_before_edit` | 通过，断言均满足 |
| TC-LJC-036 | 异常 | 任务退回且已满足编辑条件 | 负责人 POST resubmit/ | 403，仍为退回，不新增审批轮次 | `test_resubmit_rejects_non_creator` | 通过，断言均满足 |

<a id="case-13"></a>
#### 13 `TaskHistoryView.get()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-037 | 正常 | 创建与改负责人、标签后，将新负责人及标签重命名 | 创建人 GET history/ | 最新条目仍返回变更时负责人和标签名称及对应 ID | `test_history_preserves_recorded_names` | 通过，断言均满足 |
| TC-LJC-038 | 边界 | 旧变更历史只存负责人和标签数字 ID | 创建人 GET history/ | 标签空 before 保持空，已有 ID 转换为含名称的对象 | `test_history_resolves_legacy_ids` | 通过，断言均满足 |
| TC-LJC-039 | 异常 | 任务由研发组长创建 | 产品员工 GET history/ | 404，响应无 results | `test_history_rejects_outsider` | 通过，断言均满足 |

<a id="case-14"></a>
#### 14 `create_approval_round()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-040 | 正常 | 待审任务已有第 1 轮 | 再次调用函数 | 返回第 2 轮，直属上级收到关联第 2 轮待审通知 | `test_round_increments_and_notifies` | 通过，断言均满足 |
| TC-LJC-041 | 边界 | 任务无任何审批记录 | 首次调用函数 | 返回 round=1、decision=pending，且创建一条通知 | `test_round_starts_at_one` | 通过，断言均满足 |
| TC-LJC-042 | 异常 | 任务创建人的直属上级已停用 | 调用创建审批轮次 | 抛 ValidationError，无审批记录或通知 | `test_round_unavailable_manager` | 通过，断言均满足 |

<a id="case-15"></a>
#### 15 `ApprovalInboxView.get()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-043 | 正常 | 研发与产品分别有一项待审批任务 | 研发经理 GET inbox/ | 仅返回研发任务且审批人为本人 | `test_inbox_current_approver_only` | 通过，断言均满足 |
| TC-LJC-044 | 边界 | 曾有两条待审批，一条取消，一条已审批 | GET inbox/ | 200，count=0/results=[] | `test_inbox_excludes_cancelled_and_handled` | 通过，断言均满足 |
| TC-LJC-045 | 异常 | 未登录 | GET inbox/ | 401 | `test_inbox_rejects_anonymous` | 通过，断言均满足 |

<a id="case-16"></a>
#### 16 `ApprovalOutboxView.get()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-046 | 正常 | 本人有待审和退回任务，其他组长也已送审 | 本人 GET outbox/ | 仅返回本人待审和退回两条记录 | `test_outbox_own_pending_returned` | 通过，断言均满足 |
| TC-LJC-047 | 边界 | 本人曾有待审任务，现分别已通过、已取消 | GET outbox/ | 200，count=0/results=[] | `test_outbox_excludes_approved_and_cancelled` | 通过，断言均满足 |
| TC-LJC-048 | 异常 | 未登录 | GET outbox/ | 401 | `test_outbox_rejects_anonymous` | 通过，断言均满足 |

<a id="case-17"></a>
#### 17 `TaskApprovalView.post()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-049 | 正常 | 第 1 轮待审批且已有待审通知 | 直属上级 POST decision=approved | 200，任务通过，待审通知失效，创建人获通过通知且负责人获派发通知 | `test_approval_accepts_and_dispatches` | 通过，断言均满足 |
| TC-LJC-050 | 边界 | 第 1 轮待审批 | 直属上级提交 returned 并填写 1 字意见 | 200，待重新编辑，创建人收到含原轮次和原因的退回通知，无派发通知 | `test_approval_returns_with_reason` | 通过，断言均满足 |
| TC-LJC-051 | 异常 | 待审批任务存在 | 非本轮审批人通过；直属审批人处理后重复通过 | 403 和 400，始终只有一条成功的审批结果通知 | `test_approval_denies_wrong_actor_and_repeat` | 通过，断言均满足 |

<a id="case-18"></a>
#### 18 `require_executable()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-052 | 正常 | 任务已审批、活动、未完成 | 负责人执行校验 | 返回 None，不抛错 | `test_executable_approved_active` | 通过，断言均满足 |
| TC-LJC-053 | 边界 | 分别存在待审、已完成和已取消任务 | 负责人尝试执行 | 三种状态均抛 ValidationError | `test_executable_closed_or_pending` | 通过，断言均满足 |
| TC-LJC-054 | 异常 | 任务已审批且活动，操作者为创建人但非负责人 | 创建人调用执行校验 | 抛 PermissionDenied | `test_executable_denies_non_owner` | 通过，断言均满足 |

<a id="case-19"></a>
#### 19 `TaskUpdateView.post()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-055 | 正常 | 任务已有一条进展 | 负责人 POST 新进展 | 201，新旧两条均存在，任务仍未完成，新增审计 | `test_progress_appends_without_completing` | 通过，断言均满足 |
| TC-LJC-056 | 边界 | 可执行任务 | 负责人 POST content='  好  ' | 201，返回和数据库内容均为单字“好” | `test_progress_one_char_trimmed` | 通过，断言均满足 |
| TC-LJC-057 | 异常 | 可执行任务，负责人以外另有创建人 | 本人传空白内容；创建人传有效内容 | 400 和 403，无进展、无审计 | `test_progress_rejects_blank_and_non_owner` | 通过，断言均满足 |

<a id="case-20"></a>
#### 20 `TaskSubmissionView.post()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-058 | 正常 | 他人派发，需附材料说明，有旧派发和退回通知 | POST 成果说明及材料位置 | 201，状态 waiting；任务仍未完成，旧通知失效，仅创建人收到本轮待验收并写审计 | `test_submit_waits_and_notifies_creator` | 通过，断言均满足 |
| TC-LJC-059 | 边界 | 已审批自派任务，无需材料 | 本人提交最短的 1 字成果说明 | 201/self_completed，任务完成且有完成时间，不发送本人通知 | `test_submit_self_completes` | 通过，断言均满足 |
| TC-LJC-060 | 异常 | 有必填材料要求，随后已经提交一个有效结果 | 省略材料说明；第一次成功提交后重提 | 两次非法请求均 400，只保留一条提交和一条待验收通知 | `test_submit_rejects_missing_material_and_duplicate` | 通过，断言均满足 |

<a id="case-21"></a>
#### 21 `TaskReviewView.post()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-061 | 正常 | 已有待验收记录和关联待验收通知 | 创建人 POST decision=accepted | 200，任务完成且有完成时间，关闭待验收，向负责人发通过通知并写审计 | `test_review_accepts_and_resolves_notice` | 通过，断言均满足 |
| TC-LJC-062 | 边界 | 已有待验收成果 | 创建人以 1 字原因退回，负责人提交新版 | 退回 200，保留原原因；重提 201 且留下新旧两条，旧退回通知结束 | `test_review_returns_then_allows_new_submission` | 通过，断言均满足 |
| TC-LJC-063 | 异常 | 已有待验收成果，经理可只读 | 经理尝试验收；创建人退回后尝试再次处理同一记录 | 403 和 400，只写入一条验收结果通知 | `test_review_denies_manager_and_second_decision` | 通过，断言均满足 |

<a id="case-22"></a>
#### 22 `TaskCancelView.post()`

测试类为 `MidtermTaskLifecycleTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-064 | 正常 | 已审批任务，有派发通知 | 创建人取消，reason=计划调整 | 200，任务归档，派发通知失效，负责人收到取消原因并写审计 | `test_cancel_approved_notifies_assignee` | 通过，断言均满足 |
| TC-LJC-065 | 边界 | 任务仍在第 1 轮待审，尚未派发 | 创建人取消 | 200，待审失效，通知仅发当前审批人 | `test_cancel_pending_notifies_approver` | 通过，断言均满足 |
| TC-LJC-066 | 异常 | 有活动、已完成和已取消任务 | 负责人取消活动任务；创建人取消完成任务或再次取消 | 403 或 400，业务状态、通知及审计均无非法变化 | `test_cancel_denies_non_creator_closed_and_repeat` | 通过，断言均满足 |

<a id="case-23"></a>
#### 23 `create_task_notification()`

测试类为 `MidtermTaskNotificationTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-067 | 正常 | 任务有一项提交记录 | 传创建人、待验收类型、事件键及 submission | 返回通知，接收人、任务、提交与未读字段均正确 | `test_notice_creation_links_submission` | 通过，断言均满足 |
| TC-LJC-068 | 边界 | 首次事件已入库 | 重放同一个事件键 | 返回同一主键，通知表仍只有一条 | `test_notice_creation_deduplicates` | 通过，断言均满足 |
| TC-LJC-069 | 异常 | 任务存在但没有接收人 | recipient=None | 返回 None，不写入通知 | `test_notice_without_recipient` | 通过，断言均满足 |

<a id="case-24"></a>
#### 24 `resolve_task_notifications()`

测试类为 `MidtermTaskNotificationTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-070 | 正常 | 同任务有不同提交的待验收通知及不同类型通知 | 只结束指定任务、类型和提交 | 更新数量 1，目标写 resolved_at 但仍未读，其他通知不变 | `test_resolve_scopes_type_and_submission` | 通过，断言均满足 |
| TC-LJC-071 | 边界 | 目标通知已于固定时间失效 | 重复调用关闭通知 | 更新数量 0，原失效时间保留 | `test_resolve_keeps_existing_timestamp` | 通过，断言均满足 |
| TC-LJC-072 | 异常 | 目标任务没有匹配通知，另有任务存在有效通知 | 对目标任务按通知类型关闭 | 更新数量 0，另一任务通知仍有效 | `test_resolve_does_not_touch_other_task` | 通过，断言均满足 |

<a id="case-25"></a>
#### 25 `send_assignment()`

测试类为 `MidtermTaskNotificationTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-073 | 正常 | 负责人和创建人不同 | send_assignment(task, 17) | 只向负责人发派发通知，事件键对应 17 | `test_dispatch_notifies_assignee` | 通过，断言均满足 |
| TC-LJC-074 | 边界 | 免审批他派任务 | 无 approval_id 连续调用两次 | 只生成一条事件键以 :0 结尾的通知 | `test_dispatch_default_and_repeat` | 通过，断言均满足 |
| TC-LJC-075 | 异常 | 创建人同时为负责人 | 调用 send_assignment | 无本人派发通知 | `test_dispatch_suppresses_self_assignment` | 通过，断言均满足 |

<a id="case-26"></a>
#### 26 `notification_data()`

测试类为 `MidtermTaskNotificationTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-076 | 正常 | 第 1 轮退回，第 2 轮已重新送审 | 序列化第 1 轮退回结果通知 | round=1、decision=returned、旧退回理由，任务当前状态仍 pending | `test_notice_data_keeps_original_round` | 通过，断言均满足 |
| TC-LJC-077 | 边界 | 普通已派发通知未关联审批或提交 | 传入该通知对象 | 审批轮次和决定为 None，摘要为空，任务 ID 正确 | `test_notice_data_without_approval_context` | 通过，断言均满足 |
| TC-LJC-078 | 异常 | 历史审批通知事件键的轮次无法解析 | 序列化异常键 approval:任务ID:bad | 仍返回对应通知，不伪造审批轮次和决定 | `test_notice_data_tolerates_bad_round` | 通过，断言均满足 |

<a id="case-27"></a>
#### 27 `NotificationListView.get()`

测试类为 `MidtermTaskNotificationTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-079 | 正常 | 本人有未读和已结束通知，另一人也有通知 | GET notifications/ | 只返回本人两条，浏览不改变已读状态或读时刻 | `test_notices_history_read_has_no_side_effects` | 通过，断言均满足 |
| TC-LJC-080 | 边界 | 本人有 1 条有效未读、1 条已读及 1 条已结束 | GET unread_only=true&page_size=1 | 只返回目标且 count=1、next=null | `test_notices_unread_filters_resolved_and_read` | 通过，断言均满足 |
| TC-LJC-081 | 异常 | 员工已登录 | GET unread_only=yes | 400，错误标明 unread_only | `test_notices_reject_invalid_unread_filter` | 通过，断言均满足 |

<a id="case-28"></a>
#### 28 `NotificationUnreadCountView.get()`

测试类为 `MidtermTaskNotificationTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-082 | 正常 | 本人有 2 条有效未读、已读与已结束各一条，其他账号也有未读 | GET unread-count/ | 200/unread_count=2 | `test_notice_count_counts_only_active_unread` | 通过，断言均满足 |
| TC-LJC-083 | 边界 | 本人唯一未读通知已结束 | GET unread-count/ | 200/unread_count=0，历史通知仍未读 | `test_notice_count_zero_after_resolving` | 通过，断言均满足 |
| TC-LJC-084 | 异常 | 未登录，系统已有通知 | GET unread-count/ | 401，响应无 unread_count | `test_notice_count_rejects_anonymous` | 通过，断言均满足 |

<a id="case-29"></a>
#### 29 `NotificationMarkReadView.post()`

测试类为 `MidtermTaskNotificationTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-085 | 正常 | 本人有两条未读，另一人也有一条 | 提交本人一个 ID 和他人 ID | marked_read_count=1，仅指定本人通知写入 is_read 和 read_at | `test_mark_read_changes_only_requested_owned` | 通过，断言均满足 |
| TC-LJC-086 | 边界 | 有一条本人未读 | 空数组、重复 ID、再次提交同 ID | 更新数量依次 0、1、0，重复操作保留首次 read_at | `test_mark_read_empty_duplicate_and_repeat` | 通过，断言均满足 |
| TC-LJC-087 | 异常 | 本人有一条未读通知 | notification_ids 同时传合法 ID 和 0 | 400，合法那条也保持未读 | `test_mark_read_rejects_invalid_id_atomically` | 通过，断言均满足 |

<a id="case-30"></a>
#### 30 `unread_notification_count()`

测试类为 `MidtermTaskNotificationTests`。

| 编号 | 类型 | 前置条件 | 输入／操作 | 期望输出与副作用 | 测试方法 | 实际结果 |
| --- | --- | --- | --- | --- | --- | --- |
| TC-LJC-088 | 正常 | 本人有 2 条有效未读，另有已读和已结束通知 | 同一个用户调用服务和未读接口 | 两处均为 2 | `test_home_count_matches_notification_endpoint` | 通过，断言均满足 |
| TC-LJC-089 | 边界 | 当前用户没有任何通知 | 调用 unread_notification_count(user) | 返回整数 0 | `test_home_count_zero_for_empty_account` | 通过，断言均满足 |
| TC-LJC-090 | 异常 | 本人只有已结束通知，其他账号有有效未读 | 使用两个账号分别调用服务 | 本人为 0，其他账号为 1 | `test_home_count_excludes_foreign_and_resolved` | 通过，断言均满足 |

### 3.4 Android 手动验证

在隔离环境中用合成的研发经理、研发组长和研发员工角色准备各测试任务，同一审批流程的处理人和结果接收人分别登录。手动用例与自动化统计分开，以下三条因设备条件不满足而阻塞。

| 编号 | 类型 | 前置条件与输入／操作 | 预期界面行为 | 实际结果 |
| --- | --- | --- | --- | --- |
| MAN-LJC-001 | 正常 | 组长创建任务，经理通过，组长打开首页铃铛并点本轮「审批通过」结果 | 打开对应任务详情，显示通过状态，仅所点击的通知标记已读 | 阻塞，当前无可用设备 |
| MAN-LJC-002 | 边界 | 经理通过待审通知打开详情，填写退回理由并提交，返回列表后点原待审通知 | 列表刷新显示退回，原待审通知不再打开任务或审批入口 | 阻塞，当前无可用设备 |
| MAN-LJC-003 | 正常 | 组长收到第 1 轮退回结果后修改并重提，再点历史「第 1 轮审批退回」结果 | 原轮次和原因仍可见，创建人可打开其有权查看的任务详情，无审批操作权限 | 阻塞，当前无可用设备 |

## 4. 测试执行

从仓库根目录进入 `backend`，在安装 `requirements.txt` 的 Python 环境中运行。下列 `python` 指本次使用的虚拟环境解释器。

```powershell
cd backend
$env:PYTHONUTF8 = '1'
python manage.py test apps.tasks.test_midterm_ljc_tasks_notifications --noinput --verbosity 2
python manage.py test apps.tasks apps.home --noinput --verbosity 2
python manage.py test --noinput --verbosity 1
```

| 轮次 | 时间（北京时间） | 范围与版本 | 发现 | 实际结果 | 证据 |
| --- | --- | --- | ---: | --- | --- |
| 基准 | 2026-11-19，增加测试前 | `python manage.py test --noinput --verbosity 1`，纯基准 `bdff2db` | 97 | 97 通过，退出码 0 | 本地终端 |
| 新增首跑 | 2026-11-19 | 新增文件未提交的初稿 | 90 | 88 通过、1 失败、1 执行错误，均为测试本身问题 | 见第 6 节 |
| 新增重跑 | 2026-11-19 15:41 | 新增文件修正后，内容与 `049dc6a` 一致 | 90 | 90 通过，1.248 秒，退出码 0 | [逐条执行输出](evidence/ljc/1-automated-cases.txt) |
| 模块回归 | 2026-11-19 15:41 | `apps.tasks apps.home`，`049dc6a` | 123 | 123 通过，3.301 秒，退出码 0 | [任务与首页回归输出](evidence/ljc/2-task-home-regression.txt) |
| 后端全量 | 2026-11-19 15:42 | 整个后端，`049dc6a` | 187 | 187 通过，8.032 秒，退出码 0，存在基准已有的 schema 警告 | [完整后端输出](evidence/ljc/3-full-backend.txt) |

## 5. 统计与覆盖检查

| 用例 | 已设计 | 已执行 | 通过 | 失败 | 阻塞 | 未执行 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 后端正常 | 30 | 30 | 30 | 0 | 0 | 0 |
| 后端边界 | 30 | 30 | 30 | 0 | 0 | 0 |
| 后端异常 | 30 | 30 | 30 | 0 | 0 | 0 |
| Android 手动 | 3 | 0 | 0 | 0 | 3 | 0 |

最终重跑 90 条新增测试均通过，失败、执行错误及跳过均为 0。单条方法内使用的小范围子场景按该方法计一次。第 2 节 30 个函数逐一映射到第 3.3 节的 3 个不同编号，其中每组依次为正常、边界、异常。全量 187 条包含基准原有 97 条和新增 90 条，三轮统计存在重叠。

## 6. 问题与缺陷记录

- **TEST-LJC-001，测试用例错误，已修正。** TC-LJC-010 原应读取任务创建审计的标题快照，首跑误用了不存在的 `object_label` 字段并抛出 `AttributeError`。改用实际持久化字段 `object_label_snapshot` 后，重跑验证审计标题为「新任务」。
- **TEST-LJC-002，测试夹具错误，已修正。** TC-LJC-042 预期停用直属上级后不能新建审批轮次。首跑对数据库停用账号后仍用已有对象的缓存外键，导致没有抛错。重新从数据库加载任务后，重跑验证 `ValidationError`，且无审批或通知写入。
- **OBS-LJC-001，基准已有警告，待跟踪。** 在 `bdff2db` 上运行全量后端测试时，drf-spectacular 报告工作视图和日志各自的 `CalendarDaySerializer` 共用 OpenAPI 组件名 `CalendarDay`。新增测试后的全量执行也出现该警告。后端测试仍通过，可能影响生成的 OpenAPI 日期结构，见 [完整后端输出](evidence/ljc/3-full-backend.txt)。
- 本次已经执行的任务、置顶和通知范围内未发现业务缺陷。Android 结果待手动验收。

## 7. 代码覆盖率

未生成百分比覆盖率报告。本轮依照 Issue 的函数与用例映射验收，并记录测试运行器的实际结果，代码行覆盖率未测量。

## 8. 结论与汇总交接

任务生命周期、置顶和通知的 30 个核心函数都有正常、边界、异常三类验证。新增、相关模块和单人分支的全量后端执行均通过。中期汇总需要保留第 6 节的基准警告和第 3.4 节的 Android 手动未执行项。上述全量结果来自梁家程分支，团队汇总时需在四组测试整合后的统一版本再次运行。

汇总材料为本报告、[新增代码](../../backend/apps/tasks/test_midterm_ljc_tasks_notifications.py)、[执行证据](evidence/ljc/1-automated-cases.txt)及第 1 节所列基准和测试代码版本。

## 附录 A：AI 辅助与人工复核

- 工具为 Codex，使用日期为 2026-11-19。输入为 Issue #42、团队基准的业务代码、需求与报告模板。辅助覆盖 TC-LJC-001 至 090 的设计、代码及报告编排。结果以本地实际执行输出为准。
- 以下是本轮实际提示词。

> 完成issue内容中要求的中期测试提交pr并完成文档，文档中保留具体函数，注明函数作用和测试用例（对用例说明该测试用例类型，期望输入输出），注明团队统一的测试基准提交、执行命令和实际结果

- **人工逐条复核状态为待完成。** 梁家程本人或评审人尚未确认复核结果。第 3.3 节逐条列有复核所需的前置条件、输入、预期输出、定位和实际结果。复核时按用例编号登记需求依据、输入有效性、断言覆盖、人工调整与理由及复核人。首跑修正已在第 6 节如实记录。

## 附录 B：提交前自查

- [x] 30 个分配函数逐一映射到可独立执行的用例，每个函数有正常、边界、异常三类。
- [x] 保留原有测试，已记录实际测试版本、命令、输出、统计和环境限制。
- [x] 测试错误、基准警告和未执行的手动验证已登记。
- [x] 已保留实际 AI 提示词。
- [ ] 人工逐条复核，待本人或评审人确认。
