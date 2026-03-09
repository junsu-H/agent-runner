@echo off
setlocal EnableDelayedExpansion

echo.
echo ================================================
echo   Agent Runner — 설치 스크립트 (Windows)
echo ================================================
echo.

:: ─── 1. Java 25+ ───
echo [1/2] Java 25+ 확인

set JAVA_OK=0
where java >nul 2>nul
if %errorlevel%==0 (
    for /f "tokens=3 delims= " %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set "JAVA_RAW=%%~v"
    )
    for /f "tokens=1 delims=." %%m in ("!JAVA_RAW!") do set "JAVA_MAJOR=%%m"
    if !JAVA_MAJOR! GEQ 25 (
        echo   [OK] Java !JAVA_RAW! 이미 설치됨
        set JAVA_OK=1
    ) else (
        echo   [..] Java !JAVA_RAW! 감지됨 ^(25+ 필요^)
    )
) else (
    echo   [..] Java가 설치되어 있지 않습니다.
)

if !JAVA_OK!==0 (
    echo   [..] winget으로 Java 25 ^(Temurin^) 설치 중...
    winget install --id EclipseAdoptium.Temurin.25.JDK --accept-source-agreements --accept-package-agreements
    if !errorlevel!==0 (
        echo   [OK] Java 25 설치 완료. 터미널을 재시작해 주세요.
    ) else (
        echo   [!!] Java 설치 실패. https://adoptium.net 에서 수동 설치해 주세요.
    )
)

echo.

:: ─── 2. Node.js 20+ ───
echo [2/2] Node.js 20+ 확인

set NODE_OK=0
where node >nul 2>nul
if %errorlevel%==0 (
    for /f "tokens=1 delims=." %%v in ('node -v') do (
        set "NODE_RAW=%%v"
        set "NODE_MAJOR=!NODE_RAW:v=!"
    )
    if !NODE_MAJOR! GEQ 20 (
        echo   [OK] Node.js v!NODE_MAJOR! 이미 설치됨
        set NODE_OK=1
    ) else (
        echo   [..] Node.js v!NODE_MAJOR! 감지됨 ^(20+ 필요^)
    )
) else (
    echo   [..] Node.js가 설치되어 있지 않습니다.
)

if !NODE_OK!==0 (
    echo   [..] winget으로 Node.js LTS 설치 중...
    winget install --id OpenJS.NodeJS.LTS --accept-source-agreements --accept-package-agreements
    if !errorlevel!==0 (
        echo   [OK] Node.js 설치 완료. 터미널을 재시작해 주세요.
    ) else (
        echo   [!!] Node.js 설치 실패. https://nodejs.org 에서 수동 설치해 주세요.
    )
)

echo.

:: ─── 결과 요약 ───
echo ================================================
echo   설치 확인 완료
echo.
echo   다음 명령으로 실행하세요:
echo     start.bat
echo ================================================
echo.
pause
