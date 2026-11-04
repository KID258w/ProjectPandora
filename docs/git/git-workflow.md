# Git 协作与分支工作流

本文规定 Project Pandora 团队在 GitHub 上的 Issue、分支、提交、Pull Request（PR）、测试和发布流程。目标是让每项工作可分配、可审阅、可追溯，并避免误覆盖团队提交。

填写 Issue 和 PR 的具体格式见[Issue 模板说明](issue-template-guide.md)与[PR 模板说明](pr-template-guide.md)。

## 1. 分支约定

| 分支 | 用途 | 约定 |
| --- | --- | --- |
| `main` | 稳定、可演示的阶段版本 | 不直接开发；只接收通过测试的 `dev` 发布 PR |
| `dev` | 当前阶段的集成与测试线 | 不直接开发；功能分支通过 PR 合入 |
| `feature/<Issue编号>-<功能>` | 单个成员或一组明确任务的开发分支 | 从最新远程 `dev` 创建，完成后通过 PR 合入 `dev` |

每个开发分支名称必须唯一、可读，并与 Issue 对应。例如 Issue `#123` 的接口和页面由不同成员完成时，可以分别使用 `feature/123-task-api` 与 `feature/123-task-ui`。不要让两位成员同时在同一条个人功能分支上直接提交。

## 2. 开发从 Issue 开始

正式开发前，由组长创建或确认 GitHub Issue，并在 Issue 中写明：

- 需求范围、验收条件和不包含的内容；
- 关联的 OpenSpec change（如适用）；
- 成员、各自负责的任务和建议分支名称；
- 自测或集成测试要求。

多人协作时，可在 Issue 中用表格分配任务：

| 成员 | 负责内容 | 分支 |
| --- | --- | --- |
| 成员 A | 后端接口 | `feature/123-task-api` |
| 成员 B | Android 页面 | `feature/123-task-ui` |

一个 Issue 可以对应多个功能分支和 PR。功能 PR 说明关联 Issue 和 OpenSpec change；只有相关工作进入阶段发布时，才由 `dev → main` 的发布 PR 关闭对应 Issue。

## 3. 成员开发与提 PR

### 3.1 同步远程 `dev`

每次开始新任务前，先确认工作区干净，再同步远程 `dev`。本地尚无 `dev` 时：

```bash
git fetch origin
git switch --track -c dev origin/dev
```

本地已有 `dev` 时：

```bash
git switch dev
git fetch origin
git pull --ff-only origin dev
```

`--ff-only` 可避免在本地 `dev` 已分叉时意外生成合并提交。如果命令因分叉而停止，先检查状态和提交图，再决定如何整合；不要为了消除报错直接强推。

### 3.2 从 `dev` 创建个人功能分支

```bash
git switch -c feature/123-task-api
```

分支名应与 Issue 中安排一致。开发期间在该分支提交和测试，不直接向 `main` 或 `dev` 提交。若 `dev` 已有其他成员合入的新提交，先获取并整合最新 `dev`，解决冲突、重新测试后再提 PR：

```bash
git fetch origin
git merge origin/dev
```

### 3.3 推送并创建 PR

```bash
git push -u origin feature/123-task-api
```

在 GitHub 创建 PR，目标分支选 `dev`。PR 应说明改动内容、测试结果、关联 Issue 和 OpenSpec change，并标记尚未完成的事项。

PR 作者先完成自查和自测，再请求至少一位**非作者成员**审阅。作者不能批准自己的 PR；审阅者确认实现符合需求、测试通过后，由有权限的成员合入 `dev`。功能 PR 不应提前关闭仍有其他关联工作或尚未发布的 Issue。

PR 合入后，成员更新本地 `dev`，并删除已合并的个人分支（GitHub 可设置自动删除远程分支）：

```bash
git switch dev
git pull --ff-only origin dev
git branch -d feature/123-task-api
```

## 4. `dev` 集成测试与发布到 `main`

功能分支合入 `dev` 后，由团队在 `dev` 上进行集成测试和阶段验收。发现问题时，创建修复 Issue，并从最新远程 `dev` 创建新的修复分支，通过 PR 修复；不要绕过 PR 直接修改 `dev`。

确认本阶段测试通过、没有阻塞缺陷后，创建 `dev → main` 发布 PR。PR 应列出阶段内容、测试结论及未解决问题，并由非作者成员审阅。通过后合入 `main`，使 `main` 成为稳定、可演示版本。

为了让发布后 `dev` 能以快进方式与 `main` 指向同一提交，仓库的 `dev → main` PR 建议使用 **Create a merge commit（创建合并提交）**，不要对该发布 PR 使用 Squash 或 Rebase 合并。发布完成后，将 `dev` 快进到最新 `main`：

```bash
git switch dev
git fetch origin
git merge --ff-only origin/main
git push origin dev
```

如果分支保护规则禁止直接推送这次快进同步，由组长按仓库规则通过 PR 完成同步。确认 `dev` 与 `main` 已同步后，再进入下一阶段；下一阶段开发仍从最新远程 `dev` 开始。

## 5. 分支分叉、推送拒绝与强推

### 普通推送被拒绝时

先停止，不要反复推送，也不要立即使用强推。检查本地和远程各自包含哪些提交：

```bash
git fetch origin
git status --short --branch
git log --oneline --graph --decorate --left-right HEAD...origin/<分支名>
```

确认远程独有提交是否需要保留，再通过合并或其他团队同意的方式整合。若不确定，以保留两边提交为默认选择，并请组长协助。

### 强推规则

强推会改写远程分支历史，只能用于团队已明确决定丢弃目标远程分支当前历史的情况。执行前必须：

1. 重新 `fetch`，确认要覆盖的远程分支及其最新提交；
2. 检查远程独有提交，并确认已在其他分支保留或明确不再需要；
3. 确认目标分支和新提交，获得组长/团队明确同意；
4. 只对指定分支使用 `--force-with-lease`，绝不模糊指定目标。

例如，经确认只需用本地 `dev` 覆盖远程 `dev`：

```bash
git fetch origin dev
git log --oneline --graph --decorate --left-right dev...origin/dev
# 确认远程独有提交已妥善保留，且团队同意覆盖后再执行
git push --force-with-lease origin dev:dev
```

不要对 `main` 强推；不要使用不带保护的 `git push --force`。强推后立即重新 fetch 并核对本地分支与远程目标是否指向预期提交。

## 6. 日常检查清单

- 开发前：Issue 已分配，工作区干净，个人分支从最新远程 `dev` 创建。
- 提 PR 前：已同步必要的 `dev` 更新，冲突已解决，相关测试已运行。
- 合入前：目标是 `dev`，Issue/change 已关联，至少一位非作者成员已审阅。
- 发布前：`dev` 集成测试通过，发布 PR 目标是 `main`，发布结论和遗留问题已说明。
- 发布后：`dev` 与 `main` 已同步，下一阶段从更新后的 `dev` 开始。
