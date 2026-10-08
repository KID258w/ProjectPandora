## Why

用户需要在月、周、日尺度回顾工作安排和任务变化；管理者也需要按授权范围查看下属任务视图。视图必须是只读任务投影，避免与独立日志功能重复。

## What Changes

- 新增移动端月/周/日只读工作视图，默认进入当前周，并支持周期切换和查看对象筛选。
- 月视图显示任务持续范围和截止节点；周/日按实际任务进度和状态变化展示。
- 当前周和今日显示对应未完成任务区域；过去周期不显示待办区，历史状态按自然日结束时冻结。
- 视图只显示任务、进度和状态历史，不显示日志、公司事项或个人自建习惯；不跳转任务详情、不提供编辑操作。

## Capabilities

### New Capabilities
- `month-week-day-work-views`: 基于正式任务及其历史记录生成的个人/授权下属工作视图。

### Modified Capabilities
- None.

## Impact

- 关联 `docs/requirements/04-month-week-day-work-views.md`、`01-task-creation-and-acceptance.md`、`03-daily-log-module.md`。
- 影响 Android 视图 Tab、任务视图查询/API、进度/状态历史与组织范围读取。依赖身份组织、任务与历史记录；不依赖日志展示。
