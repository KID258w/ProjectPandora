#!/usr/bin/env bash
# 服务器端部署脚本：由 GitHub Actions 通过 SSH 在 ${DEPLOY_PATH} 下执行。
# 也可手动执行：IMAGE_TAG=<tag> ./remote-deploy.sh
# 环境变量：
#   IMAGE_TAG      必填，要部署的镜像标签（git sha 前 12 位）
#   BACKEND_IMAGE / WEB_IMAGE   镜像地址（默认 ghcr.io/kid258w/projectpandora/*）
#   WEB_PORT       容器对外映射端口，默认 8080（仅绑 127.0.0.1，由宿主 Nginx 反代）
#   PANDORA_DATA_DIR  宿主机数据目录，默认 /var/lib/pandora（bind mount 到容器 /data）
#   APP_URL        可选，公网地址；设置后额外验证一次对外入口
#   GHCR_USER / GHCR_TOKEN     私有仓库拉取凭据，留空表示匿名拉取
set -euo pipefail

cd "$(dirname "$0")"

: "${IMAGE_TAG:?IMAGE_TAG 未设置}"
export IMAGE_TAG
export WEB_PORT="${WEB_PORT:-8080}"
export PANDORA_DATA_DIR="${PANDORA_DATA_DIR:-/var/lib/pandora}"

# 压缩后的旧镜像会占满磁盘，保留最近几个 tag 由 docker image prune 处理
if [ ! -f .env ]; then
  echo "错误：当前目录缺少 .env，请按 deploy/.env.example 创建并填写" >&2
  echo "提示：为避免与旧的手动部署配置不一致，建议直接软链到共享配置：" >&2
  echo "  ln -sfn /opt/pandora/shared/.env /opt/pandora/.env" >&2
  exit 1
fi

if [ ! -d "$PANDORA_DATA_DIR" ]; then
  echo "错误：数据目录 $PANDORA_DATA_DIR 不存在。" >&2
  echo "首次从手动部署迁移时，该目录应已存在（/var/lib/pandora）；新服务器请先运行 bootstrap-server.sh" >&2
  exit 1
fi

if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?GHCR_USER 未设置}" --password-stdin
fi

# 部署前备份（Issue 要求）：在新镜像启动、迁移之前先留一份快照。
# 容器 entrypoint 也会在启动时备份一次，两层叠加，这里的是“上一次可用状态”。
if [ -f "$PANDORA_DATA_DIR/db.sqlite3" ] && [ "${PANDORA_DB_BACKUP:-1}" != "0" ]; then
  mkdir -p "$PANDORA_DATA_DIR/backups"
  snapshot="$PANDORA_DATA_DIR/backups/db-predeploy-$(date +%Y%m%d-%H%M%S).sqlite3"
  cp "$PANDORA_DATA_DIR/db.sqlite3" "$snapshot"
  echo "==> 部署前已备份数据库：$snapshot"
  # 只保留最近 30 份部署前快照
  ls -1t "$PANDORA_DATA_DIR"/backups/db-predeploy-*.sqlite3 2>/dev/null | tail -n +31 | xargs -r rm -f
else
  echo "==> 跳过部署前备份（数据库尚未创建，或 PANDORA_DB_BACKUP=0）"
fi

echo "==> 拉取镜像 $IMAGE_TAG"
docker compose pull backend web

echo "==> 启动容器"
docker compose up -d --remove-orphans

# 健康判断：优先按容器健康检查状态（backend 的健康检查就是 /api/health/），
# 宿主机有 curl 时再额外验证一次对外端口。
service_healthy() {
  [ "$(docker compose ps --format '{{.Service}} {{.Health}}' | awk -v s="$1" '$1==s{print $2}')" = "healthy" ]
}

probe() {
  if command -v curl >/dev/null 2>&1; then
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${WEB_PORT}/api/health/" || true)"
    [ "$code" = "200" ]
  else
    service_healthy backend && service_healthy web
  fi
}

echo "==> 健康检查 http://127.0.0.1:${WEB_PORT}/api/health/"
for i in $(seq 1 30); do
  if probe; then
    echo "$IMAGE_TAG" > .last-good-tag
    docker image prune -f >/dev/null 2>&1 || true
    echo "==> 部署成功：$IMAGE_TAG"
    # 额外验证一次公网入口（宿主 Nginx → 本容器）。仅提示，不影响发布结果。
    if [ -n "${APP_URL:-}" ] && command -v curl >/dev/null 2>&1; then
      public_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${APP_URL%/}/api/health/" || true)"
      if [ "$public_code" = "200" ]; then
        echo "==> 公网入口正常：${APP_URL%/}/api/health/"
      else
        echo "警告：公网入口返回 ${public_code:-无响应}，请检查宿主 Nginx 反向代理配置" >&2
      fi
    fi
    exit 0
  fi
  echo "    第 ${i}/30 次未通过，4s 后重试"
  sleep 4
done

echo "==> 健康检查失败，自动回滚" >&2
docker compose ps >&2 || true
docker compose logs --tail=80 backend >&2 || true
./remote-rollback.sh || echo "回滚未执行（可能没有可用历史版本），请人工处理" >&2
exit 1
