@echo off
chcp 65001 >nul
echo ========================================
echo   屏幕画笔插件构建脚本（PenPlugin）
echo ========================================

cd /d %~dp0

if not exist ..\..\RandomNamePicker.jar (
    echo [错误] 未找到 RandomNamePicker.jar，请先在项目根目录运行 build.bat 完成主程序打包。
    pause
    exit /b 1
)

if not exist lib\jna-5.14.0.jar (
    echo [错误] 缺少 JNA 依赖，请下载到 lib\ 目录：
    echo   https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar
    echo   https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/5.14.0/jna-platform-5.14.0.jar
    pause
    exit /b 1
)

if exist classes rmdir /s /q classes
mkdir classes

echo [1/4] 解压 JNA 依赖（打 fat JAR）...
pushd classes
jar xf ..\lib\jna-5.14.0.jar
jar xf ..\lib\jna-platform-5.14.0.jar
popd

echo [2/4] 编译插件...
javac -encoding UTF-8 -cp ..\..\RandomNamePicker.jar;lib\jna-5.14.0.jar;lib\jna-platform-5.14.0.jar -d classes PenPlugin.java PenDock.java PenOverlayWindow.java PenNative.java PenStroke.java PenStrokeRenderer.java
if errorlevel 1 (
    echo 插件编译失败！
    pause
    exit /b 1
)

echo [3/4] 打包 fat JAR（含 Plugin-Class 清单属性）...
echo Plugin-Class: PenPlugin> manifest.txt
jar cfm PenPlugin.jar manifest.txt -C classes .

echo [4/4] 复制到 extensions\ 目录...
if not exist ..\..\extensions mkdir ..\..\extensions
copy /y PenPlugin.jar ..\..\extensions\PenPlugin.jar >nul

echo.
echo 构建完成！重启程序后插件将自动加载。
pause
