# Tasks

## 1. 通用门禁（ci.yml）

- [x] 规格校验：`openspec validate --all` 阻断 + strict 欠债报告 + change 任务进度摘要
- [x] 敏感文件扫描：按文件名拦截 keystore、`.env` 全变体、`local.properties`、`google-services.json` 等
- [x] Workflow 语法检查：actionlint（镜像固定 1.7.12，内含 shellcheck）
- [x] 反向验证：造出只有 proposal 的 change 与 `local.properties`，确认被拦截

## 2. 本次交付：目标分支为 dev 的 PR 模块检查

- [x] `ci-backend.yml`：`Django 检查与测试`，触发条件 = PR → dev 且 `backend/**` 变化
- [x] `ci-web.yml`：`Web 依赖安装与构建`，触发条件 = PR → dev 且 `web/**` 变化
- [x] `ci-android.yml`：`Android 测试与 Debug 编译`，触发条件 = PR → dev 且 `android/**` 变化
- [x] 三个工作流把「本地复现命令」写入 Checks Summary，使结果与复现方式都能在 PR 页面看到
- [x] 上传产物：Android 的测试/Lint 报告与 Debug APK、Web 的 `dist`
- [x] 新增 `docs/cicd/pr-check-workflow.md`：触发条件、检查内容、失败排查方式、本地复现命令
- [x] 本地验证：YAML 解析、actionlint（含 shellcheck）、`openspec validate`
- [x] 开 PR 到 dev，确认三项检查出现在 PR 页面且结果可查看（PR #7：四个 check 均 success）

## 3. 协作模板与仓库卫生

- [x] PR 模板（分支命名、本地校验、任务勾选、密钥、不直推 dev/main）
- [x] Issue 表单（用户故事 / 缺陷）
- [x] CODEOWNERS（并记录"唯一 owner 即作者"的风险）
- [x] 根 `.gitignore`（密钥、IDE、构建产物；保留 gradle-wrapper.jar）

## 4. 通用门禁文档

- [x] `docs/cicd/ci-gates.md`：门禁边界、ruleset 配置、本地命令、欠债与验收清单

## 5. 需要人工完成的仓库设置（本次交付之外）

- [ ] `Settings → Rules`：为 `dev` 与 `main` 各建一条 ruleset，补齐"必需 PR 批准（1）+ 必需状态检查 + 禁止强推"
- [ ] `Settings → Code security`：开启 Secret scanning + Push protection
- [ ] `Settings → Collaborators`：补齐成员 write 权限（CODEOWNERS 生效前提）
- [ ] 验证：开测试 PR 确认 Merge 按钮被拦截；本地 `git push origin dev` 被拒绝

## 6. 收尾（可另开 chore PR）

- [ ] `git update-index --chmod=+x android/gradlew` 根治可执行位问题
- [ ] `git rm -r --cached demo/.idea` 停止跟踪 IDE 配置
- [ ] 存量 10 条「Requirement text is very long (>500 characters)」拆分后启用 `--strict`
- [ ] `web/package.json` 补 lint / test 脚本（补齐后 `ci-web.yml` 会自动执行）

## 7. Issue 11 后续：`main` 自动部署

- [x] 配置仅由 `main` 更新触发的生产部署工作流；`dev` 更新不触发服务器部署。
      实现：`.github/workflows/cd-deploy.yml`（`on.push.branches: [main]`，不设 `paths` 过滤，`main` 任意提交都部署）
- [x] 使用 Docker Buildx 构建 Web 与 Django 后端镜像并推送 GHCR。
      实现：`cd-deploy.yml` 的 `build` job；镜像 `ghcr.io/<repo>/backend:<sha12>` 与 `:latest`
- [x] 配置 SSH 与 Docker Compose 更新服务器服务，敏感凭据只存储在 GitHub Secrets/Environment。
      实现：`deploy` job（`environment: production`）+ `deploy/remote-deploy.sh`；凭据只走 Secrets
- [x] 在迁移前备份 SQLite；将数据库及上传文件配置为持久化存储，并确认部署镜像不包含运行数据。
      实现：`remote-deploy.sh` 部署前快照（`db-predeploy-*.sqlite3`）+ 容器 entrypoint 迁移前备份；
      数据 bind mount `${PANDORA_DATA_DIR:-/var/lib/pandora}`；`backend/.dockerignore` 排除 `*.sqlite3`/`media/`
- [x] 增加部署后健康检查与失败停止/回滚步骤，记录上一个可用镜像版本。
      实现：`remote-deploy.sh` 健康探活（最多 30 次）+ 失败调用 `remote-rollback.sh`；`.last-good-tag` 记录可用版本
- [ ] 验证从 `main` 更新到部署成功的完整流程，并演练健康检查失败时的回滚与数据恢复办法；Android APK 发布不在本工作流范围内。
      验证清单与判据见 `docs/cicd/server-deploy.md` 第 4 节（6 项）。**未完成端到端验证前不得勾选。**

## 8. 部署选型与依赖（本项目实际情况）

- [x] 宿主 Nginx 保留 80/443 并终止 HTTPS，web 容器仅绑 `127.0.0.1:${WEB_PORT:-8080}`
- [x] 数据用 bind mount 挂宿主机 `/var/lib/pandora`，不用命名卷（避免容器看到空库）
- [x] 容器内 Nginx 透传上游 `X-Forwarded-Proto`（否则两层反代下 Admin CSRF 403）
- [ ] 在服务器配置 GitHub Secrets/Variables 并完成首次部署（需仓库管理员与服务器权限）
