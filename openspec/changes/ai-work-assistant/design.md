## Context

Pandora 负责 AI 场景入口、显式数据选择、对话管理和权限边界；模型只生成文本。工作助手不拥有业务数据写权限，模型密钥不得放在客户端。

## Goals / Non-Goals

**Goals:** 工作场景引导、多个独立会话、显式上下文授权、服务端模型代理、可编辑草稿/建议和失败处理。

**Non-Goals:** Agent 工具调用、自主多步操作、自动更改任务/日志、长期跨会话记忆、下属数据检索或 Web AI 管理。

## Decisions

- 建立工作助手对话与消息记录；每个会话有固定功能类型，模型上下文只含该会话历史及本次由本人明确选择的任务/已提交日志字段。
- 新会话不继承其他会话、MBTI 结果或树洞内容；用户可列出、继续和删除自己的对话。删除后不再出现在列表或 Pandora 后续上下文。
- 首次模型请求前展示数据处理说明并等待确认；未确认不发送请求。每次上下文附加均在请求前由用户主动选择，并展示选中对象。
- 将任务咨询、日志草稿整理和工作总结作为提示模板/场景而非自主 Agent；工作总结默认本周，但任务/日志仍由用户选择。
- AI 日志草稿只由用户手动带入日志编辑器，检查修改后自行保存/提交；AI 总结与建议仅作文本显示。
- 后端持有 DeepSeek 凭证，实施超时/错误处理，不伪造回复、不重复落库失败消息；响应标记为 AI 生成。
- 对话数据仅本人可访问；业务管理者和管理员 API 不提供读取入口。

## Alternatives Considered

- Calling DeepSeek directly from the mobile client was rejected because it exposes credentials and weakens context controls.
- An autonomous Agent with task-writing tools was rejected; the MVP is advisory and cannot mutate business records.
- Cross-conversation long-term memory was rejected; each conversation uses only its own history.

## Risks / Trade-offs

- [选择内容可能超出必要范围] → 选择器只显示本人授权数据，提交前展示上下文清单并设置服务端字段白名单。
- [模型可能产生虚构任务事实] → 提示说明来源，结果标记 AI 生成并由用户核对。
- [服务商数据留存不可控] → 上线前确认服务端数据处理条款和保留策略，不承诺平台侧删除模型商数据。

## Migration Plan

新增 AI 对话/消息和用户确认记录；接入服务端模型代理后先对少量业务账号启用。关闭 AI 入口时保留用户记录，提供删除能力。

## Open Questions

- DeepSeek 服务端套餐、超时/限流/重试和服务商侧数据保留策略需在实现前确认。
- 用户确认是账户级一次确认还是按功能分别确认，需结合最终隐私文案落实；任何情况下未确认不得发送。
