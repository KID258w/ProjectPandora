# CI/CD 协作引导

> 本文说明 CI/CD 的整体方案与协作约定。**服务器从零部署、从手动部署迁移、日常更新、回滚、备份和排障的实操步骤见 [server-deploy.md](server-deploy.md)。**
> CI 门禁（PR 检查什么、ruleset 怎么配）见 [ci-gates.md](ci-gates.md) 与 [pr-check-workflow.md](pr-check-workflow.md)。

## 当前状态

CI 与自动部署配置已落地，合并到 `main` 即自动发布到服务器：

| 环节 | 状态 | 位置 |
| --- | --- | --- |
| PR 检查（规格 / 敏感文件 / 后端 / Web / Android） | ✅ 已启用 | `.github/workflows/ci*.yml` |
| 镜像构建与推送 GHCR | ✅ 已配置 | `.github/workflows/cd-deploy.yml` → `build` job |
| 服务器自动部署 | ✅ 已配置 | `cd-deploy.yml` → `deploy` job + `deploy/remote-deploy.sh` |
| 部署健康检查与失败自动回滚 | ✅ 已配置 | `deploy/remote-deploy.sh` / `deploy/remote-rollback.sh` |
| 数据库自动备份（部署前，保留 7 份） | ✅ 已配置 | `backend/docker-entrypoint.sh` |
| `main` 更新触发的端到端部署验证 | ⬜ 待执行 | 见 Issue 11「首次自动部署验证」 |
| Android 签名发布 | ⬜ 未启用（模板在 `templates/`） | `templates/cd-android-release.yml` |

**上线前仍需人做的两件事**：在 GitHub 配置 Secrets/Variables（见 server-deploy.md 2.3），
在服务器 `/opt/pandora/.env` 填正式密钥。配置完成前，工作流会因缺少凭据失败。

## 已确定技术栈

- **GitHub Actions**：代码检查、测试、镜像构建与发布、SSH 部署。
- **Docker Buildx**：构建 Web（Vite + Nginx）与后端（Django + Gunicorn）镜像，带 GHA 层缓存。
- **GHCR（GitHub Container Registry）**：保存和分发镜像，标签为 `:<git sha 前 12 位>` 与 `:latest`。
- **Docker Compose**：服务器上用两个容器协同运行 Web 与后端；数据用 bind mount 挂宿主机目录。

本机 Docker 不是 Android、Web 或 Django 日常功能开发的前置条件；只有调镜像或 Compose 时才需要。

## 部署架构（本项目选型）

本项目**保留宿主 Nginx 终止 HTTPS**，Docker 只跑在后头，避免迁移证书与改动 DNS：

```
main push ──► Actions: build(push ghcr) ──► Actions: deploy(ssh)
                                             │
                        ┌────────────────────┴────────────────────┐
                        ▼                                         ▼
                 /opt/pandora/docker-compose.yml           .env（仅服务器）
                 web 容器  127.0.0.1:8080 ──► /api /admin 反代 backend:8000
                        └─ /static、/media 只读挂载
                 backend 容器   bind mount /var/lib/pandora → /data
                                （db.sqlite3、media、backups）
                                命名卷 pandora-static → /srv/static
   ▲
宿主 Nginx 80/443（Let's Encrypt）──proxy_pass──► 127.0.0.1:8080
```

两个刻意的选型，改动前请先看 [server-deploy.md](server-deploy.md) 第 1 节的理由：

1. **web 容器只绑 `127.0.0.1:${WEB_PORT}`，不绑 80**：宿主 Nginx 已占用 80/443，且证书在宿主上。
   若改成直接绑 80，`compose up` 会因端口冲突失败。
2. **数据用 bind mount 而非命名卷**：现有生产数据在 `/var/lib/pandora`。用命名卷会让容器看到空目录，
   表现为"数据库丢了"。

## 触发规则

| 事件 | 是否部署 | 说明 |
| --- | --- | --- |
| push 到 `main` | ✅ 部署 | `cd-deploy.yml` 不打 `paths` 过滤，`main` 任何提交都部署该提交 |
| push 到 `dev` | ❌ 不部署 | 只跑 PR 检查类工作流 |
| 普通 PR | ❌ 不部署 | 只跑 CI |
| PR 合入 `dev` | ❌ 不部署 | 生产只由 `main` 触发 |
| Actions 手动 Run workflow | ✅ 部署 | 可指定任意分支/ref，用于补发或回滚到历史提交 |

`concurrency: cd-deploy` + `cancel-in-progress: false` 保证部署串行，避免两个 run 同时
`compose up` 互相覆盖。

## 协作与验收

开始改动前先通过 Issue 明确负责人、分支名、交付范围和验收条件。完成后提交 PR，至少说明：

- 哪些分支和事件会触发 CI、镜像发布或部署；
- CI 覆盖的检查及本地复现命令；
- 所需 Secrets、权限和服务器前置条件（只写名称与用途，不写密钥值）；
- 验证结果、失败处理、回滚方式及尚未覆盖的风险。

验证自动部署时按 Issue 11「首次自动部署验证」的 6 项逐条记录，并附 Actions run 链接、
部署摘要截图与备份文件位置。**未完成端到端验证前，不得把自动部署标记为"已验收"。**

统筹人和相关模块负责人评审后，再按团队分支流程合入。部署到真实服务器前，先在测试环境验证，
并确认数据库与上传文件的备份恢复办法。
