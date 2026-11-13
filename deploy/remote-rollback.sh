#!/usr/bin/env bash
# 服务器端回滚脚本：回到上一次健康检查通过的镜像标签。
#   ./remote-rollback.sh            # 回滚到 .last-good-tag
#   ./remote-rollback.sh <tag>      # 回滚到指定标签
# 注意：镜像回滚不会回滚数据库迁移。若新版本包含不可逆迁移，
# 需同时用 /data/backups/db-*.sqlite3 恢复数据（见 docs/cicd/server-deploy.md）。
set -euo pipefail

cd "$(dirname "$0")"

TAG="${1:-$(cat .last-good-tag 2>/dev/null || true)}"
if [ -z "$TAG" ]; then
  echo "没有可回滚的版本（.last-good-tag 不存在），请指定标签" >&2
  exit 1
fi

export IMAGE_TAG="$TAG"

echo "==> 回滚到 $TAG"
docker compose pull backend web || true
docker compose up -d --remove-orphans
echo "==> 已回滚到 $TAG；确认无误后执行 docker compose ps 检查状态"
