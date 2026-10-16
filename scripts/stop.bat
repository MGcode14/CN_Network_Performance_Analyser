@echo off
setlocal enabledelayedexpansion

echo ================================================================================
echo    Stopping Network Performance Analyzer
echo ================================================================================

cd /d "%~dp0.."

set STOPPED=0

if exist "logs\app.pid" (
    for /f "tokens=*" %%p in (logs\app.pid) do (
        if not "%%p"=="" (
            echo Terminating process PID %%p...
            taskkill /F /PID %%p >nul 2>&1
            if !ERRORLEVEL! equ 0 (
                set STOPPED=1
            )
        )
    )
    del /f /q logs\app.pid >nul 2>&1
)

:: Fallback: find process by listening port 8080 or command line
powershell -Command "Get-CimInstance Win32_Process -Filter \"CommandLine LIKE '%%network-performance-analyzer%%'\" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Host \"Stopped analyzer process $($_.ProcessId)\" }"

echo Network Performance Analyzer stopped.
endlocal
