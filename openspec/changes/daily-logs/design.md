## Context

每日日志独立于任务进度和任务验收。本人可维护当日日志；管理者可沿授权组织树查看直接/间接下属的已提交日志，但不能知道下属是否有草稿或未填写。

## Goals / Non-Goals

**Goals:** 每日唯一、草稿/提交、同日修改、历史、本人任务可选关联、授权管理者只读查看。

**Non-Goals:** 附件、评论、搜索、管理者写入或验收、过去日期补录、视图 Tab 中显示日志。

## Decisions

- DailyLog 以作者+自然日期唯一；状态为未填写（无记录）、草稿、已提交。数据库唯一约束防止并发重复创建。
- 今日完成是唯一提交必填项；关联任务、问题与风险、明日计划可选。关联通过日志编辑流程维护，一篇日志可连多项本人任务，不写回任务进度。
- 草稿仅作者可读；提交后只向授权管理者的下属日志查询返回。管理者看不到草稿状态或缺日志状态，只展示查询范围内的已提交记录。
- 作者当天可编辑草稿/已提交记录；自然日结束后只读且不可补写/提交；修改已提交内容仍为已提交，只更新最后修改时间、不保存版本历史。
- 角色范围由组织关系解析：团队长团队、部门经理部门、创始人公司；默认本人，管理者筛选对象时显示当前对象和组织路径。
- API 以字段白名单返回摘要和正文；首页取最近十篇已提交记录，视图模块不得调用日志数据。

## Alternatives Considered

- Requiring every log to link to a task was rejected because meetings or training may not correspond to a formal task.
- Showing managers whether a subordinate has a draft or no log was rejected to preserve draft privacy; manager queries return submitted records only.
- Letting managers edit or accept logs was rejected; logs are author-maintained records, not an acceptance workflow.

## Risks / Trade-offs

- [时区边界影响“当天”判定] → 后端使用统一配置的业务时区进行唯一性与日终只读校验。
- [管理者查询暴露草稿状态] → 查询层只检索 submitted，不返回草稿/未填写占位。
- [任务已取消/完成可能影响关联] → 保留历史关联显示，不允许日志编辑器选择无权限或非本人执行的任务。

## Migration Plan

新增每日日志、任务关联表及提交时间字段。部署时无需为历史日期生成空日志；首页仅展示已有已提交记录。回滚关闭日志入口/API且保留数据。

## Open Questions

- 业务自然日所用时区需在部署配置中确认；不改变“自然日结束后冻结”的产品规则。
