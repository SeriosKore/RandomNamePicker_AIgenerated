@echo off
title Delete AutoStart - RandomNamePicker

REM 设置 UTF-8 编码
chcp 65001 >nul

echo ========================================
echo   RandomNamePicker AutoStart Deleter
echo ========================================
echo.

echo [Step 1] Checking registry key...
pause

reg query "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "RandomNamePicker"
echo.
echo ErrorLevel: %ERRORLEVEL%
echo.

if %ERRORLEVEL% NEQ 0 (
    echo [INFO] No auto-start entry found!
    echo Program is not set to auto-start.
    pause
    exit /b 0
)

echo [Step 2] Found auto-start entry!
echo.
set /p confirm=Delete it? Press Y to confirm, any other key to cancel: 
echo.

if /i "%confirm%"=="Y" (
    echo [Step 3] Deleting registry entry...
    reg delete "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "RandomNamePicker" /f
    
    echo.
    if %ERRORLEVEL% EQU 0 (
        echo [SUCCESS] Auto-start removed successfully!
    ) else (
        echo [FAILED] Failed to delete! Error code: %ERRORLEVEL%
        echo.
        echo Possible solutions:
        echo 1. Right-click and run as Administrator
        echo 2. Check if antivirus is blocking
    )
) else (
    echo [CANCELLED] Operation cancelled by user.
)

echo.
echo ========================================
echo Press any key to exit...
pause >nul
