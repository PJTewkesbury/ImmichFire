@echo off
setlocal

rem Install ImmichFire APK onto a Fire TV / Android device over ADB.
rem Usage:
rem   install.bat          - installs debug APK (default)
rem   install.bat debug    - installs debug APK
rem   install.bat release  - installs release APK

cd /d "%~dp0"

set DEVICE=192.168.0.236:5555

rem --- Locate adb ------------------------------------------------------------
set "ADB="
if exist "E:\AndroidSDK\platform-tools\adb.exe" set "ADB=E:\AndroidSDK\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB (
    where adb >nul 2>nul
    if %errorlevel%==0 set "ADB=adb"
)
if not defined ADB (
    echo ERROR: adb not found. Install Android platform-tools or set ANDROID_HOME.
    exit /b 1
)

set VARIANT=%1
if "%VARIANT%"=="" set VARIANT=debug

if /i "%VARIANT%"=="debug" (
    set "APK=app\build\outputs\apk\debug\app-debug.apk"
) else if /i "%VARIANT%"=="release" (
    set "APK=app\build\outputs\apk\release\app-release-unsigned.apk"
) else (
    echo ERROR: unknown variant "%VARIANT%" ^(use debug or release^).
    exit /b 1
)

if not exist "%APK%" (
    echo ERROR: APK not found at "%APK%".
    echo Build it first with: build.bat %VARIANT%
    exit /b 1
)

echo Connecting to %DEVICE% ...
"%ADB%" connect %DEVICE%
if errorlevel 1 (
    echo ERROR: adb connect failed.
    exit /b 1
)

echo.
echo Installing %APK% ...
"%ADB%" -s %DEVICE% install -r -d "%APK%"
if errorlevel 1 (
    echo.
    echo Install failed. If this is a signature mismatch, uninstall first:
    echo   "%ADB%" -s %DEVICE% uninstall com.intec.immichfire
    exit /b 1
)

echo.
echo Install complete.
endlocal
