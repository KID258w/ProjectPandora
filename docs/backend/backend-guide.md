# Backend 使用指南

本文记录 Project Pandora 当前 Django 后端骨架的组成、首次安装、日常运行和停止方式。后端代码位于仓库根目录的 `backend/`。

## 当前后端情况

- 主框架：Django 5.2；接口框架：Django REST Framework（DRF）。
- 本地数据库：SQLite，默认文件是 `backend/db.sqlite3`。当前没有 MySQL 连接配置。
- 认证基础：Simple JWT，提供获取和刷新令牌接口。
- 接口文档：OpenAPI、Swagger UI、ReDoc。
- 已有公开健康检查：`GET /api/health/`。
- 已建立自定义用户模型，当前登录名使用 `username`，并有 `display_name` 字段；业务角色、部门/团队、RBAC 权限和业务模块还没有实现。
- Vue 本地开发地址 `http://localhost:5173` 与 `http://127.0.0.1:5173` 已加入 CORS 白名单。
- Windows 本地开发使用 Django `runserver`。Gunicorn 依赖只在非 Windows 环境安装，供后续 Linux/Docker 部署使用。

SQLite 数据库文件、虚拟环境和 `.env` 都在 `backend/.gitignore` 中，不会作为项目源文件提交。迁移文件会提交，组员在本地运行 `migrate` 后即可建立自己的 SQLite 数据库。

## 首次安装（Windows / PowerShell）

先确认已安装 Python 3.11 或更新版本。在仓库根目录打开 PowerShell：

```powershell
cd backend
py -3.11 --version
py -3.11 -m venv .venv
```

激活虚拟环境并安装依赖：

```powershell
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

如果 PowerShell 阻止执行激活脚本，可仅对当前终端临时放行，然后重新激活：

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

配置本地环境文件。只在首次安装且尚无 `.env` 时复制；已有 `.env` 时不要覆盖：

```powershell
Copy-Item .env.example .env
```

`.env` 当前用于开发环境配置。默认 `DJANGO_DEBUG=True`，仅供本机开发；不要将 `.env` 提交到 Git，也不要把当前开发配置直接用于公网部署。

## 初始化数据库并启动

在 `backend/` 目录、且虚拟环境已激活时运行：

```powershell
python manage.py migrate
python manage.py check
python manage.py runserver
```

成功后，开发服务器默认监听 `127.0.0.1:8000`。可在浏览器中打开：

| 用途 | 地址 |
| --- | --- |
| 健康检查 | <http://127.0.0.1:8000/api/health/> |
| Swagger UI | <http://127.0.0.1:8000/api/docs/> |
| ReDoc | <http://127.0.0.1:8000/api/redoc/> |
| Django Admin | <http://127.0.0.1:8000/admin/> |

健康检查成功时会返回类似内容：

```json
{"status":"ok","service":"pandora-backend"}
```

如需进入 Django Admin，可创建本地管理员账号：

```powershell
python manage.py createsuperuser
```

这是你本机 SQLite 数据库里的开发账号，不会自动创建在其他组员的数据库中。

## 常用命令

在 `backend/` 目录中执行：

```powershell
# 自动化测试
python manage.py test

# 检查 Django 配置
python manage.py check

# 生成模型迁移（模型新增或变更时）
python manage.py makemigrations

# 应用迁移到本机 SQLite 数据库
python manage.py migrate
```

JWT 接口：

- `POST /api/auth/token/`：提交 JSON `{ "username": "账号", "password": "密码" }`，获取 `access` 和 `refresh`。
- `POST /api/auth/token/refresh/`：提交 JSON `{ "refresh": "刷新令牌" }`，获取新的 `access`。

除健康检查、令牌获取/刷新和文档入口外，DRF 接口默认要求携带有效 JWT。当前还没有具体业务 API。

## 停止后端进程

### 推荐方式：在运行服务器的终端停止

`runserver` 会持续占用当前终端。回到显示 Django 请求日志的那个终端，按 `Ctrl+C`，看到进程退出后即可关闭终端。停止服务器不会删除数据库或修改源文件。

### 终端已关闭或进程在后台运行时（Windows）

先查找 8000 端口的监听进程：

```powershell
Get-NetTCPConnection -LocalPort 8000 -State Listen |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

用上一步显示的进程号检查它确实是本项目的 Django `runserver`，将示例中的 `12345` 换成实际进程号：

```powershell
Get-CimInstance Win32_Process -Filter "ProcessId = 12345" |
    Select-Object ProcessId, Name, CommandLine
```

确认 `CommandLine` 指向本项目的 `manage.py runserver` 后，再停止该进程：

```powershell
Stop-Process -Id 12345
```

不要仅凭端口号直接结束进程；如果该端口属于其他程序，请勿停止它。若服务器改用其他端口，例如 `8001`，查询时也要相应替换 `8000`。

### 端口被占用时

可以在启动时指定另一个端口：

```powershell
python manage.py runserver 8001
```

此时访问地址中的端口也改为 `8001`，例如 `http://127.0.0.1:8001/api/health/`。

## 当前范围与注意事项

这只是后端基础骨架，不代表任务派发、审批验收、日志、工作视图、AI、组织管理或 RBAC 已经完成。新增业务前应先对照 `docs/requirements/` 和对应 OpenSpec change 确认需求。

本地 SQLite 适合当前原型与早期开发。后续多人联调或部署时，再由团队统一决定 MySQL 配置、Docker 数据卷和环境变量；不要把开发 SQLite 文件当作共享数据库。
