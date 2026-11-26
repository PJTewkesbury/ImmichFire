@echo off
setlocal

rem Build ImmichFire APKs (debug + unsigned release) from the command line.
rem Usage:
rem   build.bat           - builds both debug and release
rem   build.bat debug     - builds debug only
rem   build.bat release   - builds unsigned release only
rem   build.bat clean     - cleans build outputs

cd /d "%~dp0"

rem --- Locate a JDK if JAVA_HOME is not set -----------------------------------
if not defined JAVA_HOME (
    if exist "E:\AndroidStudio\jbr\bin\java.exe" (
        set "JAVA_HOME=E:\AndroidStudio\jbr"
    ) else if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
    ) else if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    )
)

if not defined JAVA_HOME (
    echo ERROR: JAVA_HOME is not set and no bundled JDK was found.
    echo Install a JDK 17+ or point JAVA_HOME at Android Studio's jbr folder.
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: JAVA_HOME is set to "%JAVA_HOME%" but bin\java.exe is missing.
    exit /b 1
)

echo Using JAVA_HOME=%JAVA_HOME%

set TASK=%1
if "%TASK%"=="" set TASK=all

if /i "%TASK%"=="clean" (
    call gradlew.bat clean
    goto :end
)

if /i "%TASK%"=="debug" (
    call gradlew.bat assembleDebug --stacktrace
    goto :show
)

if /i "%TASK%"=="release" (
    call gradlew.bat assembleRelease --stacktrace
    goto :show
)

call gradlew.bat assembleDebug assembleRelease --stacktrace

:show
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b %errorlevel%
)
echo.
echo BUILD SUCCESSFUL
echo.
echo APK output:
if exist "app\build\outputs\apk\debug"   dir /b "app\build\outputs\apk\debug\*.apk"   2>nul
if exist "app\build\outputs\apk\release" dir /b "app\build\outputs\apk\release\*.apk" 2>nul

:end
endlocal
