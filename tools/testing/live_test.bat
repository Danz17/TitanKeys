@echo off
REM Complete live testing workflow for TitanKeys
REM Builds, installs, and launches the app with wireless ADB support

setlocal enabledelayedexpansion

echo ========================================
echo TitanKeys Live Testing Workflow
echo ========================================
echo.

REM Check if ADB is available
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: ADB not found in PATH
    echo Please ensure Android SDK platform-tools is installed
    exit /b 1
)

REM Try to connect wirelessly if not already connected
echo [1/5] Checking ADB connection...
adb devices | findstr /R "device$" >nul
if %ERRORLEVEL% NEQ 0 (
    echo No device connected. Attempting wireless connection...
    call tools\adb\wireless_adb_connect.bat
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo WARNING: Could not establish wireless connection
        echo Continuing with build (you can install manually later)...
    )
) else (
    echo Device already connected
    adb devices
)

echo.
echo [2/5] Building APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed
    exit /b %ERRORLEVEL%
)

echo.
echo [3/5] Installing to device...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Installation failed
    echo Make sure device is connected and USB debugging is enabled
    exit /b %ERRORLEVEL%
)

echo.
echo [4/5] Launching app...
adb shell am start -n com.titankeys.keyboard/.MainActivity
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo WARNING: Could not launch app automatically
    echo Please launch manually from the device
)

echo.
echo [5/5] Starting logcat (Press Ctrl+C to stop)...
echo.
echo ========================================
echo Logcat Output (filtered for TitanKeys)
echo ========================================
echo.
adb logcat -c
adb logcat | findstr /I "TitanKeys SuggestionController DictionaryRepo NgramLanguageModel ClipboardHistoryManager"

endlocal

