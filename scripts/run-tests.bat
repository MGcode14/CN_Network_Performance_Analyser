@echo off
setlocal

echo ================================================================================
echo    Running Automated Tests for Network Performance Analyzer
echo ================================================================================

cd /d "%~dp0.."
call mvnw.cmd test

if %ERRORLEVEL% equ 0 (
    echo.
    echo ================================================================================
    echo    ALL TESTS PASSED SUCCESSFULLY!
    echo ================================================================================
) else (
    echo.
    echo ================================================================================
    echo    TESTS FAILED - PLEASE CHECK OUTPUT ABOVE
    echo ================================================================================
)

endlocal
exit /b %ERRORLEVEL%
