@echo off
REM Wireless ADB connection script for Windows
REM Automatically connects to Android device via wireless ADB

setlocal enabledelayedexpansion

echo ========================================
echo Wireless ADB Connection
echo ========================================
echo.

REM Check if ADB is available
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: ADB not found in PATH
    echo Please ensure Android SDK platform-tools is installed and in PATH
    exit /b 1
)

REM Check if device is already connected
echo Checking for connected devices...
adb devices | findstr /R "device$" >nul
if %ERRORLEVEL% EQU 0 (
    echo Device already connected via ADB
    adb devices
    exit /b 0
)

REM Check for wireless connection
adb devices | findstr /R "[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*:[0-9][0-9]*" >nul
if %ERRORLEVEL% EQU 0 (
    echo Wireless device already connected
    adb devices
    exit /b 0
)

echo.
echo No device connected. Setting up wireless ADB...
echo.
echo Instructions:
echo 1. On your Titan 2 device:
echo    - Settings ^> About phone ^> Tap "Build number" 7 times
echo    - Settings ^> System ^> Developer options
echo    - Enable "Wireless debugging"
echo.
echo 2. In Wireless debugging:
echo    - Tap "Pair device with pairing code"
echo    - Note the IP address, port, and pairing code
echo.
echo 3. Enter the pairing information below:
echo.

set /p PAIR_IP="Enter pairing IP address (e.g., 192.168.1.100): "
set /p PAIR_PORT="Enter pairing port (e.g., 12345): "
set /p PAIR_CODE="Enter pairing code (6 digits): "

if "%PAIR_IP%"=="" (
    echo ERROR: IP address required
    exit /b 1
)
if "%PAIR_PORT%"=="" (
    echo ERROR: Port required
    exit /b 1
)
if "%PAIR_CODE%"=="" (
    echo ERROR: Pairing code required
    exit /b 1
)

echo.
echo Pairing device...
adb pair %PAIR_IP%:%PAIR_PORT%
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Pairing failed
    echo Please check:
    echo - Device is on the same WiFi network
    echo - Wireless debugging is enabled
    echo - IP address and port are correct
    exit /b 1
)

echo.
echo Pairing successful! Now connecting...
echo.
echo In Wireless debugging on your device, note the "IP address ^& Port"
echo (e.g., 192.168.1.100:12345)
echo.

set /p CONNECT_IP="Enter connection IP address: "
set /p CONNECT_PORT="Enter connection port: "

if "%CONNECT_IP%"=="" (
    echo ERROR: Connection IP required
    exit /b 1
)
if "%CONNECT_PORT%"=="" (
    echo ERROR: Connection port required
    exit /b 1
)

echo.
echo Connecting to %CONNECT_IP%:%CONNECT_PORT%...
adb connect %CONNECT_IP%:%CONNECT_PORT%
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Connection failed
    exit /b 1
)

echo.
echo Waiting for device...
timeout /t 2 /nobreak >nul

echo.
echo Connected devices:
adb devices

echo.
echo ========================================
echo Wireless ADB connection established!
echo ========================================
echo.
echo Tip: To reconnect later, just run:
echo   adb connect %CONNECT_IP%:%CONNECT_PORT%
echo.

endlocal

