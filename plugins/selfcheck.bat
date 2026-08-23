@echo off
chcp 65001 >nul
echo ========================================
echo   RandomNamePicker 自检脚本
echo ========================================

cd /d %~dp0
cd ..

if not exist RandomNamePicker.jar (
    echo [错误] 未找到 RandomNamePicker.jar，请先运行 plugins\package.bat 完成打包。
    pause
    exit /b 1
)

REM 在临时目录中运行，避免污染仓库数据
set TESTDIR=%TEMP%\RNP_selftest_%RANDOM%
mkdir "%TESTDIR%"

echo [1/3] 编译自检类...
javac -encoding UTF-8 -cp RandomNamePicker.jar -d "%TESTDIR%" plugins\selftest\SelfCheck.java
if errorlevel 1 (
    echo 自检类编译失败！
    rmdir /s /q "%TESTDIR%"
    pause
    exit /b 1
)

echo [2/3] 打包自检类...
jar cfe "%TESTDIR%\selftest.jar" SelfCheck -C "%TESTDIR%" SelfCheck.class
if errorlevel 1 (
    echo 自检类打包失败！
    rmdir /s /q "%TESTDIR%"
    pause
    exit /b 1
)

echo [3/3] 运行自检...
cd /d "%TESTDIR%"
java -cp "%cd%\selftest.jar;%~dp0..\RandomNamePicker.jar" SelfCheck
set EXITCODE=%ERRORLEVEL%

cd /d %~dp0
cd ..
rmdir /s /q "%TESTDIR%"

echo.
if %EXITCODE% EQU 0 (
    echo 自检通过！
) else (
    echo 自检未通过，请检查上方输出。
)
pause
exit /b %EXITCODE%
