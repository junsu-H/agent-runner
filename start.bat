@echo off
setlocal

set ROOT_DIR=%~dp0

echo [agent-runner] Starting backend...
cd /d "%ROOT_DIR%backend"
start "agent-runner-backend" cmd /c "gradlew.bat bootRun --quiet"

echo [agent-runner] Starting frontend...
cd /d "%ROOT_DIR%frontend"
call npm install --silent 2>nul
start "agent-runner-frontend" cmd /c "npx vite --host"

echo.
echo ================================================
echo   Agent Runner
echo   Backend  : http://localhost:21293
echo   Frontend : http://localhost:5173
echo ================================================
echo.
echo Press any key to stop all servers...
pause >nul

echo [agent-runner] Shutting down...
taskkill /fi "WINDOWTITLE eq agent-runner-backend" /f >nul 2>nul
taskkill /fi "WINDOWTITLE eq agent-runner-frontend" /f >nul 2>nul
echo [agent-runner] Stopped.
