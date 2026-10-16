@echo off
setlocal enabledelayedexpansion

echo ================================================================================
echo    Starting Network Performance Analyzer
echo ================================================================================

cd /d "%~dp0.."

:: Check Java
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java is not installed or not found in PATH.
    echo Please install JDK 17 or higher.
    pause
    exit /b 1
)

:: Check if jar exists or build
if not exist "target\network-performance-analyzer-1.0.0.jar" (
    echo Building project and packaging executable JAR...
    call mvnw.cmd package -DskipTests
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

if not exist "logs" mkdir logs
if not exist "data" mkdir data

echo Starting Network Performance Analyzer in background...
start /b "" java -jar target\network-performance-analyzer-1.0.0.jar > logs\app.log 2>&1

:: Save PID if powershell available
powershell -Command "Get-CimInstance Win32_Process -Filter \"CommandLine LIKE '%%network-performance-analyzer%%'\" | Select-Object -ExpandProperty ProcessId | Out-File -Encoding ascii logs\app.pid"

timeout /t 2 /nobreak >nul

echo.
echo ================================================================================
echo   Network Performance Analyzer is RUNNING!
echo   Dashboard URL: http://localhost:8080/
echo   TCP Receiver : 127.0.0.1:9091
echo   UDP Receiver : 127.0.0.1:9092
echo   Logs File    : logs\app.log
echo ================================================================================
echo.
echo Opening dashboard in default web browser...
start http://localhost:8080/

endlocal
