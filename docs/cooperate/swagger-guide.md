# Swagger 接口协作指南

本文面向 Django 后端、Web 前端和 Android 前端同学，说明如何用 Swagger 查看、联调和维护接口。Swagger 是浏览器里的接口文档与试调工具，不是后端框架，也不会替代 Web/Android 客户端代码。

## 当前文档与接口范围

启动后端后访问：

- Swagger UI：<http://127.0.0.1:8000/api/docs/>
- ReDoc 阅读版：<http://127.0.0.1:8000/api/redoc/>
- OpenAPI Schema：<http://127.0.0.1:8000/api/schema/>

目前后端骨架仅提供健康检查和 JWT 令牌接口，还没有任务、日志、组织等业务接口。因此当前 Swagger 主要用于验证服务可用、查看登录令牌接口；业务 API 会随着后续开发陆续出现。详细启动说明见[后端使用指南](../backend/backend-guide.md)。

## 三方协作的基本约定

前后端开始一个功能前，先确认接口的请求方法、URL、参数、响应字段、错误响应和权限要求。后端实现并在 Swagger 中展示后，Web 和 Android 按同一份接口契约对接；若接口契约改变，先在 issue/PR 中同步变更，再更新客户端，避免双方各自猜字段。

常见 HTTP 方法：`GET` 查询、`POST` 新建或执行操作、`PATCH` 部分更新、`DELETE` 删除。请求与响应通常使用 JSON。状态码也要一并处理，例如 `200`/`201` 成功、`400` 参数错误、`401` 未登录或令牌失效、`403` 无权限、`404` 资源不存在。

## 后端同学：生成和维护 Swagger 文档

接口说明由后端代码生成，不要直接手工改 `/api/schema/` 的输出。DRF 序列化器定义输入/输出结构，视图实现处理逻辑，URL 配置暴露路径；使用 `drf-spectacular` 注解补全面向前端的中文说明。

```python
from drf_spectacular.utils import extend_schema


@extend_schema(
    summary="查询我的任务",
    description="返回当前登录用户承担且尚未完成的任务。",
    tags=["任务管理"],
)
def get(self, request):
    ...
```

序列化器字段应提供清晰的 `label`、`help_text` 和必要的校验约束；不要只写“获取数据”“提交信息”等无法帮助客户端联调的说明。路径、JSON 键和代码字段名维持稳定、简洁的英文命名，显示给开发者阅读的标题和说明使用中文。

每次增加或修改接口后，在 `backend/` 目录执行：

```powershell
python manage.py check
python manage.py test
python manage.py spectacular --validate --lang zh-hans --file openapi.yml
```

第三条命令会生成并校验本地 `openapi.yml`。它是临时生成文件；若要提交生成物，先在团队中约定存放位置与更新责任。然后启动后端，在 Swagger UI 检查接口分组、说明、参数、响应和权限是否与实现一致。

## Web 前端同学：查看与试调接口

1. 启动 Django 后打开 Swagger UI，找到对应的接口分组。
2. 查看请求方法和路径、Query/Path 参数、JSON 请求体、成功响应及错误状态码。
3. 点“Try it out”填写参数并执行，观察请求 URL、请求体、响应体和状态码。
4. 将确认后的路径和字段映射到 Vue 页面与 Axios 请求层；不要把 Swagger 页面当成 Web 管理端页面。

当前 Vue 开发地址 `http://localhost:5173` 和 `http://127.0.0.1:5173` 已在后端 CORS 白名单。如果 Vite 改用了其它端口（例如 `5174`），浏览器可能报跨域错误；请同步告知后端同学调整本地 `CORS_ALLOWED_ORIGINS`，不要用关闭浏览器安全策略的方式绕过。

试调新增、修改或删除接口会真实写入当前开发数据库。共享演示环境或生产环境不要随意使用 Swagger 的执行按钮；当前本机 SQLite 数据仅属于本机。

## Android 前端同学：把接口接入 App

Swagger 用于确认契约和手动验证，Android App 仍需通过 Retrofit 发 HTTP/HTTPS 请求，并由 Kotlin 数据类映射 JSON。对接时重点核对：

- Retrofit 的请求方法、路径和 Base URL 与文档一致。
- `@Body` 请求数据、查询参数、响应 DTO 的字段名和 JSON 类型与 Schema 一致。
- 需要登录的接口，通过拦截器发送 `Authorization: Bearer <access令牌>`；不要把密码或 refresh 令牌放进普通业务请求。
- 对 `401`、网络断开、超时和服务端 `4xx/5xx` 做明确处理，不要只依赖 Swagger 里成功的示例。

本机调试地址需按设备类型区分：

- Android Studio 模拟器访问开发电脑：`http://10.0.2.2:8000/`。
- 实体手机访问开发电脑：使用电脑的局域网 IP，例如 `http://192.168.x.x:8000/`；Django 要以 `python manage.py runserver 0.0.0.0:8000` 启动，并确保手机与电脑在同一网络、系统防火墙允许该端口。仅用于可信的本地网络调试，不要将开发服务器暴露到公网。

如果 Android 调试构建拒绝明文 HTTP，按项目的 Android 网络安全配置在 debug 环境允许本地 HTTP；正式环境应使用 HTTPS，避免在正式包中全局放开明文流量。

## JWT 登录与 Swagger 授权

当前登录接口是 `POST /api/auth/token/`，请求示例：

```json
{
  "username": "本地开发账号",
  "password": "该账号的密码"
}
```

成功响应包含 `access` 与 `refresh`。`access` 用于访问受保护接口；过期后可将 `refresh` 提交到 `POST /api/auth/token/refresh/` 获取新的 `access`。不要把令牌提交到 Git、截图公开或写入日志。

目前 Schema 中还没有受保护的业务接口，因此 Swagger 可能暂时不显示 Bearer 授权按钮。后端加入受保护接口后，如 Swagger 显示“Authorize”，在弹窗中按提示粘贴 access 令牌；若认证方式是 HTTP Bearer，通常只粘贴令牌本身，不要重复添加 `Bearer ` 前缀。客户端请求头则需要完整格式：`Authorization: Bearer <access令牌>`。

## 常见问题

- **Swagger 页面打不开：**确认 Django 正在运行，并检查地址端口是否为 `8000`。
- **接口返回 401：**确认是否需要登录、access 是否过期，以及请求头是否为 `Authorization: Bearer <access>`。
- **Swagger 试调正常但网页请求失败：**检查浏览器开发者工具中的 CORS 报错、Web 当前端口和 Django 日志。
- **实体手机连不上：**确认 Django 绑定 `0.0.0.0`、手机与电脑处于同一局域网，并检查 Windows 防火墙。
- **页面只有基础接口：**这是当前骨架的预期状态；任务、日志等功能接口尚未实现。
