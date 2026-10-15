# add-ci-cd-gates

## Why

仓库已有 `android/`、`backend/`、`web/` 三份模块骨架，但没有一条自动化检查：改动的正确性无法在合并前被验证，密钥文件（keystore、`.env`、`local.properties`）被提交时也没有人会立刻发现。团队约定要求"每个 PR 说明关联的 Issue、OpenSpec change 与测试结果"，但目前没有任何机制保证这些约定被执行。

本 change 属于**工程基础设施改动**，不改变任何产品行为，因此在 `.openspec.yaml` 中声明 `skip_specs: true`（不产生 spec delta）。

## What Changes

### 1. 通用门禁（`ci.yml`，PR 进 dev/main 与推送 dev/main 触发）

- 规格校验（OpenSpec）：`validate --all` 阻断结构性错误，并输出 strict 欠债报告与各 change 任务进度
- 敏感文件扫描：按文件名拦截 keystore、`.env` 全变体、`local.properties`、`google-services.json`、`id_rsa*` 等
- Workflow 语法检查：actionlint（镜像固定 `1.7.12`，内含 shellcheck）

### 2. 本次交付：目标分支为 `dev` 的 PR 自动运行模块检查

| 工作流 | Job 名（PR 页面显示） | 触发条件 | 检查内容 |
| --- | --- | --- | --- |
| `ci-backend.yml` | `Django 检查与测试` | PR → `dev`，且 `backend/**` 变化 | `manage.py check`、`makemigrations --check`、`migrate`、`manage.py test`（Python 3.11） |
| `ci-web.yml` | `Web 依赖安装与构建` | PR → `dev`，且 `web/**` 变化 | `npm ci`、`npm run build`（lint/typecheck/test 脚本存在时一并执行） |
| `ci-android.yml` | `Android 测试与 Debug 编译` | PR → `dev`，且 `android/**` 变化 | `lintDebug`、`testDebugUnitTest`、`assembleDebug`，上传报告与 Debug APK |

- 三个工作流都把**本地复现命令**写入 Checks Summary，使"运行结果"与"复现方式"都能在 PR 页面直接看到
- 模块检查不在 `push` 时重复运行：PR 阶段已验证，合并后无需再跑一遍昂贵的 Gradle 构建

### 3. 协作模板与仓库卫生

- PR 模板、Issue 表单（用户故事 / 缺陷）、CODEOWNERS
- Dependabot：`github-actions`、`npm(/web)`、`pip(/backend)`、`gradle(/android)`
- 根 `.gitignore`：密钥、IDE 配置、构建产物（保留 gradle-wrapper.jar）

### 4. 文档

- `docs/cicd/ci-gates.md`：门禁清单、ruleset 配置步骤、本地命令、欠债与验收清单
- `docs/cicd/pr-check-workflow.md`：**本次交付** —— 触发条件、检查内容、PR 页面查看方式、失败排查方式、本地复现命令

## Non-goals

- **本次不配置 Docker、镜像构建/发布或服务器部署**（属于 `docs/cicd/cicd-guide.md` 的待办 3–6，另行安排）
- 不引入 MySQL service（后端当前使用 SQLite，切换时再补）
- 不启用 openspec `--strict` 阻断（存量 WARNING 先以欠债报告形式跟踪）
- 不配置 Android 签名发布与 instrumentation/UI 测试
- 不改动任何业务代码

## Impact

- 受影响模块：仓库级 CI/CD 与协作流程，不涉及业务代码逻辑
- 需要的人工配置：`Settings → Rules` 为 `dev`/`main` 各建一条 ruleset（必需批准 + 必需状态检查）；`Settings → Code security` 开启 Secret scanning；补齐协作者 write 权限
