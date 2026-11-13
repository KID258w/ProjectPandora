# Android Demo 使用指南

按下面的步骤即可把原型运行到 Android 手机上。

## 1. 克隆项目

在终端执行：

```bash
git clone --branch dev --single-branch https://github.com/KID258w/ProjectPandora.git
```

项目会下载到当前目录下的 `ProjectPandora` 文件夹。

## 2. 安装并准备 Android Studio

安装 Android Studio 并首次启动。按照安装向导安装 Android SDK 等必要组件；如果 SDK 尚未安装完成，可在 Android Studio 的 SDK Manager 中补装项目要求的 SDK。虚拟设备（AVD）是可选的，本指南直接使用手机运行。

## 3. 在 Android Studio 打开 Demo

在 Android Studio 欢迎页选择 **Open**，打开克隆下来的项目中的 `ProjectPandora/demo` 文件夹。等待 Gradle Sync 完成。

## 4. 连接并识别手机

1. 在手机设置中开启开发者选项和 **USB 调试**。
2. 使用支持数据传输的 USB 线连接手机与电脑。
3. 手机弹出 USB 调试授权提示时，选择允许。
4. 在 Android Studio 顶部的设备选择器中确认手机已出现并被选中。

提示：Android Studio 的 **Device Manager** 主要用于管理虚拟设备（AVD）；已连接的实体手机通常显示在顶部设备选择器中。如果手机没有出现，检查 USB 调试授权和数据线；Windows 电脑还可能需要安装手机品牌对应的 USB 驱动。

## 5. 运行并安装

确认顶部运行配置为 **app**、设备选择为你的手机后，点击绿色 **Run（三角形）** 按钮。Android Studio 会自动执行 Gradle 构建，并将应用安装、启动到手机上。首次构建需要等待一段时间，并确保电脑可以下载 Gradle 和项目依赖。

安装完成后，在手机上即可体验 Demo 的首页、视图、日志、AI 地图和我的等页面。

## 常见情况

- **No target device found**：手机尚未被识别或未选中；检查 USB 调试、手机授权提示和数据线。
- **Gradle Sync / 构建失败**：检查网络，并确认 Android SDK 已按提示安装完成。
- **只想使用手机演示**：不需要创建 AVD；只有没有可用手机时才需要配置虚拟设备。

