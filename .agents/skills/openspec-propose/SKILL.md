---
name: openspec-propose
description: 为 ProjectPandora 创建一个新的 OpenSpec change 并生成全部工件（proposal / specs delta / design / tasks）。当用户描述一个需求、功能或改动并希望形成可评审的方案时使用；纯工具或文档类改动用 skip_specs。
license: MIT
compatibility: 需要 openspec CLI（本项目固定 npx -y @fission-ai/openspec@1.14.1）与 Git 仓库上下文。
---

# OpenSpec：提案（propose）

为一个边界清晰的需求创建 change，并生成可评审的工件。**先方案、后编码**是本项目的硬约定（`docs/openspec/openspec-guide.md`、`docs/git/git-workflow.md`）。

## pi 环境适配（与 Claude Code 版本的差异）

- 需要向用户确认时：**直接在回复里提问并等待回答**，不要调用 AskUserQuestion（pi 没有该工具）。
- 跟踪进度：**直接维护 change 的 `tasks.md`**，不要调用 TodoWrite。
- 需要子代理时：pi 的 subagent 必须由操作者显式授权；默认**自己按步骤执行**。

## 步骤

1. **确认需求边界**。信息不足时一次问清：要解决什么问题、明确不做什么、验收标准（尽量是 GIVEN/WHEN/THEN）。若用户已把范围写清楚，不要重复提问。

2. **查重与命名**。change 名用 kebab-case，描述一个功能边界而非整个项目：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 list --json
   ```
   已存在同类 change 时应先更新它，而不是新建。

3. **建目录** `openspec/changes/<change-name>/`：
   - `proposal.md`：Why / What Changes / Non-goals / Impact
   - `specs/<capability>/spec.md`：产品能力类改动必填，用 delta 头
   - `design.md`：有架构、数据、接口决策时写
   - `tasks.md`：可实施、可验证、可勾选的清单
   - 纯工具 / 文档 / 流水线改动（不产生可验收的产品行为）：写 `.openspec.yaml` 代替 specs

4. **specs 的硬性结构**（校验器会拦）：
   ```markdown
   ## ADDED Requirements

   ### Requirement: <一条可验收的行为>

   #### Scenario: <正常场景>
   - **WHEN** ...
   - **THEN** ...

   #### Scenario: <边界或异常场景>   ← 建议补
   ```
   - 粒度是**每条 Requirement**：每条下面必须至少有一个 `#### Scenario:`，一个文件可以有多条 Requirement。
   - delta 头只认 `## ADDED / MODIFIED / REMOVED / RENAMED Requirements`。
   - 校验器**不检查 Scenario 的内容质量**，所以正常/边界/异常场景要自己写全。

5. **skip_specs 的正确写法**（三个字段缺一不可，否则报 "marker is not honored"）：
   ```yaml
   # openspec/changes/<change-name>/.openspec.yaml
   schema: spec-driven
   skip_specs: true
   created: YYYY-MM-DD
   ```

6. **校验**：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 validate <change-name> --strict
   ```
   注意 `--strict` 会把 WARNING 也算失败（例如"需求文字超过 500 字符"），遇到先拆短或把细节移进 Scenario。

7. **汇报并给出下一步**：change 名称、生成了哪些文件、校验结果，以及"说一句 `实施 <change-name>` 或运行 `/skill:openspec-apply-change <change-name>` 即可开始"。

## 本项目约定

- 先有 GitHub Issue，再建 change；功能分支命名 `feature/<Issue编号>-<功能>`，PR 目标分支是 `dev`。
- `openspec/` 必须提交到 Git。
- 详细流程见 `docs/openspec/openspec-guide.md`。
