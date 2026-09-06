@echo off
echo Building RandomNamePicker Project...

cd /d "%~dp0.."

REM --- remove old outputs ---
if exist out rmdir /s /q out
if exist RandomNamePicker.jar del RandomNamePicker.jar

REM --- create output dir ---
mkdir out

REM --- collect all sources recursively (method A: argfile) ---
echo Collecting source files...
dir /s /b src\*.java > sources.txt
echo Compiling Java files...
javac -encoding UTF-8 -d out @sources.txt

if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

REM --- cleanup temp argfile ---
if exist sources.txt del sources.txt

REM --- package JAR ---
echo Creating JAR file...
jar cfm RandomNamePicker.jar manifest.txt -C out .

if errorlevel 1 (
    echo JAR creation failed!
    pause
    exit /b 1
)

echo Build successful!
echo JAR file: %cd%\RandomNamePicker_new.jar
pause
