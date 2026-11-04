# Android 正式 App 开发指南

## 当前项目现状

正式 Android App 位于仓库根目录的 `android/`，与用于甲方沟通演示的 `demo/` 分开维护。项目由 Android Studio 创建，使用 Kotlin、Jetpack Compose 和 Gradle Kotlin DSL。

目前已准备：

- Compose 主入口和五个底部导航页面：**首页、视图、日志、AI地图、我的**。
- 独立的任务中心页面入口与顶部返回栏；当前从“视图”页进入。
- Retrofit、OkHttp、Gson 转换器的共享客户端入口：`app/src/main/java/com/example/pandoraapp/core/network/ApiClient.kt`。
- 网络权限和 Debug 构建的明文 HTTP 配置，便于本地连接 Django；Release 构建不开放该明文流量配置。
- 所有页面目前均为占位，不包含真实业务、登录、持久化或 API 调用。

当前 `applicationId` / `namespace` 与 Kotlin 包名统一为 `com.projectpandora.app`，手机桌面显示名称为 `Pandora`。应用标识在正式发布后应保持稳定；更改后 Android 会将其视为不同应用，不能直接覆盖安装旧包。

## 打开与运行

1. 在一个终端启动 Django 后端，并保持它运行：

   ```powershell
   cd backend
   python manage.py runserver
   ```

2. 用 USB 连接已开启 USB 调试的手机；需要测试模拟器时，先启动 Android Studio 模拟器。
3. 在仓库根目录的 Git Bash 运行连接脚本：

   ```bash
   bash scripts/connect-android-backend.sh
   ```

   脚本会检查后端健康状态，并为所有已授权的手机和模拟器设置端口转发。成功时会列出设备序列号和“后端健康检查通过”。若手机弹出 USB 调试授权提示，请选择允许。

4. 用 Android Studio 打开仓库中的 `android/` 文件夹，等待 Gradle Sync 完成，选择手机或模拟器，点击绿色 **Run** 按钮运行 `app` 配置。两台设备需要分别选择并运行一次。

命令行构建（在 `android/` 目录执行）：

```powershell
.\gradlew.bat :app:assembleDebug
```

停止运行：点击 Android Studio 工具栏的停止按钮；命令行构建完成后，关闭终端即可。若仅关闭模拟器或 App 页面，Gradle 后台服务可能仍在运行。

## Django 地址配置

Debug API 默认地址为 `http://127.0.0.1:8000/`。连接脚本通过 `adb reverse tcp:8000 tcp:8000` 将手机或模拟器上的这个地址转发到电脑后端；手机和模拟器使用相同配置。Django 继续按 README 中的本地启动命令运行。手机断开重连、模拟器重启或 ADB 服务重启后，重新运行连接脚本即可。

App 从 `BuildConfig.API_BASE_URL` 读取基地址；脚本确认后端可用后，再在 Android Studio 点击 **Run** 构建并安装。API 路径须以 Django 实际路由及 Swagger 为准。只在 Debug 构建中允许 HTTP；生产环境应使用 HTTPS。

## 目录结构

```text
app/src/main/java/com/example/pandoraapp/
├── core/network/       Retrofit 客户端
├── feature/            按业务功能组织的 Compose 页面
├── navigation/         主导航与页面路由
├── ui/theme/           Android Studio 生成的 Compose 主题
└── MainActivity.kt     应用入口
```

后续新增业务时，在对应 `feature/` 中建立界面与 ViewModel，再通过 `core/network/` 的 Retrofit 服务调用后端；页面不应直接访问网络或数据库。

## 需求与协作文件

- Android 及共用业务需求：[`docs/requirements/`](../requirements/) 下的六份需求文档。
- API 契约：[`docs/backend/api-design.md`](../backend/api-design.md)。文档中尚未实现的接口须先与后端同学确认。
- Swagger 查看与接口联调：[`docs/cooperate/swagger-guide.md`](../cooperate/swagger-guide.md)。
- Git 分支和 PR 流程：[`docs/git/git-workflow.md`](../git/git-workflow.md)，Issue/PR 编写说明见同目录下对应指南。
- OpenSpec 变更索引：[`docs/openspec/openspec-summary.md`](../openspec/openspec-summary.md)。
