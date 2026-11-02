@echo off
rem 一键编译 debug APK（用工程内自带的 JDK 17 + Gradle 8.9 + SDK，不依赖系统环境）
setlocal
set "PROJ=%~dp0"
set "JAVA_HOME=%PROJ%.toolchain\jdk\jdk-17.0.2"
set "ANDROID_HOME=%PROJ%.toolchain\sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [错误] 找不到内置 JDK: %JAVA_HOME%
  echo 请确认 .toolchain 目录没被删掉
  exit /b 1
)

echo JDK    = %JAVA_HOME%
echo SDK    = %ANDROID_HOME%
echo.
call "%PROJ%gradlew.bat" assembleDebug %*
echo.
if exist "%PROJ%app\build\outputs\apk\debug\app-debug.apk" (
  echo [OK] APK: app\build\outputs\apk\debug\app-debug.apk
) else (
  echo [失败] 没有生成 APK，看上面的报错
)
endlocal
