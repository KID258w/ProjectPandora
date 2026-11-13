---
name: openspec-apply-change
description: 按 OpenSpec change 的 tasks.md 逐条实施并勾选任务，含验证与进度汇报。当用户要开始实施、继续实施或推进某个 change 的任务时使用。
license: MIT
compatibility: 需要 openspec CLI（npx -y @fission-ai/openspec@1.14.1）。
---

# OpenSpec：实施（apply）

按 `tasks.md` 做实现，并在每一步留下可验证的证据。

## pi 环境适配

- 选择 change 时**直接问用户**，不要用 AskUserQuestion；用户已指定名称就直接用。
- 开始前明确宣告：`使用 change: <name>`，并说明如何切换（`/skill:openspec-apply-change <other>`）。
- 不要调用 TodoWrite 或未授权的 subagent：进度就记录在 `tasks.md` 里。

## 步骤

1. **确定 change**：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 list --json          # 列出所有 change 与任务进度
   npx -y @fission-ai/openspec@1.14.1 status --change <name>
   ```

2. **读全上下文再动手**，顺序：`proposal.md` → `design.md` → `specs/*/spec.md` → `tasks.md`。同时读相关代码，确认现状与设计一致；有冲突时先问，不要擅自改设计。

3. **逐条实施**：一次专注一条任务。
   - 只改实现所需的文件，不做顺手重构，不动与本次无关的代码。
   - 用户的未提交改动**不要回退**。
   - 不引入未在 `requirements.txt` / `package.json` / `libs.versions.toml` 中声明的依赖，除非任务要求且用户同意。

4. **每完成一条就验证并勾选**。把 `tasks.md` 里的 `- [ ]` 改成 `- [x]`。各模块的验证命令（与 CI 一致）：
   ```bash
   # 后端
   cd backend && python manage.py check && python manage.py makemigrations --check --dry-run && python manage.py test
   # Web
   cd web && npm ci && npm run build
   # Android
   cd android && ./gradlew lintDebug testDebugUnitTest assembleDebug
   # 通用
   npx -y @fission-ai/openspec@1.14.1 validate --all
   ```
   **不要只依赖"看起来对了"**：真实跑一次命令，把关键输出贴出来。

5. **结束前汇报**：
   ```bash
   npx -y @fission-ai/openspec@1.14.1 status --change <name>
   npx -y @fission-ai/openspec@1.14.1 validate <name> --strict
   ```
   汇报格式：已勾选的任务、每条任务对应的证据（命令 + 结果）、仍阻塞的任务及原因、变更的文件清单。

6. **收尾提示**：任务全部完成后，建议同步规格（`/skill:openspec-sync-specs <name>`）或直接归档（`/skill:openspec-archive-change <name>`）。

## 注意

- 若中途发现设计需要调整，先更新 `design.md` / `proposal.md` 并告知用户，再继续实现。
- 提交粒度：每个可验证的完整任务一个提交；提交信息用 `type(scope): 描述`（如 `feat(task): add team task assignment`）。
