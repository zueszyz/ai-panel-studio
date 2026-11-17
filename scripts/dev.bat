@echo off
REM 开发模式：后端 8080 + 前端 5173（Vite 代理 /api）。不使用容器。
setlocal
cd /d "%~dp0.."

if exist .env (
  for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    if not "%%a"=="" if not "%%a:~0,1%"=="#" set "%%a=%%b"
  )
)

start "panel-studio-server" cmd /c "mvn -f server\pom.xml spring-boot:run"
call pnpm -C web install
call pnpm -C web dev
