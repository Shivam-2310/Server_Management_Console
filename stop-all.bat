@echo off
echo ========================================
echo Server Management Console
echo Stopping all services...
echo ========================================
echo.

REM Check if backend is running
curl -s http://localhost:8080/api/dashboard >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Backend is running on port 8080
    set BACKEND_RUNNING=1
) else (
    echo [INFO] Backend is not running on port 8080
    set BACKEND_RUNNING=0
)

REM Check if frontend is running
curl -s http://localhost:3001 >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Frontend is running on port 3001
    set FRONTEND_RUNNING=1
) else (
    echo [INFO] Frontend is not running on port 3001
    set FRONTEND_RUNNING=0
)

echo.

REM Stop Backend if running
if %BACKEND_RUNNING% equ 1 (
    echo Stopping Backend (Spring Boot)...
    
    REM Find and kill processes on port 8080
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do (
        echo   Stopping process on port 8080 (PID: %%a)
        taskkill /F /PID %%a >nul 2>&1
    )
    
    REM Note: Killing all Java processes is too aggressive
    REM Only processes on port 8080 are killed above
    
    timeout /t 2 /nobreak >nul
    
    REM Verify backend is stopped
    curl -s http://localhost:8080/api/dashboard >nul 2>&1
    if %errorlevel% equ 0 (
        echo   [WARNING] Backend may still be running
    ) else (
        echo   [OK] Backend stopped successfully
    )
) else (
    echo Backend is not running, skipping...
)

echo.

REM Stop Frontend if running
if %FRONTEND_RUNNING% equ 1 (
    echo Stopping Frontend (React/Vite)...
    
    REM Find and kill processes on port 3001
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr :3001 ^| findstr LISTENING') do (
        echo   Stopping process on port 3001 (PID: %%a)
        taskkill /F /PID %%a >nul 2>&1
    )
    
    REM Note: Killing all Node processes is too aggressive
    REM Only processes on port 3001 are killed above
    
    timeout /t 2 /nobreak >nul
    
    REM Verify frontend is stopped
    curl -s http://localhost:3001 >nul 2>&1
    if %errorlevel% equ 0 (
        echo   [WARNING] Frontend may still be running
    ) else (
        echo   [OK] Frontend stopped successfully
    )
) else (
    echo Frontend is not running, skipping...
)

echo.
echo ========================================
echo Services Status:
echo ========================================
echo.

REM Final status check
curl -s http://localhost:8080/api/dashboard >nul 2>&1
if %errorlevel% equ 0 (
    echo Backend:  STILL RUNNING (port 8080)
) else (
    echo Backend:  STOPPED
)

curl -s http://localhost:3001 >nul 2>&1
if %errorlevel% equ 0 (
    echo Frontend: STILL RUNNING (port 3001)
) else (
    echo Frontend: STOPPED
)

echo.
echo All services have been stopped!
echo.
pause

