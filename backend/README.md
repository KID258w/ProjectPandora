# Pandora Backend

Django + Django REST Framework 后端。当前使用 SQLite，暂不配置 MySQL；管理员/业务账号、组织与两端登录、Issue #20 日志/标签及 Issue #21 任务/审批/通知接口已实现。

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
- App 日志、标签、任务中心、直属上级审批、完成验收、任务定义历史及站内通知接口；请求与权限规则见 `../docs/backend/api-design.md`。
- 公司重要事项管理（草稿/发布/下线/排序/附件）、App 首页四面板聚合、个人十大重点工作；附件存 `backend/media/`（已 gitignore），开发期由 Django 提供 `/media/`，正式环境需持久化目录或对象存储。
- Vue 开发服务器默认允许 `localhost:5173` 和 `127.0.0.1:5173` 跨域访问；可通过 `.env` 中的 `CORS_ALLOWED_ORIGINS` 调整。

生产环境不要使用默认开发密钥或 `DEBUG=True`。本地 SQLite 文件适合原型开发；将来部署 Docker 时需要配置持久化卷，正式协作环境再切换 MySQL。

