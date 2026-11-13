#!/usr/bin/env bash
# 服务器端部署脚本：由 GitHub Actions 通过 SSH 在 ${DEPLOY_PATH} 下执行。
# 也可手动执行：IMAGE_TAG=<tag> ./remote-deploy.sh
# 环境变量：
#   IMAGE_TAG      必填，要部署的镜像标签（git sha 前 12 位）
#   BACKEND_IMAGE / WEB_IMAGE   镜像地址（默认 ghcr.io/kid258w/projectpandora/*）
#   WEB_PORT       对外端口，默认 80
#   GHCR_USER / GHCR_TOKEN     私有仓库拉取凭据，留空表示匿名拉取
set -euo pipefail

cd "$(dirname "$0")"

: "${IMAGE_TAG:?IMAGE_TAG 未设置}"
export IMAGE_TAG
export WEB_PORT="${WEB_PORT:-80}"

if [ ! -f .env ]; then
  echo "错误：当前目录缺少 .env，请按 deploy/.env.example 创建并填写" >&2
  exit 1
fi

if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?GHCR_USER 未设置}" --password-stdin
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
