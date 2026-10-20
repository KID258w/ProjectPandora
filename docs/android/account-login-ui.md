# Android 账号登录与当前用户

本文记录 Issue #6 的 Android 接口对齐阶段（PR 6）。界面顺序仍为启动页、欢迎页、登录页、主界面和“我的”；登录及个人资料改由后端业务账号接口提供数据，不再使用本地演示账号。

## 后端接口

Android 只调用 `/api/v1/app/` 业务用户入口。成功和失败响应都使用 `code`、`message`、`data`、`requestId` 信封；登录或刷新 API 返回的 JWT 位于 `data` 中。

| 方法与路径 | 请求 | 使用方式 |
| --- | --- | --- |
| `POST /api/v1/app/auth/token/` | `{"username":"employee01","password":"<密码>"}` | 返回 `data.access` 和 `data.refresh`；随后请求当前用户 |
| `POST /api/v1/app/auth/token/refresh/` | `{"refresh":"<refresh JWT>"}` | 返回新的 `data.access`；若服务端启用轮换，也接受新的 `data.refresh` |
| `GET /api/v1/app/me/` | `Authorization: Bearer <access JWT>` | 返回当前业务用户的姓名、身份、部门、团队和直属上级 |

当前用户的主要字段为 `username`、`account_type`、`business_role`、`display_name`、`department_name`、`team_name`、`manager_name` 和 `is_active`。App 只接受 `account_type=business` 且启用中的用户，并将 `founder`、`department_manager`、`team_leader`、`employee` 显示为“创始人”“部门经理”“团队长”“员工”。首页问候、“我的”身份卡片及个人资料页均使用 `/app/me/` 的返回值。

## 登录、刷新和退出

1. 启动页先恢复本地会话。没有令牌时进入欢迎页；有令牌时请求 `/app/me/`，校验成功后进入首页。
2. 登录请求通过后，令牌暂存到 AES-GCM 加密的 SharedPreferences，再请求 `/app/me/`。只有取得有效的业务用户信息才进入主界面。
3. 受保护请求收到 `401` 时，OkHttp Authenticator 串行调用 App 刷新接口，并将原请求用新 access token 重试一次。refresh 被拒绝或过期时删除本地令牌，启动流程进入登录页并提示重新登录。
4. 错误密码、停用账号或错误账号类型均不建立登录态；登录页显示“账号或密码错误；如果账号已停用，请联系管理员。”服务端的 `403` 显示 App 权限提示。
5. 网络错误不会被当作令牌过期。恢复会话时遇到网络错误会保留令牌并提示检查网络；服务端错误显示稍后重试提示。
6. 退出登录只清除本地 access/refresh 令牌并返回登录页。后端当前没有注销端点。

令牌对以 AES-GCM 加密后保存，AES 密钥由 Android Keystore 管理；密码不会写入磁盘。令牌偏好文件已从云备份和设备迁移备份中排除。通知设置保存在另一份非敏感偏好中。

## 本地联调地址

Debug 构建默认使用 Android 模拟器访问宿主机的 `http://10.0.2.2:8000/`。在 `backend/` 启动本地服务：

```powershell
python manage.py runserver 0.0.0.0:8000
```

连接实体手机时，将 Gradle 属性覆盖为运行后端电脑的局域网地址，例如：

```powershell
cd android
./gradlew.bat assembleDebug -PpandoraApiBaseUrl=http://192.168.1.20:8000/
```

Debug 变体为本地 HTTP 开放明文流量；Release 变体关闭明文流量，正式服务应使用 HTTPS。

## Launcher 图标

Launcher 的 adaptive icon 与 API 24–25 密度图标使用提供的 `Pandoraz_APP_logo.png`。应用内 Figma 品牌图保持原样。

## 验证记录

2026-10-20 本地验证：

- 后端：`python manage.py test --verbosity 2`，17 项测试通过，覆盖 App 登录、刷新、当前用户、错误密码、停用账号及账号类型隔离。
- Android：`./gradlew.bat lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`，通过。10 项 JVM 测试覆盖响应映射、凭据失败、会话恢复，以及 Retrofit 登录、当前用户、401 刷新和重试；`connectedDebugAndroidTest` 在隔离的 Android 37 模拟器上 7 项全部通过，覆盖账户 UI 流程和 Keystore 令牌存储。
- Debug APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`。

Android CI 的标准复现命令为：

```bash
cd android
./gradlew lintDebug testDebugUnitTest assembleDebug
```
