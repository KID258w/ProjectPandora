# Project Pandora Web 管理端

基于 Vue 3、Vite、Element Plus、Vue Router 和 Axios 的管理端前端骨架。

## 本地运行

需要 Node.js 20.19+（或 22.12+）和 npm。

```bash
npm install
npm run dev
```

Vite 默认在 `http://localhost:5173` 启动。

## 配置后端地址

复制 `.env.example` 为 `.env.local`，按需修改 `VITE_API_BASE_URL`。默认 Django 地址为 `http://127.0.0.1:8000`。

管理端业务 API 目前尚未实现；当前工作台和导航页面是结构占位。Axios 实例已放在 `src/services/http.js`，JWT access token 的请求头读取入口也已预留。

## 构建

```bash
npm run build
npm run preview
```

## 目录结构

```text
src/
├── layouts/       管理端整体布局
├── router/        页面路由
├── services/      HTTP 请求实例
├── views/         页面视图
├── App.vue
└── main.js
```
