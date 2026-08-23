@echo off
chcp 65001 >nul
echo ========================================
echo   RandomNamePicker 打包脚本（生成 release）
echo ========================================

cd /d %~dp0
cd ..

REM 删除旧的输出
if exist out rmdir /s /q out
if exist release rmdir /s /q release
if exist RandomNamePicker.jar del RandomNamePicker.jar

mkdir out
mkdir release

echo [1/4] 编译 Java 文件...
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
    echo 编译失败！请确认已安装 JDK 并配置 PATH。
    pause
    exit /b 1
)

echo [2/4] 生成清单文件...
echo Main-Class: Main> manifest.txt

echo [3/4] 打包 JAR...
jar cfm RandomNamePicker.jar manifest.txt -C out .
if errorlevel 1 (
    echo JAR 打包失败！
    pause
    exit /b 1
)

echo [4/4] 生成 release 目录...
copy RandomNamePicker.jar release\RandomNamePicker.jar >nul
copy 示例名单.txt release\示例名单.txt >nul
copy README.md release\README.md >nul

echo.
echo 打包完成！release 目录内容：
dir /b release
echo.
pause
