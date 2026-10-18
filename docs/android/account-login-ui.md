# Android 账号与个人中心 UI

当前 Android 账号流程按 Figma「Android · 账号登录 · Issue #6」实现：黄色无按钮启动画面（1 秒后自动进入）、白色欢迎页、账号登录、首页、个人设置、资料详情、通知设置、帮助与反馈及退出确认。五个底部标签复用首页、视图、日志、AI 地图和我的路由；图标、Pandora 标识、字色与橙/红色状态来自 Figma 导出资源和其色板。

## 界面预览账号

Debug 构建提供本地 UI 演示账号，密码均为 `Pandora123!`：

| 账号 | 身份 | 组织 |
| --- | --- | --- |
| `chenning` | 员工 | 产品部 · 产品一组 |
| `zhouxiao` | 团队长 | 产品部 · 产品一组 |
| `linjingli` | 部门经理 | 产品部 |
| `zhouzong` | 创始人 | 公司 |
| `disabled` | 停用状态演示 | — |

演示认证只用于 Debug UI 验收。Release 不接受本地演示账号，当前尚未接入服务端登录、令牌刷新和个人资料 API；演示会话只保存账号标识，不保存密码。

## 本地构建和测试

在仓库 `android/` 目录运行：

```powershell
.\gradlew.bat lintDebug testDebugUnitTest connectedDebugAndroidTest assembleDebug
```

界面测试需要已启动的 Android 模拟器或连接的测试设备。调试 APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。
