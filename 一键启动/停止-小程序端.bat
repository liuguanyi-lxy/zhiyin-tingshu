@echo off
chcp 65001 >nul
title 知音听书 - 停止小程序端

set "VM_IP=192.168.13.100"

echo ======================================================================
echo        知音听书 - 小程序端关闭
echo ======================================================================
echo.

echo [1/2] 关闭微信开发者工具...
tasklist /FI "IMAGENAME eq wechatdevtools.exe" 2>nul | findstr /I "wechatdevtools.exe" >nul
if errorlevel 1 (
    echo       微信开发者工具未在运行
) else (
    taskkill /IM wechatdevtools.exe /T /F >nul 2>&1
    echo       [OK] 微信开发者工具已关闭
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
