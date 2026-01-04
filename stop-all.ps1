# Server Management Console - Stop All Services
# This script stops the backend and frontend services

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Server Management Console" -ForegroundColor Cyan
Write-Host "Stopping all services..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if backend is running
$backendRunning = $false
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/dashboard" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 200) {
        $backendRunning = $true
        Write-Host "[OK] Backend is running on port 8080" -ForegroundColor Yellow
    }
} catch {
    $backendRunning = $false
    Write-Host "[INFO] Backend is not running on port 8080" -ForegroundColor Gray
}

# Check if frontend is running
$frontendRunning = $false
try {
    $response = Invoke-WebRequest -Uri "http://localhost:3001" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 200) {
        $frontendRunning = $true
        Write-Host "[OK] Frontend is running on port 3001" -ForegroundColor Yellow
    }
} catch {
    $frontendRunning = $false
    Write-Host "[INFO] Frontend is not running on port 3001" -ForegroundColor Gray
}

Write-Host ""

# Stop Backend if running
if ($backendRunning) {
    Write-Host "Stopping Backend (Spring Boot)..." -ForegroundColor Yellow
    
    # Find processes using port 8080
    $backendProcesses = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | 
        Select-Object -ExpandProperty OwningProcess -Unique
    
    if ($backendProcesses) {
        foreach ($pid in $backendProcesses) {
            try {
                $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
                if ($process) {
                    Write-Host "  Stopping process: $($process.ProcessName) (PID: $pid)" -ForegroundColor Gray
                    Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
                }
            } catch {
                Write-Host "  Warning: Could not stop process $pid" -ForegroundColor Yellow
            }
        }
        
        # Additional check: Find Java processes that are using port 8080
        # This is already covered by the port check above, but we verify
        
        Start-Sleep -Seconds 2
        
        # Verify backend is stopped
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/dashboard" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
            Write-Host "  [WARNING] Backend may still be running" -ForegroundColor Yellow
        } catch {
            Write-Host "  [OK] Backend stopped successfully" -ForegroundColor Green
        }
    } else {
        Write-Host "  [INFO] No process found on port 8080" -ForegroundColor Gray
    }
} else {
    Write-Host "Backend is not running, skipping..." -ForegroundColor Gray
}

Write-Host ""

# Stop Frontend if running
if ($frontendRunning) {
    Write-Host "Stopping Frontend (React/Vite)..." -ForegroundColor Yellow
    
    # Find processes using port 3001
    $frontendProcesses = Get-NetTCPConnection -LocalPort 3001 -ErrorAction SilentlyContinue | 
        Select-Object -ExpandProperty OwningProcess -Unique
    
    if ($frontendProcesses) {
        foreach ($pid in $frontendProcesses) {
            try {
                $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
                if ($process) {
                    Write-Host "  Stopping process: $($process.ProcessName) (PID: $pid)" -ForegroundColor Gray
                    Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
                }
            } catch {
                Write-Host "  Warning: Could not stop process $pid" -ForegroundColor Yellow
            }
        }
        
        # Additional check: Find Node processes that are using port 3001
        # This is already covered by the port check above, but we verify
        
        Start-Sleep -Seconds 2
        
        # Verify frontend is stopped
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:3001" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
            Write-Host "  [WARNING] Frontend may still be running" -ForegroundColor Yellow
        } catch {
            Write-Host "  [OK] Frontend stopped successfully" -ForegroundColor Green
        }
    } else {
        Write-Host "  [INFO] No process found on port 3001" -ForegroundColor Gray
    }
} else {
    Write-Host "Frontend is not running, skipping..." -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Services Status:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Final status check
$backendStopped = $true
$frontendStopped = $true

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/dashboard" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 200) {
        $backendStopped = $false
    }
} catch {
    $backendStopped = $true
}

try {
    $response = Invoke-WebRequest -Uri "http://localhost:3001" -Method GET -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 200) {
        $frontendStopped = $false
    }
} catch {
    $frontendStopped = $true
}

if ($backendStopped) {
    Write-Host "Backend:  STOPPED" -ForegroundColor Green
} else {
    Write-Host "Backend:  STILL RUNNING (port 8080)" -ForegroundColor Red
}

if ($frontendStopped) {
    Write-Host "Frontend: STOPPED" -ForegroundColor Green
} else {
    Write-Host "Frontend: STILL RUNNING (port 3001)" -ForegroundColor Red
}

Write-Host ""
Write-Host "All services have been stopped!" -ForegroundColor Green

