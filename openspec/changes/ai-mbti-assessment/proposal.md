## Why

AI 地图中的 MBTI 性格探索提供轻量自我认识入口，但测评结果容易被误用于诊断、招聘或绩效评价。需要规定个人用途、结果保存和访问边界，同时将尚未确定的题库和计分方案留作实现前决策。

## What Changes

- 新增移动端轻量 MBTI 自我探索问答、答题进度、返回修改、完成结果展示和重新测试；不将其包装为正式、标准化或诊断性测评。
- 结果仅供本人自我探索，包含偏好/沟通提示或反思内容；系统只保留本人最近一次结果。
- 禁止将结果用于诊断、招聘、绩效、任务分配或管理者查看。
- 轻量问答的题目、题量、偏好类型计算方式和结果文案在实现前确定；本 change 不预设未经确认的题库或算法。

## Capabilities

### New Capabilities
- `ai-mbti-assessment`: 面向个人自我探索的 MBTI 问答与最新结果管理。

### Modified Capabilities
- None.

## Impact

- 关联 `docs/requirements/06-ai-map-and-assistant.md`。
- 影响 Android AI 地图 MBTI 页面、答题/结果存储与本人访问控制。AI 地图入口依赖 `ai-work-assistant` 的导航基础；测评本身不要求把答案或结果用于其他 AI 对话。
