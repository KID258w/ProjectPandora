## Why

“AI 地图”中的工作助手需要将大模型能力约束在明确工作场景内：帮助用户理解本人任务、整理日志草稿和总结近期工作，而不是直接调用一个能读取全部业务数据的通用 API。

## What Changes

- 新增移动端 AI 地图入口与工作助手场景：任务咨询、日志草稿整理、近期工作总结（默认本周）。
- 支持多个相互隔离的工作助手对话；记录仅在当前对话内作为上下文，用户可继续或删除。
- 调用模型前取得用户对数据处理说明的确认；任务/日志必须由用户主动选择且仅发送完成当前请求所需内容。
- 通过服务端调用 DeepSeek API；输出仅为建议/草稿，不自动写入任务、进度、验收或日志。
- 不做自主执行多步操作的 Agent、跨对话长期记忆或管理者 AI 分析。

## Capabilities

### New Capabilities
- `ai-work-assistant`: 工作助手场景、独立对话历史、上下文授权与模型调用边界。

### Modified Capabilities
- None.

## Impact

- 关联 `docs/requirements/06-ai-map-and-assistant.md` 及任务、日志数据权限。
- 影响 Android AI 地图/对话 UI、Django AI 会话与代理 API、DeepSeek 服务端凭证及对话存储。依赖账号认证、任务和日志读取权限；需确认服务商数据保留规则。
