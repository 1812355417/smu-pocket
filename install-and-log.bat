@echo off
rem 装到已连接的手机，并实时过滤冒烟测试日志（tag = JwSmoke）
rem 前提：手机已开「开发者选项 → USB 调试」，且数据线连着
setlocal
set "PROJ=%~dp0"
set "ADB=%PROJ%.toolchain\sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
  echo [错误] 找不到 adb: %ADB%
  exit /b 1
)

echo === 已连接设备 ===
"%ADB%" devices
echo.

echo === 安装 APK ===
"%ADB%" install -r "%PROJ%app\build\outputs\apk\debug\app-debug.apk"
echo.

echo === 启动 App ===
"%ADB%" shell am start -n com.smu.pocket/.MainActivity
echo.

echo === 日志（Ctrl+C 退出）===
"%ADB%" logcat -c
"%ADB%" logcat -s JwSmoke:D System.err:W AndroidRuntime:E
endlocal
