@echo off
chcp 65001 >nul
title 添加 RandomNamePicker 开机自启动

echo ========================================
echo   RandomNamePicker 开机自启动设置工具
echo ========================================
echo.

REM 获取当前脚本所在目录
set SCRIPT_DIR=%~dp0
set EXE_PATH=%SCRIPT_DIR%RandomNamePicker.exe

REM 检查 exe 文件是否存在
if not exist "%EXE_PATH%" (
    echo [错误] 未找到程序文件：%EXE_PATH%
    echo.
    echo 请确保此脚本与 RandomNamePicker.exe 在同一目录下
    pause
    exit /b 1
)

echo [信息] 程序路径：%EXE_PATH%
echo.

REM 设置注册表项
echo [操作] 正在添加注册表项...
reg add "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "RandomNamePicker" /t REG_SZ /d "\"%EXE_PATH%\"" /f

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [成功] 开机自启动已启用！
    echo.
    echo 注册表位置：HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\CurrentVersion\Run
    echo 程序名称：RandomNamePicker
    echo 程序路径：%EXE_PATH%
    echo.
    echo 重启电脑后程序将自动启动
) else (
    echo.
    echo [失败] 设置注册表失败，错误代码：%ERRORLEVEL%
    echo.
    echo 可能的原因：
    echo 1. 权限不足（请右键以管理员身份运行）
    echo 2. 安全软件拦截
    echo 3. 系统策略限制
)

echo.
pause
