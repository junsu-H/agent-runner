@echo off
setlocal

set ROOT_DIR=%~dp0

:: Kill existing processes on required ports
echo [agent-runner] Checking for existing processes...
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 19876 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host '[agent-runner] Killing PID' $_.OwningProcess 'on port 19876'; Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 15432 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host '[agent-runner] Killing PID' $_.OwningProcess 'on port 15432'; Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
timeout /t 2 /nobreak >nul

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
echo   Backend  : http://localhost:19876
echo   Frontend : http://localhost:15432
echo ================================================
echo.
echo Press any key to stop all servers...
pause >nul

echo [agent-runner] Shutting down...
taskkill /fi "WINDOWTITLE eq agent-runner-backend" /f >nul 2>nul
taskkill /fi "WINDOWTITLE eq agent-runner-frontend" /f >nul 2>nul
echo [agent-runner] Stopped.
