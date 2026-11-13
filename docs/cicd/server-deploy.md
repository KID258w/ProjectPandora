# 服务器部署手册（CI/CD 负责人维护）

> 面向执行者：从零把项目部署到一台 Linux 云服务器，以及日常更新、回滚、备份、排障。
> 方案概览与触发规则见 [cicd-guide.md](cicd-guide.md)。

## 1. 部署形态

```
GitHub main 分支
   │  push
   ▼
GitHub Actions（.github/workflows/cd-deploy.yml）
   ├─ 构建 backend 镜像（Django + Gunicorn）
   ├─ 构建 web 镜像（Vite 产物 + Nginx）
   ├─ 推送 GHCR：ghcr.io/kid258w/projectpandora/{backend,web}
   └─ SSH 到服务器 → docker compose pull/up → 健康检查 → 失败自动回滚
                            │
                            ▼
                服务器 /opt/pandora（Docker Compose）
                ├─ web 容器：Nginx :80，静态页 + /api /admin 反代 + /static /media
                └─ backend 容器：Gunicorn :8000（仅容器内网）
                   卷 pandora-data  → SQLite、media 附件、迁移前备份
                   卷 pandora-static → collectstatic 产物（web 只读挂载）
```

要点：

- 只有 web 容器映射宿主机端口（`WEB_PORT`，默认 80），后端不对外暴露。
- 前端与后端同域，请求走相对路径 `/api/v1/...`，无需配置跨域和公网 API 地址。
- 运行数据全部在 Docker 卷里，重建容器/换镜像不丢数据。

## 2. 一次性准备

### 2.1 服务器

Ubuntu 22.04/24.04（或任意装得动 Docker 的 Linux），建议 2C2G 起步，开放 22 与 80（用 HTTPS 再加 443）。

```bash
# 以 sudo 用户登录后，在仓库根目录执行
DEPLOY_PATH=/opt/pandora bash deploy/bootstrap-server.sh
```

脚本会安装 Docker + Compose 插件、创建部署目录。首次部署前先写 `.env`：

```bash
cd /opt/pandora
cp .env.example .env      # 首次部署时 GitHub Actions 会把 .env.example 一起推上来
chmod 600 .env
vi .env
```

必填项：

| 变量 | 说明 |
| --- | --- |
| `DJANGO_SECRET_KEY` | 正式密钥，`python -c "import secrets;print(secrets.token_urlsafe(64))"` |
| `IDENTITY_NUMBER_ENCRYPTION_KEY` | `python -c "from cryptography.fernet import Fernet;print(Fernet.generate_key().decode())"`，**部署后不可更换**，否则已加密的身份证号无法解密 |
| `DJANGO_ALLOWED_HOSTS` | 域名或公网 IP，本机健康检查也要包含 `127.0.0.1` |
| `DJANGO_CSRF_TRUSTED_ORIGINS` | 如 `https://pandora.example.com`；用 HTTP+IP 访问时填 `http://<IP>` |
| `CORS_ALLOWED_ORIGINS` | 同域部署时填访问来源即可 |

`DJANGO_DEBUG` 保持 `False`；此时后端启动会强制校验上面两个密钥，缺任何一个都会启动失败（这是有意的）。

### 2.2 GitHub 仓库配置

`Settings → Secrets and variables → Actions`：

| 类型 | 名称 | 用途 |
| --- | --- | --- |
| Secret | `SSH_HOST` | 服务器 IP 或域名 |
| Secret | `SSH_USER` | 登录用户（需在 docker 组，或能 `sudo docker`） |
| Secret | `SSH_KEY` | 该用户的私钥全文（`-----BEGIN ...-----`） |
| Secret | `SSH_PORT` | 可选，默认 22 |
| Secret | `GHCR_PULL_USER` / `GHCR_PULL_TOKEN` | 可选；**包设为私有**时必须，Token 需 `read:packages` |
| Variable | `DEPLOY_PATH` | 可选，默认 `/opt/pandora` |
| Variable | `WEB_PORT` | 可选，默认 `80`；若前面还有 Caddy/Nginx 反代，改成 `127.0.0.1` 上的空闲端口 |
| Variable | `APP_URL` | 访问地址，用于 Actions 页面的环境链接与摘要 |
| Variable | `VITE_API_BASE_URL` | 留空 = 同域部署（推荐）；前后端不同域时才填后端公网地址 |

公钥放到服务器 `~/.ssh/authorized_keys`。镜像是否需要登录：仓库 Packages 里把 `projectpandora/backend`、`projectpandora/web` 设为 Public 就可以不配 `GHCR_PULL_*`。

建议在 `Settings → Environments → production` 里加保护规则（必须人工批准 / 限制分支为 `main`），避免误合并直接上生产。

## 3. 首次部署

1. 确认 `.env` 已填好。
2. 合并任意一次改动到 `main`，或在 Actions 页面手动触发 **CD Deploy**（`Run workflow`）。
3. 观察 job：
   - `构建并推送镜像`：两个镜像各打 `:<sha12>` 与 `:latest`。
   - `部署到服务器`：上传 `docker-compose.yml` + 部署脚本 → `remote-deploy.sh` → 健康检查。
4. 打开 `http://<服务器IP>/` 看到登录页、`http://<服务器IP>/api/health/` 返回 `{"status":"ok",...}` 即成功。

创建管理员账号（宿主机上执行）：

```bash
cd /opt/pandora
docker compose exec backend python manage.py createsuperuser
```

> 账号数据存在 `pandora-data` 卷里，之后每次部署都会保留。

## 4. 日常更新

正常流程不需要碰服务器：PR 合入 `dev` → 阶段验证 → PR 合入 `main` → 自动部署。

手动补发（回滚到历史提交、或临时指定分支）：

```
Actions → CD Deploy → Run workflow → 选择分支/ref
```

## 5. 回滚

镜像回滚（最常见）：

```bash
cd /opt/pandora
cat .last-good-tag          # 当前记录的上一个健康版本
./remote-rollback.sh        # 回到 .last-good-tag
./remote-rollback.sh <tag>  # 或指定任意历史 tag（即 git sha 前 12 位）
```

部署失败时 `remote-deploy.sh` 会**自动**执行一次回滚并把后端日志打到 Actions 输出里。

数据回滚：镜像回滚**不会**回滚数据库迁移。若新版本迁移不可逆：

```bash
cd /opt/pandora
docker compose stop backend
docker run --rm -v pandora_pandora-data:/data -v "$PWD":/backup alpine \
  cp /backup/db-20250101-120000.sqlite3 /data/db.sqlite3   # 换成实际备份文件
docker compose start backend
```

备份文件在卷内 `/data/backups/`，每次容器启动（即每次部署）前自动生成，保留最近 7 份：

```bash
docker compose exec backend ls -lt /data/backups | head
```

建议再配一条宿主机定时任务，把备份同步到别处（对象存储/另一台机器），避免单机磁盘故障：

```bash
# crontab -e
0 3 * * * docker run --rm -v pandora_pandora-data:/data -v /opt/pandora/backup:/backup alpine \
  sh -c 'cp -a /data/backups /backup/ && ls -1t /backup/backups/db-*.sqlite3 | tail -n +31 | xargs -r rm -f'
```

## 6. 故障排查

| 现象 | 排查 |
| --- | --- |
| Actions 在「上传 Compose 与部署脚本」失败 | `SSH_HOST/USER/KEY` 是否正确；服务器安全组是否放行 22；私钥是否包含完整 BEGIN/END |
| `拉取镜像` 报 `unauthorized` | 包是私有的：配置 `GHCR_PULL_USER/GHCR_PULL_TOKEN`（`read:packages`） |
| 报 `.env 缺少` | 按 2.1 创建 `/opt/pandora/.env` |
| 健康检查一直失败 | `docker compose logs --tail=200 backend`；常见原因：`.env` 里密钥缺失（`ImproperlyConfigured`）、`DJANGO_ALLOWED_HOSTS` 没包含访问用的 Host |
| 页面能开但接口 502 | `docker compose ps` 看 backend 是否 healthy；后端没起来时 web 也会连带不健康 |
| Admin 页面 403 CSRF | `DJANGO_CSRF_TRUSTED_ORIGINS` 没配或协议写错（https/http） |
| 上传附件失败 413 | Nginx `client_max_body_size`（默认 12m）与 `COMPANY_ITEM_ATTACHMENT_MAX_BYTES` 是否匹配 |
| 改了代码但页面没变 | 是否合到了 `main`；浏览器/CDN 缓存；`docker compose ps` 看容器启动时间 |
| 服务器磁盘涨 | `docker image prune -f`、检查 `/data/backups` 与 media 体积 |

常用命令：

```bash
cd /opt/pandora
docker compose ps
docker compose logs -f backend
docker compose logs -f web
docker compose exec backend python manage.py check --deploy
curl -i http://127.0.0.1/api/health/
```

## 7. HTTPS 与域名（可选）

当前 web 容器只监听 HTTP。有域名后最简单的做法是在宿主机前置一层 Caddy：

```bash
# /etc/caddy/Caddyfile
pandora.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

同时把 `deploy/.env` 的 `WEB_PORT` 改成 `8080`（或 GitHub Variable `WEB_PORT=8080`），并更新 `DJANGO_ALLOWED_HOSTS`、`DJANGO_CSRF_TRUSTED_ORIGINS`、`CORS_ALLOWED_ORIGINS` 为域名。

Caddy 会自动申请并续期证书；若用云厂商负载均衡/CDN，注意放行并转发 `X-Forwarded-Proto`（Nginx 已透传）。

## 8. 已知限制

- **SQLite 单实例**：只能跑一个 backend 副本，方言与并发能力有限；多人正式使用时建议按 `docs/backend/database-design.md` 切 MySQL，届时补一个 db 服务与备份策略。
- **附件在本地卷**：换机需迁移 `pandora-data` 卷；量大时改对象存储。
- **无零停机**：`compose up` 会短暂中断（秒级），健康检查通过才算发布成功。
- **Android 端未自动化**：App 走 `cd-android` 模板（签名 + 分发）另行落地，与后端部署相互独立。
