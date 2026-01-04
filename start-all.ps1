# Server Management Console - Start All Services
# This script starts the backend and frontend services

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Server Management Console" -ForegroundColor Cyan
Write-Host "Starting all services..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if backend is already running
$backendRunning = $false
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/dashboard" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 200) {
        $backendRunning = $true
        Write-Host "[OK] Backend is already running on port 8080" -ForegroundColor Green
    }
} catch {
    $backendRunning = $false
}

# Check if frontend is already running
$frontendRunning = $false
try {
    $response = Invoke-WebRequest -Uri "http://localhost:3001" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 200) {
        $frontendRunning = $true
        Write-Host "[OK] Frontend is already running on port 3001" -ForegroundColor Green
    }
} catch {
    $frontendRunning = $false
}

Write-Host ""

# Start Backend if not running
if (-not $backendRunning) {
    Write-Host "Starting Backend (Spring Boot)..." -ForegroundColor Yellow
    Write-Host "  Will be available at http://localhost:8080" -ForegroundColor Gray
    
    $backendScript = "cd backend; mvn spring-boot:run"
    
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendScript
    Write-Host "  [OK] Backend starting in new window..." -ForegroundColor Green
    Start-Sleep -Seconds 3
} else {
    Write-Host "Backend already running, skipping..." -ForegroundColor Gray
}

Write-Host ""

# Check if frontend dependencies are installed
if (-not (Test-Path "frontend\node_modules")) {
    Write-Host "Installing frontend dependencies..." -ForegroundColor Yellow
    Set-Location frontend
    npm install
    Set-Location ..
    Write-Host "  [OK] Dependencies installed" -ForegroundColor Green
    Write-Host ""
}

# Start Frontend if not running
if (-not $frontendRunning) {
    Write-Host "Starting Frontend (React/Vite)..." -ForegroundColor Yellow
    Write-Host "  Will be available at http://localhost:3001" -ForegroundColor Gray
    
    $frontendScript = "cd frontend; npm run dev"
    
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendScript
    Write-Host "  [OK] Frontend starting in new window..." -ForegroundColor Green
    Start-Sleep -Seconds 2
} else {
    Write-Host "Frontend already running, skipping..." -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Services Status:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Backend:  http://localhost:8080" -ForegroundColor White
Write-Host "Frontend: http://localhost:3001" -ForegroundColor White
Write-Host ""
Write-Host "Note: Services are running in separate windows." -ForegroundColor Gray
Write-Host "      Close those windows to stop the services." -ForegroundColor Gray
Write-Host ""
Write-Host "Note: AI features use Google Gemini API (configured in application.yml)" -ForegroundColor Gray
Write-Host ""
Write-Host "All services are starting! Check the new windows for status." -ForegroundColor Green
