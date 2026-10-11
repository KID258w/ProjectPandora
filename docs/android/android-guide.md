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

1. 用 Android Studio 打开仓库中的 `android/` 文件夹，等待 Gradle Sync 完成。
2. 选择模拟器或已开启 USB 调试的手机作为目标设备。
3. 点击绿色 **Run** 按钮运行 `app` 配置。

命令行构建（在 `android/` 目录执行）：

```powershell
.\gradlew.bat :app:assembleDebug
```

停止运行：点击 Android Studio 工具栏的停止按钮；命令行构建完成后，关闭终端即可。若仅关闭模拟器或 App 页面，Gradle 后台服务可能仍在运行。

## Django 地址配置

默认 Debug API 地址为 `http://10.0.2.2:8000/`，这是 Android 模拟器访问宿主机的地址。请求层从 `BuildConfig.API_BASE_URL` 读取基地址，API 路径须以 Django 实际路由及 Swagger 为准。

如需用 USB/无线调试的实体手机连接开发电脑，可在启动 Gradle 命令时临时覆盖地址（不要提交个人 IP）：

```powershell
.\gradlew.bat :app:assembleDebug -PpandoraApiBaseUrl=http://192.168.1.10:8000/
```

将示例 IP 换成开发电脑的局域网 IP。实体手机和电脑需在同一网络，Django 开发服务需监听局域网地址，并允许防火墙通过相应端口。通过 Android Studio 运行时如需覆盖，可在个人 Gradle 属性文件中设置 `pandoraApiBaseUrl`；Windows 通常为 `%USERPROFILE%\.gradle\gradle.properties`。只在 Debug 构建中允许 HTTP；生产环境应使用 HTTPS。

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
