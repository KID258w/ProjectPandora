# OpenSpec 使用指南

本指南用于 ProjectPandora 团队。OpenSpec 让每项可交付的需求都留下从需求、设计、任务到实现、测试和归档的记录。它适合与 GitHub Issue、分支和 Pull Request 一起使用。

## 一条需求的生命周期

```text
探索需求 -> 创建变更方案 -> 评审方案 -> 按任务实现和测试 -> 同步规格 -> 归档
 explore       propose                                      apply              sync        archive
```

一次 OpenSpec change 对应一个边界清晰的功能或改动，例如 `add-task-assignment`，而不是整个项目。变更中的常见文件为：

- `proposal.md`：为什么做、做什么、不做什么。
- `specs/<capability>/spec.md`：可验收的需求和 GIVEN/WHEN/THEN 场景。
- `design.md`：架构、数据、接口和关键技术决定。
- `tasks.md`：可实施、可测试并可勾选的任务。

## 团队基本约定

1. 每个 Story 或缺陷先建 GitHub Issue，再创建PR并新建一个 OpenSpec change。
2. 每个 change 使用 kebab-case 名称，例如 `add-work-log`、`add-task-assignment`。
3. 先评审 proposal、spec、design、tasks，再开始写业务代码。
4. 每个 `tasks.md` 任务关联 Issue、分支、PR 或测试证据；完成后将 `- [ ]` 改为 `- [x]`。
5. PR 合并前至少由一名非作者成员审阅，并在 PR 中链接对应 Issue 和 change。
6. 所有任务完成、测试通过且主规格已同步后才归档。`openspec/` 必须提交到 Git。

## 统一命令速查

团队只使用一套 OpenSpec 流程；Claude Code 与 Codex 只是触发同一动作时的语法不同。命令后的 change 名称可省略，但多人并行或存在多个 change 时，应明确写出名称。

| 使用时机 | 统一动作 | Claude Code | Codex |
| --- | --- | --- | --- |
| 需求还不清楚，需要讨论，不写代码 | 探索 | `/opsx:explore [主题]` | `$openspec-explore [主题]` |
| 创建完整方案 | 提案 | `/opsx:propose [需求描述或名称]` | `$openspec-propose [需求描述或名称]` |
| 方案需要调整 | 更新 | 直接说明要更新的 change 和内容 | 直接说明要更新的 change 和内容 |
| 按 `tasks.md` 实现并勾选任务 | 实施 | `/opsx:apply [change-name]` | `$openspec-apply-change [change-name]` |
| 提前将 delta specs 合入主规格，可选 | 同步 | `/opsx:sync [change-name]` | `$openspec-sync-specs [change-name]` |
| 完成后同步规格并归档 | 归档 | `/opsx:archive [change-name]` | `$openspec-archive-change [change-name]` |

默认路径是：探索（可选）-> 提案 -> 人工评审 -> 实施 -> 归档。通常无需单独执行“同步”，因为归档时会提示并处理规格同步。

示例：无论使用哪个工具，团队要表达的都是同一件事——“为 `add-task-assignment` 创建方案”或“实施 `add-task-assignment`”。在 Claude Code 输入 `/opsx:propose 增加团队长派发任务和员工更新进度`；在 Codex 输入 `$openspec-propose 增加团队长派发任务和员工更新进度`。

如果 Codex 输入 `$` 后没有出现候选项，确认 IDE 工作区根目录为 ProjectPandora，且 `.codex/skills/` 已存在；新开 Codex 对话或重载窗口后再试。Claude Code 则应确认 `.claude/commands/opsx/` 已存在。

## CLI 检查命令

在本机 PowerShell 中，若 `openspec` 因执行策略无法运行，改用 `openspec.cmd`，不要为了这个项目修改系统执行策略。

```powershell
# 查看当前 change 和主规格
openspec.cmd list
openspec.cmd list --specs

# 查看一个 change 的工件完成度
openspec.cmd status --change add-task-assignment

# 严格校验当前 change 或全部规范
openspec.cmd validate add-task-assignment --strict
openspec.cmd validate --all --strict
```

## Pandora 示例

建议 Sprint 0 的首个 change 是 `define-pandora-mvp`，只完成范围和计划，不写全部业务代码。它应明确：

- MVP：登录与角色识别、员工日志、任务派发、接收/进度更新、团队看板和基础月周日视图。
- 非目标：DeepSeek 分析、MBTI 报告、跨公司匹配、复杂报表。
- 测试：角色越权、必填字段、非法任务状态、任务闭环的端到端场景，以及已选非功能需求的验证方法。

之后将实现拆成更小的 changes，例如 `add-auth-and-roles`、`add-work-log`、`add-task-assignment`、`add-team-dashboard`。每个 change 独立分支、PR 和测试记录，能清晰展示全体成员的真实贡献。

Git 分支、Issue 分配、PR 审阅、阶段发布和强推安全规则见[团队 Git 协作指南](../git/git-workflow.md)。

## 参考资料

- OpenSpec 官方 Quickstart：https://openspec.dev/docs/quickstart
- OpenSpec 官方命令参考：https://github.com/Fission-AI/OpenSpec/blob/main/docs/commands.md
- 团队提供的入门博客：https://zhuanlan.zhihu.com/p/1980408655154257981
