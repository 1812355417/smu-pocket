@echo off
rem 跑单元测试（49 项：周次换算 / 周次串解析 / JSON 解析 / 电费 0 值陷阱）
setlocal
set "PROJ=%~dp0"
set "JAVA_HOME=%PROJ%.toolchain\jdk\jdk-17.0.2"
set "ANDROID_HOME=%PROJ%.toolchain\sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

call "%PROJ%gradlew.bat" testDebugUnitTest %*
echo.
echo 报告: app\build\reports\tests\testDebugUnitTest\index.html
endlocal
