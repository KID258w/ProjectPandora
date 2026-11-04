---
name: openspec-archive-change
description: 在实现完成后归档一个 OpenSpec change（必要时先同步规格），并确认 openspec/ 已提交。当用户说某个 change 做完了、要收尾或要归档时使用。
license: MIT
compatibility: 需要 openspec CLI（npx -y @fission-ai/openspec@1.14.1）。
---

# OpenSpec：归档（archive）

把已完成的 change 从 `openspec/changes/` 归档到 `openspec/changes/archive/`。

## pi 环境适配

- 选择 change、确认操作**直接问用户**（不要用 AskUserQuestion）。
- **不要为同步规格调用 subagent**（pi 的子代理需操作者显式授权）：按 `openspec-sync-specs` 技能的步骤自己完成。

## 归档前置条件（逐条确认，不满足就停下来告知用户）

1. `tasks.md` 中**所有**任务已勾选 `- [x]`：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 status --change <name>
   npx -y @fission-ai/openspec@1.14.1 list --json        # 看 completedTasks / totalTasks
   ```
2. 规格校验通过：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 validate --all
   ```
3. 相关测试与构建已实际通过（后端 `manage.py test`、Web `npm run build`、Android `gradlew testDebugUnitTest assembleDebug` 中适用的部分）。
4. 尚未同步的 delta specs 已确认是否需要合入主规格。

## 步骤

1. **确定并复述**：change 名称、任务完成度、校验结果，然后询问是否继续。

2. **询问是否先同步规格**（如果该 change 有 `specs/` 目录）：
   - 选"先同步"：按 `openspec-sync-specs` 技能把 delta 合入 `openspec/specs/`，再继续归档。
   - 选"直接归档"：跳过同步（归档流程自身也会处理规格）。

3. **执行归档**：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 archive <name>
   ```

4. **确认结果并提醒收尾**：
   - `openspec/changes/archive/` 下出现该 change；
   - `openspec/` 的改动已提交到 Git（本项目要求 `openspec/` 入库）；
   - 关联的 GitHub Issue 可以关闭，PR 描述中补上"已完成并归档 <change-name>"；
   - 若该 change 属于阶段发布范围，提示走 `dev → main` 的发布 PR。

## 注意

- **归档不等于发布**：代码合并、Issue 关闭、发布 PR 是独立动作。
- 归档后的 change 不应再修改；需要新改动就新建 change。
