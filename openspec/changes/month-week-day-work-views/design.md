## Context

月、周、日视图仅聚合正式任务及任务历史。自然日结束后视为历史快照；当前日/当前周有未完成任务提示，历史或未来周期不显示该区域。

## Goals / Non-Goals

**Goals:** 当前用户任务日历与进展回顾、管理者授权筛选下属、当前周/今日待办区域、历史冻结。

**Non-Goals:** 日志摘要/正文/标记、任务编辑与跳转、团队叠加日历、公司事项、习惯计划和额外搜索。

## Decisions

- 使用统一视图查询层按用户任务范围读取 Task、progress history、status history；不复制业务数据，不查询 DailyLog 或任务-日志关联表。
- 首次进入 Tab 展示当前周；用户可切换月/周/日并前后导航，月/周日期点击仅进入日视图。视图全程只读。
- 月视图以任务开始/截止区间绘制连续任务条；只有截止日的任务只出现在截止日。
- 周视图逐日展示当天实际产生的进度/状态变更，不重复展示无变化任务；当前周额外列出未完成、已开始/逾期/本周计划开始的本人任务。
- 日视图展示当天安排与变化；仅今天显示“今日未完成任务”。过去/未来日期均无待办区；未来只显示计划任务。
- 历史周期查询按各自然日结束时重建任务状态，后续更改不能回写旧日状态。查看对象过滤按日志模块相同的组织树权限，但只查询工作视图数据。

## Alternatives Considered

- Rewriting historical dates with a task's current state was rejected; historical state is reconstructed from events through that date's end.
- Combining logs with task events was rejected because logs have separate privacy and viewing rules.
- A multi-person team calendar was rejected for MVP; managers select one authorized person at a time.

## Risks / Trade-offs

- [历史状态快照成本] → 从状态变更历史按日期重建，不额外复制快照；需要覆盖同日多次变化的边界测试。
- [周/日数据重复] → 每天只取有变化的事件，未完成区只展示当前摘要。
- [时区造成冻结偏差] → 使用统一业务时区计算日界并在部署配置中明确。

## Migration Plan

复用任务及历史数据，不新增用户维护的数据。确保进度和状态事件有准确时间戳后开放视图查询；回滚关闭视图入口。

## Open Questions

- 业务时区通过部署配置确定；任务时间字段的精度/显示格式在 UI 设计阶段确认。
