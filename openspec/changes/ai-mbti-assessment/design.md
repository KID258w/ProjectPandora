## Context

MBTI 是 AI 地图中的独立个人探索功能，不是工作绩效工具。产品方向和隐私边界已确定，具体问卷、授权来源、题量和评分仍待选定。

## Goals / Non-Goals

**Goals:** 说明用途边界、逐题作答/返回修改、生成并查看结果、重测替换最新结果、本人私有访问。

**Non-Goals:** 管理者/管理员查看、招聘或绩效使用、诊断结论、结果历史库、把结果自动注入 Work Assistant/Treehole 上下文。

## Decisions

- 在用户开始前展示自我探索用途及不可用于诊断/人事决策的说明；答题提交后才生成结果。
- 问题、评分规则和结果描述作为有版本的测评定义维护；在题库/计分确认前不锁定题数、选项、维度权重或第三方量表来源。
- 每用户仅保留最近一次完成结果；重测成功后替换，失败/未完成不覆盖上一次结果。
- 结果 API 仅允许本人读取；不向管理分析、组织统计、其他对话上下文或 Web 管理端暴露。
- 结果提供自我反思和沟通偏好提示，并标注为非诊断、非定论内容。

## Alternatives Considered

- Inventing a questionnaire or scoring formula during implementation was rejected; the content and scoring must be confirmed first.
- Retaining and showing all assessment history was rejected; only the latest completed result is displayed.
- Connecting results to HR or task-assignment workflows was rejected; this is for personal exploration only.

## Risks / Trade-offs

- [未授权使用量表内容] → 实施前确认题目来源及使用许可。
- [结果被误当作客观标签] → 明确解释边界，避免把结果用于人员管理接口。
- [重测中断导致旧结果丢失] → 仅在新结果完整生成后替换原结果。

## Migration Plan

新增用户最新测评结果存储和测评定义版本；无结果的用户显示开始页。停用功能不删除已保存结果，按用户删除请求处理。

## Open Questions

- 必须在实现前确定题目来源、题量、计分规则、结果文案及是否使用可合法使用的现成量表。
- 是否由固定规则生成偏好结果，或调用模型润色反思建议，需要在确认题库与结果口径后决定。
