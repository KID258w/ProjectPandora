#!/usr/bin/env bash
# 一次性服务器初始化脚本（Ubuntu 22.04/24.04 或 Debian）。
# 在以 sudo 权限登录后执行：bash deploy/bootstrap-server.sh
# 只做三件事：装 Docker + Compose 插件、建部署目录、生成 .env 骨架。
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/pandora}"

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

echo "==> 建议同时开启防火墙（确认 SSH 端口后再执行）"
echo "    ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable"

cat <<EOF

完成。后续步骤：
1. 在 $DEPLOY_PATH/.env 填入 DJANGO_SECRET_KEY、IDENTITY_NUMBER_ENCRYPTION_KEY、
   DJANGO_ALLOWED_HOSTS 等（参考 deploy/.env.example）。
2. 在 GitHub 仓库 Settings → Secrets and variables → Actions 配置
   SSH_HOST / SSH_USER / SSH_KEY（Variables: DEPLOY_PATH、WEB_PORT、APP_URL）。
3. 向 main 分支合并一次（或手动触发 CD Deploy），首次部署会自动完成。
EOF
