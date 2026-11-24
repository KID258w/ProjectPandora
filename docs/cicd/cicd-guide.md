# CI/CD 协作引导

## 当前状态

GitHub Actions 已配置仓库级 OpenSpec/敏感文件门禁及后端、Web、Android 的 PR 检查；当前 `dev` 尚未配置服务器自动部署。Issue 11 计划在 `main` 更新后自动部署 Web 与 Django 后端。下列部署流程是目标方案，尚未验证生效。

## 已确定技术栈

- **GitHub Actions**：自动运行代码检查、测试、镜像构建和发布等工作流。
- **Docker Buildx**：构建 Web 与 Django 后端所需的容器镜像。
- **GHCR（GitHub Container Registry）**：保存和分发项目容器镜像。
- **Docker Compose**：在目标服务器上配置并协同运行 Web 与 Django 服务。

本机 Docker 不是 Android、Web 或 Django 日常功能开发的前置条件。CI/CD 负责人可用 Docker 在本地验证镜像和 Compose；实际自动构建可由 GitHub Actions runner 执行。目标服务器需要具备运行 Docker Compose 的环境。

云服务器厂商、域名、HTTPS 入口和正式数据库方案尚未在本项目中配置；确认后应更新本指南和部署配置。

## Issue 11 自动部署目标（待实现）

- 仅当代码合入并更新 `main` 后，GitHub Actions 才启动生产部署；`dev` 更新不直接部署到生产。
- Actions 使用 Docker Buildx 构建 Web 与 Django 后端镜像，推送至 GHCR，再通过 SSH 在服务器上运行 Docker Compose 更新服务。
- 部署前备份 SQLite 数据库，再执行数据库迁移；数据库与上传文件使用持久化存储，不打入镜像。
- 部署后执行健康检查；检查失败时停止发布并尝试回滚到上一个健康版本。
- GitHub Secrets/Environment 保存镜像发布和服务器连接凭据，不把密钥写入仓库或镜像。
- 本流程负责云服务器上的 Web 与后端，不自动构建或发布 Android APK。

以上属于 Issue 11 的验收目标。只有在实际服务器完成一次 `main` 更新触发、部署、健康检查及失败处理验证后，才能将其标记为已启用。

## CI/CD 负责人待完成

1. 按 Issue 11 实现 `main` 更新触发的镜像构建、GHCR 发布和 SSH/Compose 部署。
2. 确认后端、Web 构建配置、环境变量、健康检查和服务重启方式。
3. 确保 SQLite 数据库和上传文件在容器更新后持久保留；迁移前备份 SQLite。
4. 配置 GitHub Secrets/Environment，并验证首次部署、后续更新、健康检查失败和回滚流程。
5. 完成从 PR 合入 `main` 到服务器更新的一次端到端验证；不得将真实密钥提交到仓库。

## 协作与验收

开始实现前，先通过 Issue 明确负责人、分支名、交付范围和验收条件。完成后提交 PR，至少说明：

- 哪些分支和事件会触发 CI、镜像发布或部署；
- CI 覆盖的检查及本地复现命令；
- 所需 Secrets、权限和服务器前置条件（只写名称与用途，不写密钥值）；
- 验证结果、失败处理、回滚方式及尚未覆盖的风险。

统筹人和相关模块负责人评审后，再按团队分支流程合入。部署到真实服务器前，先在测试环境验证，并确认数据库与上传文件的备份恢复办法。
