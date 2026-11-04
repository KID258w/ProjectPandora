#!/usr/bin/env bash
set -euo pipefail

BACKEND_HEALTH_URL="http://127.0.0.1:8000/api/health/"
ADB="${ADB:-}"

if [[ -z "$ADB" ]]; then
    if command -v adb >/dev/null 2>&1; then
        ADB="$(command -v adb)"
    elif [[ -x "/d/Android/SDK/platform-tools/adb.exe" ]]; then
        ADB="/d/Android/SDK/platform-tools/adb.exe"
    else
        echo "找不到 adb。请安装 Android SDK Platform Tools，或设置 ADB 环境变量。" >&2
        exit 1
    fi
fi

if ! curl --fail --silent --show-error --max-time 3 "$BACKEND_HEALTH_URL" >/dev/null; then
    echo "电脑上的后端未响应：$BACKEND_HEALTH_URL" >&2
    echo "请先在 backend 目录启动 Django：python manage.py runserver" >&2
    exit 1
fi

"$ADB" start-server >/dev/null

connected=0
while IFS=$' \t' read -r serial state _; do
    [[ -z "${serial:-}" || "$serial" == "List" ]] && continue
    if [[ "${state:-}" != "device" ]]; then
        echo "跳过 $serial（ADB 状态：${state:-未知}）" >&2
        continue
    fi

    if "$ADB" -s "$serial" reverse tcp:8000 tcp:8000 >/dev/null; then
        echo "已连接 $serial：设备 127.0.0.1:8000 -> 电脑 127.0.0.1:8000"
        connected=$((connected + 1))
    fi
done < <("$ADB" devices | tr -d '\r')

if (( connected == 0 )); then
    echo "没有找到已授权的手机或模拟器。请连接设备并允许 USB 调试后重试。" >&2
    exit 1
fi

echo "后端健康检查通过；共设置 $connected 台设备。"
