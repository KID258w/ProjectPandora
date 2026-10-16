---
name: openspec-sync-specs
description: 把某个 change 的 delta specs 合并进主规格 openspec/specs/，不归档 change。当用户希望提前同步规格，或归档前需要先合入主规格时使用。
license: MIT
compatibility: 需要 openspec CLI（npx -y @fission-ai/openspec@1.14.1）。
---

# OpenSpec：同步规格（sync）

把 `openspec/changes/<name>/specs/<capability>/spec.md` 的 delta 合入 `openspec/specs/<capability>/spec.md`。

## pi 环境适配

- 选择 change **直接问用户**（不要用 AskUserQuestion）。
- 不要为此调用 subagent：pi 的子代理需操作者显式授权，本技能自己就能完成。

## 步骤

1. **确定 change**：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 list --json
   ```

2. **读 delta**：`openspec/changes/<name>/specs/<capability>/spec.md`，逐个 capability 处理，识别四种 delta：
   - `## ADDED Requirements` → 追加到主规格的 `## Requirements`
   - `## MODIFIED Requirements` → 用 delta 内容**整体替换**主规格里同名 Requirement（含它的 Scenario）
   - `## REMOVED Requirements` → 从主规格删掉同名 Requirement
   - `## RENAMED Requirements` → 改标题，正文按需同步

3. **写主规格** `openspec/specs/<capability>/spec.md`，结构必须完整：
   ```markdown
   ## Purpose

   <该 capability 管什么、边界在哪 —— 必须 ≥50 字符，否则 strict 校验会失败>

   ## Requirements

   ### Requirement: <标题>

   #### Scenario: <场景名>
   - **WHEN** ...
   - **THEN** ...
   ```
   - 主规格文件必须同时有 `## Purpose` 和 `## Requirements` 两个 section。
   - 每条 Requirement 同样必须至少有一个 `#### Scenario:`。
   - capability 目录不存在时新建。

4. **校验**：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 validate --all --strict
   ```
   注意 `--strict` 把 WARNING 也算失败（如 Purpose 过短、Requirement 超过 500 字符）。

5. **汇报**：改了哪些主规格文件、新增/替换/删除了哪些 Requirement、校验结果。提醒用户提交 `openspec/`。

## 注意

- delta 文件**保持不动**：归档时仍需要它（除非本次就是要提前同步）。
- 同步与归档是两件事：本技能不删除 change、不移动文件。
