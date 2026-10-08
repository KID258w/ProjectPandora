## Why

任务进度回答任务推进到哪里，工作日志记录某一天实际做了什么；两者相关但不能互相替代。需要提供独立日志入口，并确保草稿隐私、每日唯一和管理者只读查看边界。

## What Changes

- 新增移动端每日工作日志的创建、草稿保存、提交、同日编辑与历史只读查看。
- 每位用户每天最多一篇；“今日完成”为提交必填，任务关联可选且仅从本人任务中选择。
- 日志提交后可由授权管理者按组织范围向下查看已提交内容；草稿只对作者可见。
- 首页读取最近十篇已提交日志摘要；工作视图不读取或展示任何日志信息。
- MVP 不含附件、评论、日志搜索、管理者修改/验收或补录过去日期。

## Capabilities

### New Capabilities
- `daily-logs`: 每日日志填写、提交、历史查看与授权的下属日志只读查看。

### Modified Capabilities
- None.

## Impact

- 关联 `docs/requirements/03-daily-log-module.md`、`02-home-four-panels.md`、`04-month-week-day-work-views.md`。
- 影响 Android 日志 Tab、DailyLog 与任务关联数据、日志 API/组织范围授权及首页摘要查询。依赖身份/组织与任务基础；日志关联不自动改任务进度。
