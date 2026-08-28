@echo off
chcp 65001 >nul
echo ========================================
echo   示例插件构建脚本（ExamplePlugin）
echo ========================================

cd /d %~dp0

if not exist ..\..\RandomNamePicker.jar (
    echo [错误] 未找到 RandomNamePicker.jar，请先在项目根目录运行 build.bat 完成主程序打包。
    pause
    exit /b 1
)

if exist classes rmdir /s /q classes
mkdir classes

echo [1/3] 编译插件...
javac -encoding UTF-8 -cp ..\..\RandomNamePicker.jar -d classes ExamplePlugin.java
if errorlevel 1 (
    echo 插件编译失败！
    pause
    exit /b 1
)

echo [2/3] 打包插件 JAR（含 Plugin-Class 清单属性）...
echo Plugin-Class: ExamplePlugin> manifest.txt
jar cfm ExamplePlugin.jar manifest.txt -C classes .

echo [3/3] 复制到 extensions\ 目录...
if not exist ..\..\extensions mkdir ..\..\extensions
copy /y ExamplePlugin.jar ..\..\extensions\ExamplePlugin.jar >nul

echo.
echo 构建完成！重启程序后插件将自动加载。
pause
