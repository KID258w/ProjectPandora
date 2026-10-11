# Web 前端使用与协作指南

## 当前 Web 前端现状

前端项目位于仓库根目录的 `web/`，基于 Vue 3、Vite、Element Plus、Vue Router 和 Axios。

目前已建立可运行的管理端骨架，包括：

- 管理端整体布局和侧边导航。
- 工作台页面。
- 组织管理、公司重要事项、系统管理等路由及占位页面。
- Axios 请求实例，位于 `web/src/services/http.js`。
- 后端地址环境变量示例，位于 `web/.env.example`。

当前还没有完成具体业务页面、业务数据接入、登录鉴权和真实 API 联调。现有工作台及占位页面仅用于搭建工程结构，不代表功能已经实现。

## 环境要求

- Node.js 20.19+ 或 22.12+。
- npm。

可在终端检查版本：

```bash
node --version
npm --version
```

## 启动项目

在仓库根目录打开 PowerShell、终端或 VS Code Terminal，执行：

```powershell
cd web
npm install
npm run dev
```

首次启动或依赖有变化时执行 `npm install`；依赖已安装且没有变化时，之后通常只需：

```powershell
cd web
npm run dev
```

终端会显示本地访问地址，通常为 <http://localhost:5173/>。如果 5173 端口已被占用，Vite 可能自动改用其它端口，请以终端显示的地址为准。

## 关闭项目

在运行 `npm run dev` 的同一个终端窗口按 `Ctrl+C`，开发服务器就会停止。关闭浏览器页面不会停止开发服务器。

如果找不到启动服务器的终端，先检查 5173 端口对应的进程，再确认它确实是本项目的 Vite/Node 进程后再结束；不要直接结束所有 Node 进程。Vite 若使用了其它端口，请替换下面的端口号。

```powershell
Get-NetTCPConnection -LocalPort 5173 -State Listen | Select-Object LocalAddress, LocalPort, OwningProcess
$viteProcessId = 1234 # 将 1234 替换为上一步显示且确认属于本项目的进程号
Get-Process -Id $viteProcessId
Stop-Process -Id $viteProcessId
```

## 配置后端地址

项目提供 `web/.env.example`，默认后端地址为 `http://127.0.0.1:8000`。本地需要自定义时，在 `web/` 目录下创建 `.env.local`，并设置：

```dotenv
VITE_API_BASE_URL=http://127.0.0.1:8000
```

变量值填写 Django 服务根地址，不要额外拼接 `/api`。Vite 环境变量必须使用 `VITE_` 前缀；修改后需要重启开发服务器。不要把密钥或其它敏感凭据放进前端环境变量，因为它们会被打包到客户端。

## 主要目录

```text
web/
├── src/
│   ├── layouts/       管理端整体布局
│   ├── router/        页面路由
│   ├── services/      HTTP 请求实例
│   ├── views/         工作台和各业务页面
│   ├── App.vue
│   ├── main.js
│   └── style.css
├── .env.example       后端地址配置示例
├── package.json       命令与依赖
└── README.md          项目简要说明
```

构建检查：

```powershell
npm run build
```

## 需求与协作文件

开发 Web 页面或与后端联调时，按以下文档协作维护：

- Web 管理端需求：[`docs/requirements/05-web-admin-console.md`](../requirements/05-web-admin-console.md)。
- API 契约（前后端共同核对）：[`docs/backend/api-design.md`](../backend/api-design.md)。文档中的规划接口不一定已经实现，联调前请以 Django 实际路由和 Swagger 展示为准。
- 数据模型参考：[`docs/backend/database-design.md`](../backend/database-design.md)。Web 前端通常不直接访问数据库，字段和业务规则以 API 契约为准。
- Swagger 查看与试调：[`docs/cooperate/swagger-guide.md`](../cooperate/swagger-guide.md)。
- Git 分支、PR 与合并流程：[`docs/git/git-workflow.md`](../git/git-workflow.md)。
- Issue 提交说明：[`docs/git/issue-template-guide.md`](../git/issue-template-guide.md)；PR 提交说明：[`docs/git/pr-template-guide.md`](../git/pr-template-guide.md)。
- OpenSpec 变更范围索引：[`docs/openspec/openspec-summary.md`](../openspec/openspec-summary.md)。

需求有变化时，先在 Issue 中明确任务范围和分支名；涉及接口、字段或数据结构变化时，同步更新 API/数据库设计并在 PR 中说明，前后端再按 Swagger 契约联调。不要仅凭前端占位页面或尚未落地的接口草案判断业务已经完成。
