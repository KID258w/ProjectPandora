# Pandora 后端生产镜像：Django + Gunicorn
# 构建上下文固定为 backend/，由 .github/workflows/cd-deploy.yml 调用。
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# 先装依赖，代码改动不会让依赖层缓存失效
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 运行数据（SQLite、media）和 collectstatic 产物都在挂载卷里，
# 镜像本身不包含 db.sqlite3 / media / staticfiles。
RUN chmod +x /app/docker-entrypoint.sh \
    && mkdir -p /data/media /srv/static

EXPOSE 8000

# 用 sh -c 才能展开 GUNICORN_WORKERS；exec 保证 gunicorn 是 1 号进程，能收到 SIGTERM
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["sh", "-c", "exec gunicorn config.wsgi:application --bind 0.0.0.0:8000 --workers ${GUNICORN_WORKERS:-3} --timeout 60 --access-logfile - --error-logfile -"]
