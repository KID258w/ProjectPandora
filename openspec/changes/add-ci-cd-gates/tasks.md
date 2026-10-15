# Tasks

## 1. 基础设施与触发规则

- [x] 确定触发规则：PR 进 dev/main、推送 dev/main 时触发；模块检查按 paths 过滤
- [x] 确定必需检查清单：规格校验、敏感文件扫描、Workflow 语法检查（模块检查不设为必需）

## 2. 通用检查工作流（ci.yml）

- [x] 规格校验：`openspec validate --all` 阻断 + strict 欠债报告 + change 任务进度摘要
- [x] 敏感文件扫描：按文件名拦截 keystore、`.env` 全变体、`local.properties`、`google-services.json` 等
- [x] Workflow 语法检查：actionlint（镜像固定 1.7.12，内含 shellcheck）
- [x] 反向验证：造出只有 proposal 的 change 与 `local.properties`，确认被拦截

## 3. 模块检查工作流

- [x] `ci-android.yml`：Lint + 单元测试 + 构建 Debug APK，上传报告与产物
- [x] `ci-backend.yml`：`manage.py check`、`makemigrations --check`、`migrate`、`manage.py test`（Python 3.11）
- [x] `ci-web.yml`：`npm ci` + lint/typecheck/test（脚本存在时）+ `build`

## 4. 协作模板与仓库卫生

- [x] PR 模板（分支命名、本地校验、任务勾选、密钥、不直推 dev/main）
- [x] Issue 表单（用户故事 / 缺陷）
- [x] CODEOWNERS（并记录"唯一 owner 即作者"的风险）
- [x] Dependabot：github-actions、npm(/web)、pip(/backend)、gradle(/android)
- [x] 根 `.gitignore`（密钥、IDE、构建产物；保留 gradle-wrapper.jar）

## 5. 文档

- [x] `docs/cicd/ci-gates.md`：门禁边界、ruleset 配置、本地命令、欠债与验收清单

## 6. 需要人工完成的仓库设置

- [ ] `Settings → Rules`：为 `dev` 与 `main` 各建一条 ruleset，补齐"必需 PR 批准 + 必需状态检查 + 禁止强推"
- [ ] `Settings → Code security`：开启 Secret scanning + Push protection
- [ ] `Settings → Collaborators`：补齐成员 write 权限（CODEOWNERS 生效前提）
- [ ] 验证：开测试 PR 确认 Merge 按钮被拦截；本地 `git push origin dev` 被拒绝

## 7. 收尾（可另开 chore PR）

- [ ] `git update-index --chmod=+x android/gradlew` 根治可执行位问题
- [ ] `git rm -r --cached demo/.idea` 停止跟踪 IDE 配置
- [ ] 存量 10 条「Requirement text is very long (>500 characters)」拆分后启用 `--strict`
