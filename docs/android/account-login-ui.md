# Android 账号登录与当前用户

本文记录 Issue #6 的 Android 接口对齐阶段、Issue #1 遗留修复，以及 Issue2 待实现的个人资料维护。界面顺序仍为启动页、欢迎页、登录页、主界面和“我的”；登录及个人资料改由后端业务账号接口提供数据，不再使用本地演示账号。

## 本次 Issue #1 遗留修复

- 登录页将登录标识统一称为“手机号”，提示和错误校验文案也使用手机号；请求仍发送现有 `username` 字段，字段值为员工当前手机号。
- 个人资料页展示手机号和身份证号脱敏值；完整身份证号不会从当前用户接口读取或在页面展示。

## 后端接口

Android 只调用 `/api/v1/app/` 业务用户入口。成功和失败响应都使用 `code`、`message`、`data`、`requestId` 信封；登录或刷新 API 返回的 JWT 位于 `data` 中。

| 方法与路径 | 请求 | 使用方式 |
| --- | --- | --- |
| `POST /api/v1/app/auth/token/` | `{"username":"13800000001","password":"<密码>"}` | `username` 填员工当前手机号；返回 `data.access` 和 `data.refresh`，随后请求当前用户 |
| `POST /api/v1/app/auth/token/refresh/` | `{"refresh":"<refresh JWT>"}` | 返回新的 `data.access`；若服务端启用轮换，也接受新的 `data.refresh` |
| `GET /api/v1/app/me/` | `Authorization: Bearer <access JWT>` | 返回当前业务用户的姓名、身份、部门、团队和直属上级 |
| `PATCH /api/v1/app/me/` | 资料字段的 JSON 子集 | 员工本人更新姓名、手机号和身份证号；手机号放在 `username` 字段 |
| `POST /api/v1/app/me/password/` | 当前密码、新密码和新密码确认 | 员工本人修改密码；服务端不回传密码 |

当前用户的主要字段为 `username`、`account_type`、`business_role`、`display_name`、`id_number_masked`、`department_name`、`team_name`、`manager_name` 和 `is_active`。其中 `username` 是当前手机号，`id_number_masked` 是身份证脱敏值。App 只接受 `account_type=business` 且启用中的用户，并将 `founder`、`department_manager`、`team_leader`、`employee` 显示为“创始人”“部门经理”“团队长”“员工”。首页问候、“我的”身份卡片及个人资料页均使用 `/app/me/` 的返回值。

## Issue2 个人资料维护目标（尚未实现）

- 员工可在“我的”个人资料页修改自己的姓名、手机号和身份证号，并通过独立的密码修改操作修改本人密码。
- 姓名和身份证号放在同一个资料编辑表单中，可以一次提交保存；两项不自动联动，只修改其中一项时不要求另一项也发生变化。
- `/api/v1/app/me/` 只返回身份证脱敏值。修改身份证号时由员工重新输入完整号码；App 不从接口读取或展示完整身份证号。
- App 提交资料修改后使用后端返回的最新资料刷新页面。手机号变更成功后，后续登录输入新手机号，请求仍将手机号放在 `username` 字段。
- 密码修改表单要求输入当前密码、新密码和确认密码；密码规则及失败原因以服务端响应为准。密码修改成功后，App 不保存明文密码。
- App 不提供业务身份、部门/团队归属、直属上级或启用状态的编辑入口。

## 登录、刷新和退出

1. 启动页先恢复本地会话。没有令牌时进入欢迎页；有令牌时请求 `/app/me/`，校验成功后进入首页。网络或服务暂时不可用时保留令牌并显示“重试验证”入口。
2. 登录请求通过后，新令牌暂存到 AES-GCM 加密的 SharedPreferences，再请求 `/app/me/`。只有取得有效的业务用户信息才进入主界面；新凭据或资料请求失败时恢复此前保存的令牌对。
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
- Android：`./gradlew.bat lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`，通过。10 项 JVM 测试和 Android 37 模拟器上的 7 项仪器测试通过。

2026-10-22 会话恢复修复验证：

- Android：`./gradlew.bat lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest --no-daemon`，通过。14 项 JVM 测试和 8 项模拟器仪器测试通过，覆盖重新登录保留旧会话及临时验证故障后的重试。
- Debug APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`。

Android CI 的标准复现命令为：

```bash
cd android
./gradlew lintDebug testDebugUnitTest assembleDebug
```
