# CI/CD 协作引导

> 本文说明 CI/CD 的整体方案与协作约定。**服务器从零部署、日常更新、回滚、备份和排障的实操步骤见 [server-deploy.md](server-deploy.md)。**
> CI 门禁（PR 检查什么、ruleset 怎么配）见 [ci-gates.md](ci-gates.md) 与 [pr-check-workflow.md](pr-check-workflow.md)。

## 当前状态

CI 与自动部署已落地，合并到 `main` 即自动发布到服务器：

| 环节 | 状态 | 位置 |
| --- | --- | --- |
| PR 检查（规格 / 敏感文件 / 后端 / Web / Android） | ✅ 已启用 | `.github/workflows/ci*.yml` |
| 镜像构建与推送 GHCR | ✅ 已配置 | `.github/workflows/cd-deploy.yml` → `build` job |
| 服务器自动部署 | ✅ 已配置 | `cd-deploy.yml` → `deploy` job + `deploy/remote-deploy.sh` |
| 部署健康检查与失败自动回滚 | ✅ 已配置 | `deploy/remote-deploy.sh` / `deploy/remote-rollback.sh` |
| 数据库自动备份（部署前，保留 7 份） | ✅ 已配置 | `backend/docker-entrypoint.sh` |
| HTTPS / 域名 | ⬜ 待运维确认 | 见 [server-deploy.md](server-deploy.md#7-https-与域名可选) |
| Android 签名发布 | ⬜ 未启用（模板在 `templates/`） | `pandora-cicd/templates/cd-android-release.yml` |

**上线前仍需人做的两件事**：在 GitHub 配置 Secrets/Variables（见下文），在服务器 `/opt/pandora/.env` 填正式密钥。

## 已确定技术栈

- **GitHub Actions**：代码检查、测试、镜像构建与发布、SSH 部署。
- **Docker Buildx**：构建 Web（Vite + Nginx）与后端（Django + Gunicorn）镜像，带 GHA 层缓存。
- **GHCR（GitHub Container Registry）**：保存和分发镜像，标签为 `:<git sha 前 12 位>` 与 `:latest`。
- **Docker Compose**：服务器上用两个容器协同运行 Web 与后端，数据与静态文件走 Docker 卷。

本机 Docker 不是 Android、Web 或 Django 日常开发的前置条件；只有调镜像或 Compose 时才需要。

## 部署架构

```
main push ──► Actions: build(push ghcr) ──► Actions: deploy(ssh)
                                             │
                     ┌───────────────────────┴──────────────────────┐
                     ▼                                              ▼
              /opt/pandora/docker-compose.yml              .env（仅服务器）
              web 容器  :80  ── Nginx ──► /api /admin 反代 backend:8000
                     └─ /static /media 只读挂载卷
              backend 容器     卷 pandora-data(SQLite/media/backups)
                               卷 pandora-static(collectstatic)
```

- 前端与后端**同域**：浏览器请求 `/api/v1/...`，Nginx 反代；无需跨域配置，也无需把后端端口暴露到公网。
- 后端容器启动时依次执行：备份 SQLite → `migrate` → `collectstatic` → 启动 Gunicorn。
- 部署脚本轮询 `/api/health/`，30 次（约 2 分钟）仍不通过则自动回滚到 `.last-good-tag`。

## 触发规则

| 事件 | 行为 |
| --- | --- |
| PR → `dev` / `main` | 跑 CI：规格校验、敏感文件扫描、workflow 语法检查；按路径触发后端/Web/Android 检查 |
| push 到 `main`（`backend/**`、`web/**`、`deploy/**` 或工作流本身变化） | 构建镜像 + 部署到服务器 |
| `Actions → CD Deploy → Run workflow` | 手动补发/指定 ref；`concurrency` 保证部署串行 |
| `production` environment | 建议加人工批准，避免误合并直接上生产 |

部署只在 `main` 生效；`dev` 用于集成验证，不自动上线。

## 需要的 Secrets 与 Variables

在 `Settings → Secrets and variables → Actions` 配置，**值不进仓库**：

| 类型 | 名称 | 必需 | 用途 |
| --- | --- | --- | --- |
| Secret | `SSH_HOST` / `SSH_USER` / `SSH_KEY` | 是 | 部署目标与登录凭据 |
| Secret | `SSH_PORT` | 否 | 默认 22 |
| Secret | `GHCR_PULL_USER` / `GHCR_PULL_TOKEN` | 否 | 包为私有时拉取镜像用（Token 需 `read:packages`） |
| Variable | `DEPLOY_PATH` | 否 | 服务器部署目录，默认 `/opt/pandora` |
| Variable | `WEB_PORT` | 否 | 对外端口，默认 `80` |
| Variable | `APP_URL` | 否 | 访问地址，显示在 Actions 环境链接与摘要里 |
| Variable | `VITE_API_BASE_URL` | 否 | 留空 = 同域部署（推荐） |

正式运行密钥（`DJANGO_SECRET_KEY`、`IDENTITY_NUMBER_ENCRYPTION_KEY` 等）只存在服务器 `.env`，不走 Actions：

```bash
cd /opt/pandora && cp .env.example .env && chmod 600 .env && vi .env
```

`IDENTITY_NUMBER_ENCRYPTION_KEY` 一旦投入使用**不可更换**，否则已入库的身份证号密文无法解密。

## 协作与验收

- 改动部署链路（`Dockerfile`、`deploy/**`、`cd-deploy.yml`、`settings.py` 的环境变量）需要 CI/CD 负责人评审。
- 新增后端环境变量时，同步更新 `deploy/.env.example` 和 [server-deploy.md](server-deploy.md)，否则线上会缺配置。
- 提交 PR 时说明：影响了哪些工作流、是否需要新增 Secrets/Variables、如何回滚。
- 上线验收：`http://<服务器IP>/` 能打开登录页、`/api/health/` 返回 ok、Admin 能登录、上传附件成功、Actions 摘要显示健康检查通过。

## 后续待办

1. 确认云厂商、域名与 HTTPS 入口（前置 Caddy 或负载均衡），验证证书续期。
2. 评估 SQLite → MySQL 的切换窗口；切换前确认 `docs/backend/database-design.md` 与迁移方案。
3. 附件迁移到对象存储，并为 `pandora-data` 卷建立异地备份。
4. Android Release 签名与内测分发（`pandora-cicd/templates/cd-android-release.yml`）。
5. 生产监控：容器日志收集、磁盘与健康检查告警（至少配一条 `/api/health/` 拨测）。
