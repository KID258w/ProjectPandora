# Project Pandora

Project Pandora 旨在为企业内部工作提供任务创建与派发、上级审批和验收、工作日志、工作视图及 AI 辅助能力，包含 Android 员工端、Web 管理端和 Django 后端。

## 项目概况

| 模块 | 技术栈 | 目录 | 当前情况 |
| --- | --- | --- | --- |
| Android 正式 App | Kotlin、Jetpack Compose、Retrofit | `android/` | 工程与导航骨架已建立，业务功能待开发 |
| Android UI 原型 | Kotlin、Jetpack Compose | `demo/` | 用于与甲方沟通和验证界面，不等同于正式 App |
| Web 管理端 | Vue 3、Vite、Element Plus、Axios | `web/` | 管理端骨架和占位页面已建立，业务功能待开发 |
| 后端 API | Django、Django REST Framework、SQLite | `backend/` | 服务、认证基础和 Swagger 文档已建立，业务 API 尚未实现 |
| CI/CD 与部署 | GitHub Actions、Docker Buildx、GHCR、Docker Compose | `deploy/` | 技术栈已确定，当前为占位目录，尚无可运行的 CI/CD 或部署配置 |

当前阶段以需求确认和基础工程搭建为主，`demo/` 用于界面沟通，正式业务功能尚未实现。产品需求与实施计划主要记录在 `docs/requirements/` 和 `openspec/changes/`；文档中的规划功能不代表相应生产代码已经实现。SQLite 用于本地开发。

## 快速开始

### 环境准备

- Git
- Python 3.11
- Node.js 20.19+ 或 22.12+，以及 npm
- 若运行 Android App 或原型：Android Studio、项目所需 Android SDK；模拟器可选，也可连接开启 USB 调试的手机

Docker 不是本地日常功能开发的必需品。CI/CD 固定采用 GitHub Actions、Docker Buildx、GHCR 和 Docker Compose；相关流程及服务器部署配置由负责组员实施。

### 1. 克隆项目

团队开发以 `dev` 为集成分支：

```bash
git clone --branch dev --single-branch https://github.com/KID258w/ProjectPandora.git
cd ProjectPandora
```

开始具体开发前，请按 [`Git 工作流`](docs/git/git-workflow.md) 从最新 `dev` 创建 Issue 指定的功能分支，不要直接在 `main` 或 `dev` 上开发。

### 2. 启动 Django 后端

在仓库根目录打开终端，执行：

```powershell
cd backend
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
python manage.py migrate
python manage.py runserver
```

`Copy-Item` 只在首次配置本地环境时执行；如果 `.env` 已存在，不要覆盖。后端默认地址为 `http://127.0.0.1:8000`。运行后可访问：

- 健康检查：<http://127.0.0.1:8000/api/health/>
- Swagger UI：<http://127.0.0.1:8000/api/docs/>
- Django Admin：<http://127.0.0.1:8000/admin/>

停止后端：在运行 `runserver` 的终端按 `Ctrl+C`。

### 3. 启动 Web 管理端

另开一个终端，在仓库根目录执行：

```powershell
cd web
npm install
npm run dev
```

浏览器打开终端显示的地址，通常为 <http://localhost:5173/>。Web 默认请求本机 Django 后端；如需调整地址，参见 [`Web 指南`](docs/web/web-guide.md)。停止服务时，在运行 Vite 的终端按 `Ctrl+C`。

### 4. 运行 Android App（可选）

在 Android Studio 中打开仓库的 `android/` 目录，等待 Gradle Sync 完成，选择模拟器或已开启 USB 调试的手机，然后点击 **Run**。模拟器访问电脑上的 Django 服务时使用 `10.0.2.2` 地址。具体说明见 [`Android 指南`](docs/android/android-guide.md)。

用于甲方演示的原型是单独的 `demo/` 项目；运行方式见 [`Demo 指南`](docs/demo/guide.md)。

## 文档导航

- [`docs/requirements/`](docs/requirements/)：产品需求文档
- [`docs/backend/api-design.md`](docs/backend/api-design.md)：前后端 API 契约设计
- [`docs/backend/database-design.md`](docs/backend/database-design.md)：数据库设计
- [`docs/backend/backend-guide.md`](docs/backend/backend-guide.md)：后端环境、启动、测试与停止说明
- [`docs/web/web-guide.md`](docs/web/web-guide.md)：Web 前端说明
- [`docs/android/android-guide.md`](docs/android/android-guide.md)：正式 Android App 说明
- [`docs/cooperate/swagger-guide.md`](docs/cooperate/swagger-guide.md)：前后端接口协作与 Swagger 使用
- [`docs/git/git-workflow.md`](docs/git/git-workflow.md)：Issue、分支、提交与 PR 流程
- [`docs/openspec/openspec-summary.md`](docs/openspec/openspec-summary.md)：OpenSpec 变更范围总览
- [`docs/cicd/cicd-guide.md`](docs/cicd/cicd-guide.md)：CI/CD 模块协作引导与待办范围

## 项目协作约定

需求、数据结构或接口发生变化时，同步更新对应的需求文档、OpenSpec change，以及 API/数据库设计（如受影响）。正式开发从 Issue 开始，完成后通过 PR 合入 `dev`；阶段验证稳定后，再通过 PR 合入 `main`。详细流程以 [`docs/git/git-workflow.md`](docs/git/git-workflow.md) 为准。
