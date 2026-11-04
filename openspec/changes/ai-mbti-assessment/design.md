## Context

MBTI 是 AI 地图中的独立、轻量个人自我探索问答，不是工作绩效工具，也不作为正式、标准化或诊断性心理测评。产品方向和隐私边界已确定，具体题目、题量和偏好计算规则仍待选定。

## Goals / Non-Goals

**Goals:** 说明用途边界、逐题作答/返回修改、生成并查看结果、重测替换最新结果、本人私有访问。

**Non-Goals:** 管理者/管理员查看、招聘或绩效使用、诊断结论、结果历史库、把结果自动注入 Work Assistant/Treehole 上下文。

## Decisions

- 在用户开始前展示自我探索用途及不可用于诊断/人事决策的说明；答题提交后才生成结果。
- 采用轻量、自我探索定位；不宣称正式测评效度，不直接套用未经确认或未获许可的第三方量表。
- 问题、偏好类型计算规则和结果描述作为有版本的问答定义维护；在内容确认前不锁定题数、选项或各偏好维度计算细节。
- 每用户仅保留最近一次完成结果；重测成功后替换，失败/未完成不覆盖上一次结果。
- 结果 API 仅允许本人读取；不向管理分析、组织统计、其他对话上下文或 Web 管理端暴露。
- 结果提供自我反思和沟通偏好提示，并标注为非诊断、非定论内容。

## Alternatives Considered

- Presenting the lightweight self-exploration questionnaire as a formal standardized MBTI instrument was rejected; the content and scoring must be confirmed first.
- Retaining and showing all assessment history was rejected; only the latest completed result is displayed.
- Connecting results to HR or task-assignment workflows was rejected; this is for personal exploration only.

## Risks / Trade-offs

- [轻量结果被误认为正式测评结论] → 明示自我探索定位和解释边界，不宣称标准化效度。
- [结果被误当作客观标签] → 明确解释边界，避免把结果用于人员管理接口。
- [重测中断导致旧结果丢失] → 仅在新结果完整生成后替换原结果。

## Migration Plan

新增用户最新测评结果存储和测评定义版本；无结果的用户显示开始页。停用功能不删除已保存结果，按用户删除请求处理。

## Open Questions

- 必须在实现前确定轻量问答题目、题量、偏好类型计算规则和结果文案；默认不采用未经确认授权的现成量表。
- 是否由固定规则生成偏好结果，或调用模型润色反思建议，需要在确认题库与结果口径后决定。
