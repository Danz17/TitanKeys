#!/bin/bash
# Wireless ADB connection script for Linux/Mac
# Automatically connects to Android device via wireless ADB

set -e

echo "========================================"
echo "Wireless ADB Connection"
echo "========================================"
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "ERROR: ADB not found in PATH"
    echo "Please ensure Android SDK platform-tools is installed and in PATH"
    exit 1
fi

# Check if device is already connected
echo "Checking for connected devices..."
if adb devices | grep -qE "device$"; then
    echo "Device already connected via ADB"
    adb devices
    exit 0
fi

# Check for wireless connection
if adb devices | grep -qE "[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+"; then
    echo "Wireless device already connected"
    adb devices
    exit 0
fi

echo ""
echo "No device connected. Setting up wireless ADB..."
echo ""
echo "Instructions:"
echo "1. On your Titan 2 device:"
echo "   - Settings > About phone > Tap \"Build number\" 7 times"
echo "   - Settings > System > Developer options"
echo "   - Enable \"Wireless debugging\""
echo ""
echo "2. In Wireless debugging:"
echo "   - Tap \"Pair device with pairing code\""
echo "   - Note the IP address, port, and pairing code"
echo ""
echo "3. Enter the pairing information below:"
echo ""

read -p "Enter pairing IP address (e.g., 192.168.1.100): " PAIR_IP
read -p "Enter pairing port (e.g., 12345): " PAIR_PORT
read -p "Enter pairing code (6 digits): " PAIR_CODE

if [ -z "$PAIR_IP" ] || [ -z "$PAIR_PORT" ] || [ -z "$PAIR_CODE" ]; then
    echo "ERROR: All fields required"
    exit 1
fi

echo ""
echo "Pairing device..."
if ! adb pair "$PAIR_IP:$PAIR_PORT"; then
    echo "ERROR: Pairing failed"
    echo "Please check:"
    echo "  - Device is on the same WiFi network"
    echo "  - Wireless debugging is enabled"
    echo "  - IP address and port are correct"
    exit 1
fi

echo ""
echo "Pairing successful! Now connecting..."
echo ""
echo "In Wireless debugging on your device, note the \"IP address & Port\""
echo "(e.g., 192.168.1.100:12345)"
echo ""

read -p "Enter connection IP address: " CONNECT_IP
read -p "Enter connection port: " CONNECT_PORT

if [ -z "$CONNECT_IP" ] || [ -z "$CONNECT_PORT" ]; then
    echo "ERROR: Connection IP and port required"
    exit 1
fi

echo ""
echo "Connecting to $CONNECT_IP:$CONNECT_PORT..."
if ! adb connect "$CONNECT_IP:$CONNECT_PORT"; then
    echo "ERROR: Connection failed"
    exit 1
fi

echo ""
echo "Waiting for device..."
sleep 2

echo ""
echo "Connected devices:"
adb devices

echo ""
echo "========================================"
echo "Wireless ADB connection established!"
echo "========================================"
echo ""
echo "Tip: To reconnect later, just run:"
echo "  adb connect $CONNECT_IP:$CONNECT_PORT"
echo ""

