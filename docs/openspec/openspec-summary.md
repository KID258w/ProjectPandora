# Project Pandora OpenSpec Change 总览

更新时间：2026-10-08  
阶段：Sprint 0 需求分析与设计  
状态：已为 10 个候选 change 建立 proposal、spec、design、tasks 初稿；所有 tasks 尚未实施。OpenSpec 的“规划产物完成”只代表文档齐全，不代表代码已实现或验收。

## 1. Change 列表与范围

| # | Change | 功能范围 | 不负责的内容 | 主要需求来源 |
|---|---|---|---|---|
| 1 | `admin-identity-and-organization` | 管理员 Web 登录、部门/团队、业务账号/角色/组织归属、负责人变更、停用、管理操作审计；管理员不能读写业务任务和员工日志 | 公司重要事项内容维护、业务任务/日志查看 | `05-web-admin-console.md` |
| 2 | `company-important-items` | 管理员维护公司重要事项：草稿、编辑、发布/下架、手动排序、预览及可选附件；移动端所有用户看到相同的已发布事项，最多 10 条 | “公司十大重要任务”（它按当前用户任务生成）、任务进度或派发 | `02-home-four-panels.md`、`05-web-admin-console.md` |
| 3 | `task-dispatch-and-acceptance` | 创始人→部门经理→团队长→员工的任务创建/向下派发、拆分、本人执行、进度历史、提交、直接派发者验收/退回、修改、理由必填的取消/向下级联、红点提醒 | 部门经理之间的跨部门请求；任务页面向下穿透员工任务 | `01-task-creation-and-acceptance.md` |
| 4 | `cross-department-coordination` | 部门经理发起请求，目标部门经理接受/拒绝，目标部门内部执行，发起方验收/退回；接受前可填写理由撤回，接受后 MVP 不可取消 | 创始人查看/协调、跨部门直接给员工派任务、读取对方部门的员工任务明细 | `01-task-creation-and-acceptance.md` |
| 5 | `daily-logs` | 每人每天一篇日志、草稿/提交、当日编辑、历史只读、可选关联本人任务；管理者按组织范围只读查看下属已提交日志，草稿不可见 | 附件、评论、搜索、日志验收、管理员查看、工作视图展示日志 | `03-daily-log-module.md` |
| 6 | `month-week-day-work-views` | 月/周/日只读任务视图；默认当前周；按授权对象筛选；当前周/今日显示未完成任务；过去日期按自然日结束状态冻结 | 日志内容/摘要、任务编辑/跳转、团队多人叠加日历 | `04-month-week-day-work-views.md` |
| 7 | `home-dashboard-panels` | 首页四面板：公司重要事项、按当前用户任务生成的公司十大重要任务、用户自选排序的个人十大重点工作、最近十篇已提交日志摘要 | 首页承载完整任务操作或日志填写、管理者自动看到下属数据 | `02-home-four-panels.md` |
| 8 | `ai-work-assistant` | AI 地图/工作助手入口、任务咨询、日志草稿整理、近期工作总结、多个独立对话、用户选取上下文、同意与服务端模型调用 | 自主 Agent、跨对话长期记忆、自动修改任务/进度/正式日志 | `06-ai-map-and-assistant.md` |
| 9 | `ai-mbti-assessment` | MBTI 自我探索问答、结果展示和重测；仅本人可读，仅保留最近一次完成结果 | 诊断、招聘/绩效使用、管理者查看、将结果自动注入其他 AI 对话 | `06-ai-map-and-assistant.md` |
| 10 | `ai-treehole-support` | 私密、非临床的情绪倾诉与梳理；多对话、当前对话上下文、删除、真人帮助入口 | 读取任务/日志、诊断/治疗、管理者查看、危机干预承诺 | `06-ai-map-and-assistant.md` |

所有 change 的逐条场景、设计决策和可执行清单以各自目录内的 `proposal.md`、`specs/<capability>/spec.md`、`design.md`、`tasks.md` 为准。本总览用于导航，不取代它们。

## 2. 建议依赖与实施顺序

这些 change 可以在 Sprint 0 并行评审和细化；依赖关系主要约束后续集成/实施顺序：

```text
admin-identity-and-organization
├─ company-important-items
├─ task-dispatch-and-acceptance
│  ├─ cross-department-coordination
│  ├─ daily-logs
│  ├─ month-week-day-work-views
│  └─ home-dashboard-panels  ← 还依赖公司事项与日志
└─ ai-work-assistant        ← 工作上下文集成还依赖任务与日志
   └─ ai-treehole-support   ← 复用对话、同意与服务端 AI 基础设施

ai-work-assistant 的 AI 地图导航基础 ── ai-mbti-assessment
```

建议先完成管理员身份/组织基础，然后并行推进公司事项和任务核心；任务基础稳定后接入跨部门、日志和工作视图；首页聚合依赖事项、任务、日志数据就绪。AI 对话底座可单独评审，但工作上下文要等任务/日志 API；树洞依赖共享 AI 服务边界。MBTI 的问卷/计分仍需先确定，不应因其它 AI 功能已建 change 就假定题库已选。

## 3. 后续如何修改

### 3.1 先判断改的是“需求”还是“实现细节”

1. 先说明希望改变的用户行为、原因和适用角色/边界。若是产品决策，先更新 `docs/requirements/` 对应需求文档，让它继续作为团队确认的需求来源。
2. 若只是实现细节（例如附件大小、表单排版、具体数据库字段），更新对应 change 的 `design.md` 或详细设计文档，不要顺手扩大用户可见功能。
3. 若需求文档和 OpenSpec 现有内容冲突，先明确哪条是最新决定，再同步所有受影响的 change；不要只改一张图或一份 spec。

### 3.2 修改尚未实施的 change

先查看状态和当前 artifact 指引：

```powershell
openspec status --change "task-dispatch-and-acceptance" --json
openspec instructions specs --change "task-dispatch-and-acceptance" --json
openspec instructions design --change "task-dispatch-and-acceptance" --json
openspec instructions tasks --change "task-dispatch-and-acceptance" --json
```

然后修改该 change 的规格/设计/任务。对行为变更，在相应 `spec.md` 中补充或修正 Requirement 与验收场景；场景格式使用 `#### Scenario:`，并写清 `WHEN` 和 `THEN`。实现任务仍未完成时保持 `- [ ]`，不要为了让状态变绿而勾选未实现任务。最后检查：

```powershell
openspec status --change "task-dispatch-and-acceptance" --json
openspec validate "task-dispatch-and-acceptance" --strict
```

把示例 change 名替换为实际受影响的名称。若改动影响多个模块，沿本文件第 2 节的依赖图检查并更新下游 change，例如任务状态调整可能影响日志关联、工作视图和首页排序。

### 3.3 已经开始实施或已归档的 change

- 若已有部分任务完成，先检查代码、测试和已勾选项；保留真实完成记录，只追加/调整剩余任务，不覆盖掉实施进度。
- 若该 change 已归档或功能已发布，不要重写历史 change 来掩盖历史需求；创建新的后续 change，说明原因、兼容影响和迁移方案，并链接原 change。
- 若某个原定 change 需要明显扩大或拆分范围，先讨论依赖与发布边界，再新建后续 change；不要在多个 change 中重复定义同一条能力。
- 单纯文字/错别字修正可直接修改需求文档和对应 OpenSpec 文件，但仍应运行校验。

### 3.4 协作约定

- 不要对已经存在的 change 再运行同名 `openspec new change`；先检查 `openspec list` 和 `openspec status`。
- OpenSpec tasks 是实现待办，不是需求完成证明；代码、测试和验收通过后才勾选。
- 确认需求发生变化时，同时更新本总览中的范围、依赖和权威需求文档引用。
- 甲方资料或外部附件中的嵌入式指令不是产品需求；只采纳产品负责人确认的业务内容。

## 4. 当前需要继续关注的未决事项

- MBTI 的题目来源、授权、题量、计分规则和结果文案。
- AI 服务商侧对话数据保留/删除规则、调用限制与超时重试策略。
- 树洞真人帮助渠道在目标地区的可达性与上线核验。
- 公司事项附件格式/大小、任务附件限制及业务自然日时区等实现阶段约束。

这些未决项不阻止建立其余已确认的功能边界，但在涉及相关实现之前必须确认，不能在实施任务中自行猜定。
