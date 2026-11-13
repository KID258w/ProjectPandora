## Context

树洞提供一般性倾听和自我梳理，不承担医疗或心理治疗角色。它是私密 AI 功能，不接触 Pandora 工作数据，也不进入管理者工作评价或分析。

## Goals / Non-Goals

**Goals:** 三种用户选择的回应风格、私有多会话、仅当前会话上下文、删除能力、AI 数据处理同意和真人帮助入口。

**Non-Goals:** 诊断/治疗、危机干预替代、管理者读取、任务/日志上下文、长期记忆、自动形成心理画像。

## Decisions

- 复用 `ai-work-assistant` 的服务端模型代理、同意流程和对话存储接口，但用独立 `treehole` 会话类型/数据访问策略；工作助手 API 不得读写树洞会话，反之亦然。
- 开始会话时由用户选择倾听、梳理或建议风格；模型输入只含用户消息、该会话历史和风格指令，不查询任务或日志。
- 将树洞聊天和回复标识为 AI 生成/一般支持，不使用临床诊断标签、不生成员工评价。
- 随时显示真人帮助入口；危机提示只做保守的安全 UI 路由，不承诺自动识别完整性或提供紧急服务。援助号码/地区渠道上线前确认可达与展示方式。
- 用户可以删除本人树洞对话；删除后不再作为 Pandora 后续上下文。Web 管理端不提供对话审计内容或查看入口。

## Alternatives Considered

- Sharing Work Assistant task/log context with Treehole was rejected; Treehole remains isolated from work data and conversations.
- Describing Treehole as diagnosis/treatment or promising complete crisis detection was rejected; MVP provides general support and a human-help route.
- Showing help resources only after risk detection was rejected; the human-help entry remains available throughout the feature.

## Risks / Trade-offs

- [高风险表达被普通建议覆盖] → 在对话界面始终保留真人求助入口；可能风险时优先呈现，而不是保证自动识别。
- [敏感内容被管理者看到] → 对话按所有者私有，拒绝管理端/组织查询；审计只记录必要系统事件，不记录消息正文。
- [模型输出不合适] → 清晰非临床声明并提供结束/删除能力；发布前评测常见安全边界。

## Migration Plan

复用 AI 会话基础新增 treehole 类型和独立权限策略。分阶段开放；如安全审核或真人帮助入口未准备妥当，则暂不启用该卡片，已有对话数据保留并允许用户删除。

## Open Questions

- 目标用户所在地区适用的专业帮助渠道、紧急联系文案及上线核验责任人需确认。
- 危机内容提示采用简单规则还是模型辅助需评估；MVP 不承诺检测完整性。
