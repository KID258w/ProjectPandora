# Pandora Backend

Django + Django REST Framework 的后端骨架。当前使用 SQLite，暂不配置 MySQL；Issue #6 的管理员/业务账号、组织与两端登录已实现，任务、日志等工作模块将在对应功能设计时逐步加入。

## 本地运行

建议使用 Python 3.11 或更新版本。Windows PowerShell 示例：

```powershell
cd backend
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
python manage.py migrate
python manage.py runserver
```

访问以下地址检查运行状态：

- 健康检查：<http://127.0.0.1:8000/api/health/>
- Swagger UI：<http://127.0.0.1:8000/api/docs/>
- ReDoc：<http://127.0.0.1:8000/api/redoc/>
- Django Admin：<http://127.0.0.1:8000/admin/>

运行自动化测试：

```powershell
python manage.py test
```

Linux/Docker 生产运行入口预留为 Gunicorn：

```sh
gunicorn config.wsgi:application --bind 0.0.0.0:8000
```

部署前需设置正式 `DJANGO_SECRET_KEY`、关闭 `DJANGO_DEBUG`，并为 SQLite 数据文件配置持久化存储；正式环境再按部署方案切换到 MySQL。

## 当前骨架包含

- SQLite 本地数据库，文件默认为 `backend/db.sqlite3`。
- DRF JSON API 和公开健康检查接口。
- Simple JWT 获取/刷新令牌接口：`POST /api/v1/admin/auth/token/`、`POST /api/v1/admin/auth/token/refresh/`、`POST /api/v1/app/auth/token/`、`POST /api/v1/app/auth/token/refresh/`。
- drf-spectacular OpenAPI、Swagger UI 和 ReDoc 文档。
- 可供后续扩展的自定义用户模型；登录名使用 Django 的 `username`，并已落地 `account_type`、`business_role`、部门/团队/上级关系及账号录入、当前用户、部门/团队接口。契约见 `../docs/backend/account-provisioning.md`。
- Vue 开发服务器默认允许 `localhost:5173` 和 `127.0.0.1:5173` 跨域访问；可通过 `.env` 中的 `CORS_ALLOWED_ORIGINS` 调整。

生产环境不要使用默认开发密钥或 `DEBUG=True`。本地 SQLite 文件适合原型开发；将来部署 Docker 时需要配置持久化卷，正式协作环境再切换 MySQL。

