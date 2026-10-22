## Context

每日日志独立于任务进度和任务验收。本人可维护今天、昨天及前天的日志；管理者可沿授权组织树查看直接/间接下属的已提交日志，但不能知道下属是否有草稿或未填写。

## Goals / Non-Goals

**Goals:** 每日唯一、草稿/提交、近三天补写与修改、超过窗口冻结、默认日历与日期详情、年月快捷切换、授权管理者只读查看及人员查找。

**Non-Goals:** 附件、评论、日志正文/摘要搜索、管理者写入或验收、补录超过近三天窗口的历史日期、未来日期写入、视图 Tab 中显示日志、任务关联及问题/风险字段。

## Decisions

- DailyLog 以作者+自然日期唯一；状态为未填写（无记录）、草稿、已提交。数据库唯一约束防止并发重复创建。
- 今日完成是唯一提交必填项，明日计划可选。日志仅包含工作文字，不设置任务关联及问题/风险字段。
- 日志 Tab 首屏为月历和选中日期的日志详情；年月标题打开快捷年月选择器。本人选中今天、昨天或前天时，右下角显示圆形新增/编辑按钮并打开选中日期表单；选中窗口外日期或查看下属时不显示该按钮。
- 管理者对象选择窗口支持按姓名、团队或部门关键词筛选授权范围内人员。搜索只匹配人员信息，不搜索日志正文、摘要或历史内容，也不扩大组织范围。
- 草稿仅作者可读；提交后只向授权管理者的下属日志查询返回。管理者看不到草稿状态或缺日志状态，只展示查询范围内的已提交记录。
- 作者可在业务时区内今天、昨天、前天三日范围创建/编辑草稿和已提交记录，包含补写过去两天；范围按自然日滚动，非 72 小时。日期早于前天或在未来时只读/不可写；修改已提交内容仍为已提交，只更新最后修改时间、不保存版本历史。
- 角色范围由组织关系解析：团队长团队、部门经理部门、创始人公司；默认本人，管理者筛选对象时显示当前对象和组织路径。无已提交日志的日期显示空状态，不暴露草稿或未填写区别。
- API 以字段白名单返回摘要和正文；首页取最近五篇已提交记录，视图模块不得调用日志数据。

## Alternatives Considered

- Keeping task association and issue/risk fields was removed from the MVP form to keep daily logging focused on work notes and next-day plans.
- Showing managers whether a subordinate has a draft or no log was rejected to preserve draft privacy; manager queries return submitted records only.
- Letting managers edit or accept logs was rejected; logs are author-maintained records, not an acceptance workflow.

## Risks / Trade-offs

- [时区边界影响可编辑三日窗口判定] → 后端使用统一配置的业务时区进行唯一性、日期窗口和冻结校验。
- [管理者查询暴露草稿状态] → 查询层只检索 submitted，不返回草稿/未填写占位。
- [人员搜索被误解为日志内容搜索] → 搜索框只在查看对象选择窗口使用，并明确提示按姓名、团队或部门搜索。

## Migration Plan

新增每日日志记录及提交时间字段，不创建日志—任务关联表。部署时无需为历史日期生成空日志；首页仅展示已有已提交记录。回滚关闭日志入口/API且保留数据。

## Open Questions

- 业务自然日所用时区需在部署配置中确认；三日窗口按该时区自然日计算，不改变“超出窗口后冻结”的产品规则。
