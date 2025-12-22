# Wireless ADB Setup Guide

This guide explains how to set up wireless ADB for live testing on your Titan 2 device.

## Prerequisites

- Titan 2 device on the same WiFi network as your development machine
- Android SDK platform-tools installed (ADB)
- Developer options enabled on Titan 2
- Android 11+ (required for wireless debugging)

## Step-by-Step Setup

### 1. Enable Developer Options on Titan 2

1. Open **Settings** → **About phone**
2. Tap **Build number** 7 times
3. You'll see a message: "You are now a developer!"

### 2. Enable Wireless Debugging

1. Go to **Settings** → **System** → **Developer options**
2. Find **Wireless debugging** and toggle it ON
3. Tap **Wireless debugging** to open settings
4. Note the **IP address and port** shown (e.g., `192.168.1.100:12345`)

### 3. Pair Device (First Time Only)

1. In **Wireless debugging** settings, tap **Pair device with pairing code**
2. Note the following:
   - **Pairing IP address** (e.g., `192.168.1.100`)
   - **Pairing port** (e.g., `12345`)
   - **Pairing code** (6 digits, e.g., `123456`)

3. On your development machine, run:
   ```bash
   adb pair <PAIRING_IP>:<PAIRING_PORT>
   ```
   Example:
   ```bash
   adb pair 192.168.1.100:12345
   ```

4. When prompted, enter the pairing code

### 4. Connect Wirelessly

1. After pairing, in **Wireless debugging** settings, note the **IP address & Port**
   (This is different from the pairing address, e.g., `192.168.1.100:45678`)

2. On your development machine, run:
   ```bash
   adb connect <IP>:<PORT>
   ```
   Example:
   ```bash
   adb connect 192.168.1.100:45678
   ```

3. Verify connection:
   ```bash
   adb devices
   ```
   You should see your device listed with the IP address.

## Using the Connection Scripts

### Windows

Run the automated connection script:
```batch
scripts\wireless_adb_connect.bat
```

The script will:
- Check if device is already connected
- Guide you through pairing (first time)
- Connect wirelessly
- Verify the connection

### Linux/Mac

Run the automated connection script:
```bash
bash scripts/wireless_adb_connect.sh
```

## Quick Reconnection

After the initial setup, you can reconnect quickly:

```bash
adb connect <IP>:<PORT>
```

**Tip**: Save the connection IP and port for easy reconnection. The port may change after device restart, but the IP usually stays the same.

## Troubleshooting

### Device Not Found

- Ensure both devices are on the same WiFi network
- Check that wireless debugging is still enabled
- Try disabling and re-enabling wireless debugging
- Restart ADB: `adb kill-server && adb start-server`

### Connection Drops

- Wireless ADB may disconnect if device goes to sleep
- Simply run `adb connect <IP>:<PORT>` again to reconnect
- The port may have changed - check Wireless debugging settings again

### Pairing Fails

- Ensure you're using the pairing IP and port (not the connection IP)
- Check that the pairing code hasn't expired (they time out quickly)
- Make sure both devices are on the same network
- Try restarting wireless debugging on the device

## Integration with Build Scripts

The build scripts (`build_and_run.bat` and `build_and_run.sh`) now automatically:
- Check for connected devices
- Attempt wireless connection if no device found
- Continue with build and install

## Live Testing Workflow

Use the complete live testing script:
```batch
scripts\live_test.bat
```

This script:
1. Checks/establishes ADB connection
2. Builds the APK
3. Installs to device
4. Launches the app
5. Streams logcat output

## Best Practices

1. **Keep Device Awake**: Set screen timeout to longer during development
2. **Stable WiFi**: Use a stable WiFi connection (avoid public networks)
3. **Save Connection Info**: Note your device's IP address for quick reconnection
4. **USB Fallback**: Keep USB cable handy as backup if wireless fails
5. **Port Changes**: The connection port may change after device restart - check settings

## Security Note

Wireless debugging is secure (uses TLS), but:
- Only enable when needed for development
- Disable when not in use
- Use on trusted networks only

---

*Made by Phenix with love and frustration*

