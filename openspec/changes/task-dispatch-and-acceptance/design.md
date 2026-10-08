## Context

业务层级为创始人→部门经理→团队长→普通员工。任务是具备父子关系的正式记录；执行进度与验收过程必须可追溯，根任务进度由负责人手动维护，不根据子任务自动加权。

## Goals / Non-Goals

**Goals:** 实现角色允许范围内的建任务/向下一层派发、拆分、本人执行、进度历史、提交验收、修改与取消联动。

**Non-Goals:** 跨部门请求、自动推算父任务进度、任务界面下属穿透、已完成任务重开、提交撤回和延期申请。

## Decisions

- 使用统一 Task 实体，以 parent relation 表达任务树；分配列表、我派发的和团队任务是同一批记录的权限化视图。
- 服务端根据当前用户角色、组织关系、直接派发关系校验创建/分配/修改/验收/取消权限，不能依赖客户端隐藏按钮。
- 普通员工在待开始状态主动开始；团队长/部门经理首次打开上级派发任务详情时转为进行中并记录开始时间。本人子任务仅标记完成，不填百分比、不自我验收。
- 执行进度更新保存历史；提交进入待验收，直接派发者通过则完成，退回必须有原因。
- 直接派发者可修改非待验收/已完成任务定义并写历史。取消必须填理由，在事务中级联取消未完成后代，已完成后代不变；子任务取消不影响父/兄弟任务。
- 根任务完成需由负责人汇总必要子任务并手动结束；不自动汇总子任务百分比。
- 关键流程事件产生应用内红点提醒；Android 系统通知不作为 MVP 验收条件。

## Alternatives Considered

- Duplicating task records for each list was rejected; a single task tree preserves consistent assignment and acceptance history.
- Automatically calculating parent progress from child percentages was rejected; the responsible manager maintains overall progress manually.
- Hiding unauthorized controls only in the client was rejected; the server must enforce permissions.

## Risks / Trade-offs

- [任务树权限易出现越权] → 所有写入和读取在服务端按直接关系与任务树授权，增加越权测试。
- [取消级联部分成功会破坏一致性] → 在数据库事务内处理目标分支并记录取消来源/原因。
- [状态很多] → 用受控状态迁移服务统一校验合法的旧状态、操作者和新状态。

## Migration Plan

新系统新增任务、进度、提交/验收、修改和取消历史结构；先完成身份与组织基础，再开放移动端任务 API。回滚仅关闭新任务入口/API，保留已存历史。

## Open Questions

- 附件存储格式/大小及具体任务表字段在数据库详细设计阶段确定；不得改变本规格中的权限和状态约束。
