@echo off
chcp 65001 >nul
title 知音听书 - 停止 H5 端

set "VM_IP=192.168.13.100"

echo ======================================================================
echo        知音听书 - H5 端关闭
echo ======================================================================
echo.

echo [1/2] 停止 5173 端口的 Vite 服务...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique; if ($p) { $p | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }; exit 0 } else { exit 1 }"
if errorlevel 1 (
    echo       未发现运行中的 H5 开发服务
) else (
    echo       [OK] H5 开发服务已停止
)

echo.
echo [2/2] 后端容器（小程序端与 H5 端共用）...
set "STOP_BACKEND="
set /p "STOP_BACKEND=是否同时停止后端容器？[y/N]: "
if /i "%STOP_BACKEND%"=="y" (
    ssh -o BatchMode=yes -o ConnectTimeout=10 root@%VM_IP% "cd /opt/tingshu-compose && docker compose down"
    if errorlevel 1 (
        echo [错误] 停止后端失败，请检查虚拟机 SSH 与 Docker。
    ) else (
        echo [OK] 后端容器已停止
    )
) else (
    echo       已保留后端容器运行
)

echo.
pause
