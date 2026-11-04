---
name: openspec-explore
description: 探索模式——在写方案或代码前，与用户一起厘清需求、调查现有实现、评估取舍。当用户想先讨论、需求还不明确、或要权衡技术方案时使用。
license: MIT
compatibility: 需要 openspec CLI（npx -y @fission-ai/openspec@1.14.1）。
---

# OpenSpec：探索（explore）

**只思考、只调查、不写产品代码。** 目标是产出足以支撑一个 change 的明确结论。

## pi 环境适配

- 需要澄清时**直接提问**（不要用 AskUserQuestion）。
- 不要调用未授权的 subagent；自己读代码、跑命令来完成调查。

## 原则

1. **先读再问**：能从仓库回答的问题自己查（`openspec/config.yaml` 里的产品规则、`docs/requirements/`、现有代码与 change），只把真正需要用户决策的问题抛出来。
2. **一次只推进一个关键问题**，不要一次抛出一串问题。
3. **可视化**：必要时用简单的表格/树状图说明现状、边界或流程。
4. **不写产品代码**：可以跑只读命令（`openspec list --json`、`git log`、`grep`），需要改动时先说明意图并征得同意。
5. **尊重已定事实**：`openspec/config.yaml` 中的角色权限、任务规则、日志规则、AI 边界是已确认结论；发现冲突要指出来，不要自行发明解释。

## 步骤

1. 明确本次要探索的主题（用户可能只给了一句话）。
2. 调查现状：相关 change 与主规格、相关代码、相关文档。
   ```bash
   npx -y @fission-ai/openspec@1.14.1 list --json
   npx -y @fission-ai/openspec@1.14.1 list --specs
   ```
3. 与用户逐项澄清：目标、非目标、验收标准（GIVEN/WHEN/THEN）、边界与异常。
4. 汇总结论，并给出建议：应当新建哪个 change（kebab-case 名称建议）、范围如何切分。
5. 用户确认后，转 `openspec-propose` 技能生成方案。

## 输出模板（结论段）

```markdown
## 已确认
- 目标：
- 非目标：
- 验收场景（GIVEN/WHEN/THEN）：

## 待决策
- <问题> — 选项 A / B，各自代价

## 建议
- change 名称建议：<kebab-case>
- 建议拆分为：<1..n 个 change>
```

> 完整的方法论（更长的引导话术）见 `.claude/skills/openspec-explore/SKILL.md`；与工具无关的部分可直接沿用。
