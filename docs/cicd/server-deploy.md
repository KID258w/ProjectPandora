# 服务器部署手册（CI/CD 负责人维护）

本文是服务器侧的实操手册：首次准备、从旧的手动部署迁移、更新、回滚、备份、排障。
CI 门禁与 PR 流程见 [ci-gates.md](ci-gates.md) 与 [pr-check-workflow.md](pr-check-workflow.md)；
整体方案与协作约定见 [cicd-guide.md](cicd-guide.md)。

## 1. 部署形态

本项目采用**两层反向代理**：宿主 Nginx 继续占用 80/443 并终止 HTTPS，Docker 里的 web 容器
只绑定宿主机回环地址的一个高位端口（默认 `8080`）。

```
公网用户
   │  https://pandora.xinphotoshare.click  (443, Let's Encrypt 证书在宿主 Nginx)
   ▼
宿主机 Nginx (80/443)
   │  proxy_pass http://127.0.0.1:8080     ← 全部请求转给 Docker
   ▼
Docker Compose 项目 pandora
   ├─ web 容器      nginx:80   静态页 + /api /admin 反代 + /static /media
   │                            （宿主机只映射 127.0.0.1:${WEB_PORT:-8080}）
   └─ backend 容器  gunicorn:8000（仅容器内网，不对外暴露）
        ├─ bind mount  ${PANDORA_DATA_DIR:-/var/lib/pandora} → /data
        │     /data/db.sqlite3   SQLite 数据库
        │     /data/media        上传附件
        │     /data/backups      迁移前自动备份
        └─ 命名卷 pandora-static → /srv/static（collectstatic 产物，供 web 只读挂载）
```

关键约定：

- **只有 web 容器映射宿主机端口**，且必须带 `127.0.0.1:` 前缀。写成 `8080:80` 会让公网绕过
  宿主 Nginx 直接访问明文 HTTP 入口。
- **数据用 bind mount 直接挂宿主机目录**，不使用命名卷。这样容器与旧的手动部署共用同一份
  `db.sqlite3` 和 `media/`，迁移时不搬数据、回退时数据仍在原处。
- **`X-Forwarded-Proto` 必须透传**。容器内 Nginx 若用 `$scheme` 会把它覆盖成 `http`，
  导致 Django 认为请求非 HTTPS，Admin 的 CSRF 校验 403。`web/nginx.conf` 已用 `map` 透传上游值。

## 2. 一次性准备

### 2.1 服务器

```bash
# 以 sudo 用户登录后，在仓库根目录执行（会装 Docker、建目录、准备数据目录）
DEPLOY_PATH=/opt/pandora PANDORA_DATA_DIR=/var/lib/pandora bash deploy/bootstrap-server.sh
```

脚本会：

1. 安装 Docker Engine 与 Compose 插件；
2. 创建 `/opt/pandora`；
3. 创建数据目录 `/var/lib/pandora/{media,backups}`；
4. 若检测到已有 `db.sqlite3`，自动留一份 `db-before-docker-<时间戳>.sqlite3` 快照再继续。

### 2.2 宿主 Nginx 反向代理（保留 HTTPS）

宿主 Nginx 继续负责证书，只把请求转发给 Docker。把站点配置里原有的
`proxy_pass http://127.0.0.1:8002;` 和 `root /opt/pandora/app/web/dist;` 全部改成 `8080`：

```nginx
# 改前备份：cp /etc/nginx/sites-available/pandora /etc/nginx/sites-available/pandora.bak

server {
    listen 80;
    server_name pandora.xinphotoshare.click;

    # certbot webroot 续期，保持原样
    location /.well-known/acme-challenge/ { root /var/www/letsencrypt; }

    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl http2;
    server_name pandora.xinphotoshare.click;

    ssl_certificate     /etc/letsencrypt/live/pandora.xinphotoshare.click/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/pandora.xinphotoshare.click/privkey.pem;

    # 与容器 web/nginx.conf 的 client_max_body_size 保持一致，两处都要够大
    client_max_body_size 12m;

    # 全部交给 Docker 里的 web 容器，由容器再分发到静态文件 / API / Admin
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;   # 关键：https 由此传入
        proxy_read_timeout 120s;
    }
}
```

改完后先 `nginx -t` 校验，再 `systemctl reload nginx`。校验失败就还原 `.bak`。

> `systemctl restart nginx` 会短时中断本机所有站点（含同机的其他网站），日常改配置请用 `reload`。

### 2.3 GitHub 仓库配置

Settings → Secrets and variables → Actions：

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| Secret | `SSH_HOST` | 服务器公网 IP |
| Secret | `SSH_USER` | SSH 用户名（需有 docker 权限） |
| Secret | `SSH_KEY` | 私钥全文，含 `BEGIN/END` 行 |
| Secret | `SSH_PORT` | 可选，默认 22 |
| Secret | `GHCR_PULL_USER` | 可选；镜像包为私有时必填 |
| Secret | `GHCR_PULL_TOKEN` | 可选；需 `read:packages` 权限 |
| Variable | `DEPLOY_PATH` | 可选，默认 `/opt/pandora` |
| Variable | `PANDORA_DATA_DIR` | 可选，默认 `/var/lib/pandora` |
| Variable | `WEB_PORT` | 可选，默认 `8080`（仅绑定回环地址） |
| Variable | `APP_URL` | 建议填 `https://pandora.xinphotoshare.click`，用于部署后验证公网入口 |
| Variable | `VITE_API_BASE_URL` | 留空表示同域（前端走相对路径），建议留空 |

Environment `production` 用于部署 job，可在 Settings → Environments 里加人工审批或限分支。

## 3. 从手动部署迁移到容器（本项目一次性步骤）

首次手动部署（Issue 41）用的是 systemd + venv + 宿主 Nginx，数据在 `/var/lib/pandora`。
切到容器只换运行方式，**数据不搬**：

1. **确认数据目录位置**

   ```bash
   ls -l /var/lib/pandora/db.sqlite3 /var/lib/pandora/media | head
   ```

   如果实际路径不是 `/var/lib/pandora`，把 GitHub Variable `PANDORA_DATA_DIR` 改成真实路径。

2. **复用同一份 `.env`，避免两份配置漂移**

   ```bash
   # 旧配置在 /opt/pandora/shared/.env，容器读 /opt/pandora/.env
   ln -sfn /opt/pandora/shared/.env /opt/pandora/.env
   chmod 600 /opt/pandora/shared/.env
   ```

   注意容器内的路径是 `/data/...`，所以 `.env` 里应保持：

   ```dotenv
   SQLITE_PATH=/data/db.sqlite3
   DJANGO_MEDIA_ROOT=/data/media
   DJANGO_STATIC_ROOT=/srv/static
   ```

   而宿主机真实目录由 `PANDORA_DATA_DIR` 通过 compose 挂载决定，不要写进 `SQLITE_PATH`。

3. **补两个新变量**（旧配置里没有，缺了会导致 Admin 登录 403）

   ```dotenv
   DJANGO_CSRF_TRUSTED_ORIGINS=https://pandora.xinphotoshare.click
   DJANGO_ALLOWED_HOSTS=pandora.xinphotoshare.click,127.0.0.1,localhost
   ```

   > 🔴 **`IDENTITY_NUMBER_ENCRYPTION_KEY` 绝对不要重新生成**。它用于加密员工身份证号，
   > 一旦换成新值，库里已有身份证号将**永久无法解密**（代码会报
   > `Stored identity-number data cannot be decrypted`）。迁移时原样沿用旧值，
   > 并把这个 key 与 `DJANGO_SECRET_KEY` 另存到密码管理器，不要写进仓库或 Issue。

4. **停掉旧的 systemd 服务，释放 8002 端口**

   ```bash
   systemctl stop pandora-backend
   systemctl disable pandora-backend
   ```

   宿主 Nginx 此时还指向 8002，站点会短暂不可用，属于预期。

5. **第一次部署**（Actions → CD Deploy → Run workflow，或合一次 `main`）

6. **切换宿主 Nginx 到 8080**（第 2.2 节），`nginx -t && systemctl reload nginx`

7. **回退预案**：保留 `/opt/pandora/app` 与 `venv` 不删。若容器版持续异常，
   `nginx` 配置还原 `.bak` + `systemctl enable --now pandora-backend` 即可切回，
   数据因为共用 `/var/lib/pandora` 不受影响（除非新版本做过不可逆迁移，见第 6 节）。

## 4. 首次部署验证（对应 Issue 验收项）

| # | 验证项 | 操作与判据 |
| --- | --- | --- |
| 1 | `dev` 更新不触发部署 | 向 `dev` 推一个提交 → Actions 里**没有** CD Deploy 运行 |
| 2 | `main` 更新触发并部署对应提交 | 合入 `main` 后看 run 摘要里的镜像标签 = 该提交 sha 前 12 位 |
| 3 | 健康检查通过、云端可访问 | `curl -i https://<域名>/api/health/` 返回 200；Web 能登录、App 能登录 |
| 4 | 数据与附件保留 | 部署前后各记一次 `docker compose exec backend python manage.py shell -c "from django.contrib.auth import get_user_model;print(get_user_model().objects.count())"` 与 `ls /var/lib/pandora/media \| wc -l`，数值不变 |
| 5 | 备份文件已生成 | `ls -lt /var/lib/pandora/backups \| head`，有本次时间戳的 `db-predeploy-*.sqlite3` |
| 5.1 | **身份证号仍能解密** | 登入 App 或查库：`docker compose exec backend python manage.py shell -c "from apps.accounts.models import User;u=User.objects.exclude(id_number_hash=None).first();print(u.id_number[:3]+'***', len(u.id_number))"`。能打印出明文长度说明 `IDENTITY_NUMBER_ENCRYPTION_KEY` 正确；报 `Stored identity-number data cannot be decrypted` 就是密钥错了，**立即回滚，不要继续迁移** |
| 6 | 失败回退已记录 | 见第 6 节；可故意把健康检查路径改错跑一次，确认自动回滚生效后还原 |

## 5. 日常更新

正常流程不需要碰服务器：PR 合入 `dev` → 阶段验证 → PR 合入 `main` → 自动部署。

手动补发（回滚到历史提交、或临时指定分支）：

```
Actions → CD Deploy → Run workflow → 选择分支/ref
```

## 6. 回滚

**镜像回滚**（最常见）：

```bash
cd /opt/pandora
cat .last-good-tag          # 当前记录的上一个健康版本
./remote-rollback.sh        # 回到 .last-good-tag
./remote-rollback.sh <tag>  # 或指定任意历史 tag（即 git sha 前 12 位）
```

部署失败时 `remote-deploy.sh` 会**自动**执行一次回滚，并把后端日志打到 Actions 输出里。

**数据回滚**：镜像回滚**不会**回滚数据库迁移。若新版本迁移不可逆：

```bash
cd /opt/pandora
docker compose stop backend
cp /var/lib/pandora/db.sqlite3 /var/lib/pandora/backups/db-before-restore.sqlite3  # 先留现状
cp /var/lib/pandora/backups/db-20250101-120000.sqlite3 /var/lib/pandora/db.sqlite3  # 换成实际文件
docker compose start backend
```

> 因为数据是 bind mount，回滚数据等同于直接覆盖宿主机 `/var/lib/pandora/db.sqlite3`，
> 不再需要 `docker run -v 卷名` 那种操作。

## 7. 备份

两层备份并存，都落在 `/var/lib/pandora/backups/`，靠文件名区分：

| 来源 | 触发时机 | 文件名 |
| --- | --- | --- |
| 容器 entrypoint | 每次容器启动（即每次部署）前 | `db-YYYYmmdd-HHMMSS.sqlite3`，保留最近 7 份 |
| 宿主 cron | 每日定时 | 建议命名 `daily-db-YYYYmmdd.sqlite3` |
| 迁移前快照 | `bootstrap-server.sh` 首次运行 | `db-before-docker-YYYYmmdd-HHMMSS.sqlite3` |

推荐再加一条定时任务，把备份同步到别处，避免单机磁盘故障：

```bash
# crontab -e
0 3 * * * cp /var/lib/pandora/db.sqlite3 /var/lib/pandora/backups/daily-db-$(date +\%Y\%m\%d).sqlite3
30 3 * * * ls -1t /var/lib/pandora/backups/daily-db-*.sqlite3 | tail -n +31 | xargs -r rm -f
```

查看现有备份：

```bash
ls -lt /var/lib/pandora/backups | head
```

## 8. 故障排查

| 现象 | 排查方向 |
| --- | --- |
| Actions 在「上传 Compose 与部署脚本」失败 | `SSH_HOST/USER/KEY` 是否正确；安全组是否放行 22；私钥是否含完整 `BEGIN/END` |
| `拉取镜像` 报 `unauthorized` | 包为私有：配置 `GHCR_PULL_USER/GHCR_PULL_TOKEN`（`read:packages`） |
| 报 `.env 缺少` | 按 2.3 创建 `/opt/pandora/.env`，或软链到 `/opt/pandora/shared/.env` |
| 报「数据目录不存在」 | `PANDORA_DATA_DIR` 写错，或新服务器未跑 `bootstrap-server.sh` |
| `Bind for 0.0.0.0:80 failed: port is already allocated` | compose 端口没改成 `127.0.0.1:8080`，与宿主 Nginx 抢 80 |
| 健康检查一直失败 | `docker compose logs --tail=200 backend`；常见：`.env` 缺密钥（`ImproperlyConfigured`）、`DJANGO_ALLOWED_HOSTS` 不含访问用的 Host |
| **Admin 能开但登录 403 CSRF** | ① `DJANGO_CSRF_TRUSTED_ORIGINS` 是否含 `https://域名`；② 容器 Nginx 是否透传 `X-Forwarded-Proto`（不要用 `$scheme`） |
| 页面能开但接口 502 | `docker compose ps` 看 backend 是否 healthy；后端没起来时 web 会连带不健康 |
| 上传附件 413 | 宿主 Nginx 和容器 Nginx 的 `client_max_body_size` 都要 ≥ 附件上限 |
| 数据库是空的（账号全没了） | 十有八九是 `PANDORA_DATA_DIR` 没指到旧数据目录，检查 compose 的 bind mount 实际值 |
| 改了代码但页面没变 | 是否合到了 `main`；浏览器缓存；`docker compose ps` 看容器启动时间 |
| 服务器磁盘涨 | `docker image prune -f`；检查 `/var/lib/pandora/backups` 与 `media` 体积 |

常用命令：

```bash
cd /opt/pandora
docker compose ps
docker compose logs -f backend
docker compose logs -f web
docker compose exec backend python manage.py check --deploy
docker compose exec backend ls -lt /data/backups | head      # 容器视角
curl -i http://127.0.0.1:8080/api/health/                    # 容器入口
curl -i https://pandora.xinphotoshare.click/api/health/      # 公网入口
```

## 9. 已知限制

- **SQLite 单实例**：只能跑一个 backend 副本，并发写入能力有限；多人正式使用时按
  `docs/backend/database-design.md` 切 MySQL，届时补 db 服务与备份策略。
- **数据在宿主机目录**：换机需整体迁移 `/var/lib/pandora`；量大时改对象存储。
- **回滚不回滚迁移**：镜像回滚只换代码，若迁移不可逆必须同时恢复数据库。
- **无零停机**：`compose up` 会有秒级中断，健康检查通过才算发布成功。
- **两层 Nginx 需同步维护**：`client_max_body_size`、gzip、超时等策略两处都要改，容易漏。
- **容器以 root 运行**：写入 `/var/lib/pandora` 的文件属主为 root，若将来引入非 root 的备份或
  FPM 进程需处理权限。
- **Android 端未自动化**：App 走 `templates/cd-android-release.yml` 模板（签名 + 分发）另行落地。
