#!/bin/bash
# Complete live testing workflow for TitanKeys
# Builds, installs, and launches the app with wireless ADB support

set -e

echo "========================================"
echo "TitanKeys Live Testing Workflow"
echo "========================================"
echo ""

# Find ADB path
ADB_PATH=""
if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB_PATH="$HOME/Library/Android/sdk/platform-tools/adb"
elif [ -f "./local.properties" ]; then
    SDK_DIR=$(grep "sdk.dir" ./local.properties | cut -d'=' -f2)
    if [ -f "$SDK_DIR/platform-tools/adb" ]; then
        ADB_PATH="$SDK_DIR/platform-tools/adb"
    fi
fi

if [ -z "$ADB_PATH" ]; then
    ADB_PATH=$(which adb 2>/dev/null)
fi

if [ -z "$ADB_PATH" ] || [ ! -f "$ADB_PATH" ]; then
    echo "ERROR: ADB not found"
    echo "Please ensure Android SDK platform-tools is installed"
    exit 1
fi

# Step 1: Check ADB connection
echo "[1/5] Checking ADB connection..."
if ! "$ADB_PATH" devices | grep -qE "device$"; then
    if ! "$ADB_PATH" devices | grep -qE "[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+"; then
        echo "No device connected. Attempting wireless connection..."
        if [ -f "tools/adb/wireless_adb_connect.sh" ]; then
            bash tools/adb/wireless_adb_connect.sh || {
                echo ""
                echo "WARNING: Could not establish wireless connection"
                echo "Continuing with build (you can install manually later)..."
            }
        fi
    fi
else
    echo "Device already connected"
    "$ADB_PATH" devices
fi

echo ""
echo "[2/5] Building APK..."
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Build failed"
    exit 1
fi

echo ""
echo "[3/5] Installing to device..."
"$ADB_PATH" install -r app/build/outputs/apk/debug/app-debug.apk
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Installation failed"
    echo "Make sure device is connected and USB debugging is enabled"
    exit 1
fi

echo ""
echo "[4/5] Launching app..."
"$ADB_PATH" shell am start -n com.titankeys.keyboard/.MainActivity
if [ $? -ne 0 ]; then
    echo ""
    echo "WARNING: Could not launch app automatically"
    echo "Please launch manually from the device"
fi

echo ""
echo "[5/5] Starting logcat (Press Ctrl+C to stop)..."
echo ""
echo "========================================"
echo "Logcat Output (filtered for TitanKeys)"
echo "========================================"
echo ""
"$ADB_PATH" logcat -c
"$ADB_PATH" logcat | grep -iE "TitanKeys|SuggestionController|DictionaryRepo|NgramLanguageModel|ClipboardHistoryManager"

