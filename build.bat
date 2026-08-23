@echo off
echo Building RandomNamePicker Project...

cd /d %~dp0

REM 删除旧的输出
if exist out rmdir /s /q out
if exist RandomNamePicker.jar del RandomNamePicker.jar

REM 创建输出目录
mkdir out

REM 编译 Java 文件
echo Compiling Java files...
javac -encoding UTF-8 -d out src\*.java

if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

REM 打包成 JAR
echo Creating JAR file...
jar cfm RandomNamePicker.jar manifest.txt -C out .

if errorlevel 1 (
    echo JAR creation failed!
    pause
    exit /b 1
)

echo Build successful!
echo JAR file: %cd%\RandomNamePicker.jar
pause
