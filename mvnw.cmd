@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Script for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "DIR=%~dp0"
set "MAVEN_CMD=%DIR%.mvn\maven\apache-maven-3.9.6\bin\mvn.cmd"

if not exist "%MAVEN_CMD%" (
    echo Maven binary not found in wrapper directory. Setting up Maven...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%DIR%scripts\setup-maven.ps1"
)

if exist "%MAVEN_CMD%" (
    "%MAVEN_CMD%" %*
    exit /b %ERRORLEVEL%
) else (
    echo Error: Failed to find or configure Maven executable.
    exit /b 1
)
