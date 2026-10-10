@echo off
REM ==============================================================================
REM WireFM - Mount WebDAV Volume as Network Drive in Windows Explorer
REM Usage: mount_windows.bat [IP] [PORT]
REM ==============================================================================

setlocal enabledelayedexpansion

set IP=%1
set PORT=%2
if "%PORT%"=="" set PORT=8081

if "%IP%"=="" (
    set /p IP="Enter WireFM IP Address (e.g. 192.168.1.100): "
)

if "%IP%"=="" (
    echo [ERROR] IP address cannot be empty.
    pause
    exit /b 1
)

echo.
echo [INFO] Starting Windows WebClient service...
sc start WebClient >nul 2>&1

set DRIVE_LETTER=Z:

echo [INFO] Disconnecting any previous drive on %DRIVE_LETTER%...
net use %DRIVE_LETTER% /delete /yes >nul 2>&1

echo [INFO] Mounting WireFM at http://%IP%:%PORT%/ as %DRIVE_LETTER%...
net use %DRIVE_LETTER% "http://%IP%:%PORT%/" /persistent:no

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [SUCCESS] WireFM successfully mounted as %DRIVE_LETTER%!
    echo [INFO] Opening Windows Explorer...
    start explorer.exe %DRIVE_LETTER%
) else (
    echo.
    echo [WARNING] Could not mount drive letter. Opening WebDAV URL in default browser...
    start http://%IP%:%PORT%/
)

pause
