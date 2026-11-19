@echo off
REM 开发模式：后端 8080 + 前端 5173（Vite 代理 /api）。不使用容器。
setlocal
cd /d "%~dp0.."

if exist .env (
  for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    if not "%%a"=="" if not "%%a:~0,1%"=="#" set "%%a=%%b"
  )
)

REM spring-boot:run 会把 JVM 工作目录切到 server\，相对路径 SQLITE_PATH 会落到 server\data
REM 另起一个库：表现是「删了 data 里的库，页面还是旧数据」。这里统一按仓库根解析。
if not defined SQLITE_PATH set "SQLITE_PATH=%CD%\data\panel-studio.db"
if not "%SQLITE_PATH:~1,1%"==":" set "SQLITE_PATH=%CD%\data\panel-studio.db"

start "panel-studio-server" cmd /c "mvn -f server\pom.xml spring-boot:run"
call pnpm -C web install
call pnpm -C web dev
