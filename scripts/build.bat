@echo off
REM 演示构建：web/dist -> server 静态目录 -> 单个可执行 jar
setlocal
cd /d "%~dp0.."

call pnpm -C web install || exit /b 1
call pnpm -C web build || exit /b 1

set "STATIC_DIR=server\src\main\resources\static"
if exist "%STATIC_DIR%" rmdir /s /q "%STATIC_DIR%"
mkdir "%STATIC_DIR%"
xcopy /e /i /y "web\dist\*" "%STATIC_DIR%" >nul || exit /b 1

call mvn -f server\pom.xml clean package || exit /b 1

findstr /s /r /c:"sk-[A-Za-z0-9][A-Za-z0-9]*" "%STATIC_DIR%\*" >nul 2>&1
if not errorlevel 1 (
  echo 构建失败：前端产物中发现疑似密钥 1>&2
  exit /b 1
)

echo 构建完成：java -jar server\target\panel-studio.jar
