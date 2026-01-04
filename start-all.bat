@echo off
echo ========================================
echo Server Management Console
echo Starting all services...
echo ========================================
echo.

REM Check if backend is running
curl -s http://localhost:8080/api/dashboard >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Backend is already running on port 8080
    set BACKEND_RUNNING=1
) else (
    set BACKEND_RUNNING=0
)

REM Check if frontend is running
curl -s http://localhost:3001 >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Frontend is already running on port 3001
    set FRONTEND_RUNNING=1
) else (
    set FRONTEND_RUNNING=0
)

echo.

REM Start Backend if not running
if %BACKEND_RUNNING% equ 0 (
    echo Starting Backend (Spring Boot)...
    echo   - Will be available at http://localhost:8080
    start "Backend - Server Management Console" cmd /k "cd backend && mvn spring-boot:run"
    echo   [OK] Backend starting in new window...
    timeout /t 3 /nobreak >nul
) else (
    echo Backend already running, skipping...
)

echo.

REM Check if frontend dependencies are installed
if not exist "frontend\node_modules" (
    echo Installing frontend dependencies...
    cd frontend
    call npm install
    cd ..
    echo   [OK] Dependencies installed
    echo.
)

REM Start Frontend if not running
if %FRONTEND_RUNNING% equ 0 (
    echo Starting Frontend (React/Vite)...
    echo   - Will be available at http://localhost:3001
    start "Frontend - Server Management Console" cmd /k "cd frontend && npm run dev"
    echo   [OK] Frontend starting in new window...
    timeout /t 2 /nobreak >nul
) else (
    echo Frontend already running, skipping...
)

echo.
echo ========================================
echo Services Status:
echo ========================================
echo.
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:3001
echo.
echo Note: Services are running in separate windows.
echo       Close those windows to stop the services.
echo.
echo Note: AI features use Google Gemini API (configured in application.yml)
echo.
pause

