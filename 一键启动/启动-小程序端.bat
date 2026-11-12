@echo off
chcp 65001 >nul
title 知音听书 - 启动小程序端（后端容器 + 微信开发者工具）

set "REPO=%~dp0.."
set "VM_IP=192.168.13.100"
set "VMRUN=E:\VMware\vmrun.exe"
set "VMX=E:\linux_xuniji\Red Hat Enterprise Linux 7 64 位.vmx"
set "WECHAT=D:\微信web开发者工具\wechatdevtools.exe"

echo ======================================================================
echo        知音听书 - 小程序端一键启动
echo        后端：虚拟机 Docker Compose 容器集群
echo        前端：微信开发者工具（mp-weixin）
echo ======================================================================
echo.

echo [1/3] 检查虚拟机 %VM_IP% ...
ssh -o BatchMode=yes -o ConnectTimeout=5 root@%VM_IP% "echo ok" >nul 2>&1
if not errorlevel 1 goto vm_ready

echo       虚拟机未响应，尝试后台唤醒...
if not exist "%VMRUN%" (
    echo [错误] 未找到 VMware vmrun：%VMRUN%
    pause & exit /b 1
)
"%VMRUN%" start "%VMX%" nogui >nul 2>&1
for /l %%i in (1,1,45) do (
    timeout /t 2 >nul
    ssh -o BatchMode=yes -o ConnectTimeout=5 root@%VM_IP% "echo ok" >nul 2>&1 && goto vm_ready
)
echo [错误] 虚拟机 90 秒内未就绪，请手动打开 VMware 后重试。
pause & exit /b 1

:vm_ready
echo       [OK] 虚拟机已就绪

echo.
echo [2/3] 启动后端 9 个微服务容器...
ssh -o BatchMode=yes -o ConnectTimeout=10 root@%VM_IP% "docker update --restart unless-stopped seata-server >/dev/null 2>&1 || true; docker start seata-server >/dev/null 2>&1 || true; cd /opt/tingshu-compose && docker compose up -d"
if errorlevel 1 (
    echo [错误] docker compose up 失败，请检查虚拟机 Docker 与 /opt/tingshu-compose。
    pause & exit /b 1
)
ssh -o BatchMode=yes -o ConnectTimeout=10 root@%VM_IP% "docker start tingshu-nginx >/dev/null 2>&1 || true" >nul 2>&1

echo       等待网关就绪（最多 90 秒）...
set "GW_READY="
for /l %%i in (1,1,45) do (
    curl -s -f -o nul --max-time 3 http://%VM_IP%:8500/api/album/category/findAllCategory1 >nul 2>&1 && (set "GW_READY=1" & goto gw_ready)
    timeout /t 2 >nul
)
:gw_ready
if defined GW_READY (
    echo       [OK] 网关 8500 已响应
) else (
    echo       [警告] 网关 90 秒内未响应，前端可能报错；可稍后在“容器状态”中查看。
)

echo.
echo [3/3] 打开微信开发者工具...
if not exist "%WECHAT%" (
    echo [警告] 未找到微信开发者工具：%WECHAT%
    echo        请手动打开并导入：%REPO%\mp-weixin
) else (
    start "" "%WECHAT%" "%REPO%\mp-weixin"
    echo       [OK] 微信开发者工具已启动
)

echo.
echo ======================================================================
echo   小程序端已启动
echo   接口地址：http://%VM_IP%/api/...
echo   网关直连：http://%VM_IP%:8500
echo ======================================================================
pause
