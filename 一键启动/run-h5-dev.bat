@echo off
chcp 65001 >nul
title 知音听书 - H5 前端开发服务（Vite）

cd /d "%~dp0..\tingshu-web"
if errorlevel 1 (
    echo [错误] 未找到 tingshu-web 目录。
    pause & exit /b 1
)
if not exist "node_modules" (
    echo [提示] 未找到 node_modules，请先在本目录执行：
    echo        npm install --registry=https://registry.npmmirror.com
    pause & exit /b 1
)

echo ======================================================================
echo   知音听书 H5 开发服务
echo   地址：http://localhost:5173
echo   关闭本窗口即停止服务
echo ======================================================================
echo.
npm run dev
pause
