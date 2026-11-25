#!/usr/bin/env bash
# 一次性服务器初始化脚本（Ubuntu 22.04/24.04 或 Debian）。
# 在以 sudo 权限登录后执行：bash deploy/bootstrap-server.sh
# 只做三件事：装 Docker + Compose 插件、建部署目录、生成 .env 骨架。
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/pandora}"
PANDORA_DATA_DIR="${PANDORA_DATA_DIR:-/var/lib/pandora}"

if ! command -v docker >/dev/null 2>&1; then
  echo "==> 安装 Docker Engine 与 Compose 插件"
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
else
  echo "==> 已安装 Docker：$(docker --version)"
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "错误：缺少 docker compose 插件，请安装 docker-compose-plugin" >&2
  exit 1
fi

echo "==> 创建部署目录 $DEPLOY_PATH"
mkdir -p "$DEPLOY_PATH"
if [ ! -f "$DEPLOY_PATH/.env" ]; then
  if [ -f "$DEPLOY_PATH/.env.example" ]; then
    cp "$DEPLOY_PATH/.env.example" "$DEPLOY_PATH/.env"
  else
    echo "    提示：部署文件由 GitHub Actions 首次推送，.env 请按仓库 deploy/.env.example 手工创建"
  fi
  chmod 600 "$DEPLOY_PATH/.env" 2>/dev/null || true
fi

echo "==> 准备数据目录 $PANDORA_DATA_DIR"
# 容器以 bind mount 方式直接读写宿主机目录，必须先存在且可写，
# 否则容器启动时会把“目录不存在”当成空数据库。
mkdir -p "$PANDORA_DATA_DIR/media" "$PANDORA_DATA_DIR/backups"
chmod 755 "$PANDORA_DATA_DIR"

if [ -f "$PANDORA_DATA_DIR/db.sqlite3" ]; then
  echo "    检测到已有数据库：$PANDORA_DATA_DIR/db.sqlite3（将沿用，不会重建）"
  # 切换到容器部署前保留一份快照，便于整体回退
  snap="$PANDORA_DATA_DIR/backups/db-before-docker-$(date +%Y%m%d-%H%M%S).sqlite3"
  cp "$PANDORA_DATA_DIR/db.sqlite3" "$snap"
  echo "    已备份为：$snap"
fi

echo "==> 建议同时开启防火墙（确认 SSH 端口后再执行）"
echo "    ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable"

cat <<EOF

完成。后续步骤：
1. 在 $DEPLOY_PATH/.env 填入 DJANGO_SECRET_KEY、IDENTITY_NUMBER_ENCRYPTION_KEY、
   DJANGO_ALLOWED_HOSTS 等（参考 deploy/.env.example）。
   若机器上已有旧的手动部署配置，建议软链复用，避免两份 .env 漂移：
     ln -sfn /opt/pandora/shared/.env $DEPLOY_PATH/.env
2. 在 GitHub 仓库 Settings → Secrets and variables → Actions 配置
   SSH_HOST / SSH_USER / SSH_KEY
   Variables：DEPLOY_PATH、PANDORA_DATA_DIR(默认 /var/lib/pandora)、
              WEB_PORT(默认 8080)、APP_URL
3. 宿主机 Nginx 的反向代理要指向 127.0.0.1:\${WEB_PORT:-8080}，
   详见 docs/cicd/server-deploy.md 的「宿主 Nginx 反向代理」一节。
4. 向 main 分支合并一次（或手动触发 CD Deploy），首次部署会自动完成。
EOF
