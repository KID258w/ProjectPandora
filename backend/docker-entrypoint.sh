#!/bin/sh
# 后端容器启动入口：迁移 → 收集静态文件 → 启动 Gunicorn。
# 迁移前自动备份 SQLite，备份保留最近 7 份，便于回滚时恢复数据。
set -e

# 数据根目录固定为容器内挂载点 /data（宿主机 ${PANDORA_DATA_DIR} 由 compose 挂到这里）。
# 不能读 PANDORA_DATA_DIR：它是宿主机路径，会随 env_file 注入容器；
# 用它拼备份路径会写进容器可写层，容器重建后备份全部丢失。
DATA_DIR="/data"
DB_PATH="${SQLITE_PATH:-$DATA_DIR/db.sqlite3}"
BACKUP_DIR="$DATA_DIR/backups"

mkdir -p "$(dirname "$DB_PATH")" "${DJANGO_MEDIA_ROOT:-$DATA_DIR/media}" "${DJANGO_STATIC_ROOT:-/srv/static}"

if [ -f "$DB_PATH" ] && [ "${PANDORA_DB_BACKUP:-1}" != "0" ]; then
  mkdir -p "$BACKUP_DIR"
  backup="$BACKUP_DIR/db-$(date +%Y%m%d-%H%M%S).sqlite3"
  cp "$DB_PATH" "$backup"
  echo "[entrypoint] SQLite 已备份到 $backup"
  # 只保留最近 7 份；文件名带时间戳，按时间倒序删除更旧的
  ls -1t "$BACKUP_DIR"/db-*.sqlite3 2>/dev/null | tail -n +8 | xargs -r rm -f
fi

echo "[entrypoint] 应用数据库迁移"
python manage.py migrate --noinput

echo "[entrypoint] 收集静态文件到 ${DJANGO_STATIC_ROOT:-/srv/static}"
python manage.py collectstatic --noinput --clear >/dev/null

echo "[entrypoint] 启动：$*"
exec "$@"
