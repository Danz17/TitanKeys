# Unihertz Titan 2 AVD Setup Guide

This guide explains how to create an Android Virtual Device (AVD) that matches the Unihertz Titan 2 specifications for testing TitanKeys keyboard.

## Quick Setup (Recommended)

### Method 1: Import via Android Studio

1. **Open Android Studio**
2. **Go to**: Tools → Device Manager
3. **Click**: "Create Device"
4. **Select**: "New Hardware Profile"
5. **Enter the following specifications**:

#### Basic Information
- **Device Name**: `Unihertz Titan 2`
- **Manufacturer**: `Unihertz`

#### Screen
- **Screen Size**: `4.5 inches`
- **Resolution**: `1440 x 1440` (Square screen - important!)
- **Screen Density**: `xxhdpi` (~458 dpi)
- **Screen Ratio**: `1:1` (Square)

#### Hardware
- **RAM**: `12288 MB` (12 GB)
- **Input**: `Physical keyboard` (QWERTY)
- **Navigation**: `DPAD`
- **Network**: `Wi-Fi, Bluetooth, Cellular`
- **Telephony**: `GSM`

#### Sensors
- ✅ Accelerometer
- ✅ Gyroscope
- ✅ Proximity sensor
- ✅ Light sensor
- ✅ GPS

#### Cameras
- **Back Camera**: `1080p`
- **Front Camera**: `720p`

6. **Click**: "Finish" to save the hardware profile
7. **Select**: The "Unihertz Titan 2" profile
8. **Click**: "Next"
9. **Select System Image**: 
   - Choose **Android 15 (API 35)** or **Android 14 (API 34)** if 15 is not available
   - Download if needed
10. **Click**: "Next"
11. **Review** and click "Finish"

### Method 2: Use XML File (Recommended)

**Option A: Replace devices.xml (Easiest)**

1. **Backup your existing devices.xml**:
   - Windows: `%USERPROFILE%\.android\devices.xml`
   - Linux/Mac: `~/.android/devices.xml`
   ```bash
   # Backup existing file
   cp ~/.android/devices.xml ~/.android/devices.xml.backup
   ```

2. **Copy the complete devices.xml**:
   ```bash
   # Windows (PowerShell)
   Copy-Item tools\emulator\devices.xml $env:USERPROFILE\.android\devices.xml
   
   # Linux/Mac
   cp tools/emulator/devices.xml ~/.android/devices.xml
   ```

3. **Restart Android Studio**

4. **Create AVD** using the "Unihertz Titan 2" profile

**Option B: Merge into existing devices.xml (Advanced)**

1. **Open your existing devices.xml**:
   - Windows: `%USERPROFILE%\.android\devices.xml`
   - Linux/Mac: `~/.android/devices.xml`

2. **Find the closing `</devices>` tag**

3. **Copy the `<d:device>...</d:device>` block from `titan2_avd.xml`**

4. **Paste it BEFORE the closing `</devices>` tag**

5. **Save and restart Android Studio**

## Important Notes

### Square Screen
The Titan 2 has a **unique square screen** (1440x1440). This is critical for proper UI testing:
- Make sure the resolution is exactly **1440 x 1440**
- Screen ratio should be **1:1** (not 16:9 or 4:3)

### Physical Keyboard
To test keyboard functionality:
1. **Connect a physical keyboard** to your computer
2. The emulator will pass keyboard input through
3. Or use the on-screen keyboard (less ideal for keyboard IME testing)

### Enabling the Keyboard in Emulator
After installing TitanKeys:
1. Open **Settings** in the emulator
2. Go to **System** → **Languages & input** → **Virtual keyboard**
3. Tap **Manage keyboards**
4. Enable **"TitanKeys Physical Keyboard"**
5. In any text field, long-press spacebar or tap keyboard icon
6. Select **"TitanKeys Physical Keyboard"**

## Testing Recommendations

### For Keyboard IME Development
While the emulator is useful for UI testing, **testing on the actual Titan 2 device** via wireless ADB is recommended because:
- Physical keyboard behavior is more accurate
- Real device performance characteristics
- Actual screen dimensions and density
- Better haptic feedback testing

### Use Emulator For
- ✅ UI layout testing
- ✅ Visual debugging
- ✅ Quick iteration on UI changes
- ✅ Testing without physical device

### Use Real Device For
- ✅ Physical keyboard input testing
- ✅ Performance testing
- ✅ Final validation
- ✅ Trackpad functionality

## Troubleshooting

### Emulator is too slow
- Use **x86_64** system image instead of ARM (faster but less accurate)
- Reduce RAM allocation if needed
- Enable hardware acceleration in AVD settings

### Keyboard not working
- Ensure physical keyboard is connected to your computer
- Check that "Physical keyboard" is enabled in hardware profile
- Try restarting the emulator

### Screen looks wrong
- Verify resolution is exactly **1440 x 1440**
- Check screen density is **xxhdpi** (~458 dpi)
- Ensure screen ratio is **1:1** (square)

## System Image Recommendations

- **Best match**: Android 15 (API 35) - matches Titan 2's OS
- **Fallback**: Android 14 (API 34) - if 15 is not available
- **Minimum**: Android 11 (API 30) - for TitanKeys compatibility

## File Locations

- **AVD XML**: `tools/emulator/titan2_avd.xml`
- **This guide**: `tools/emulator/TITAN2_AVD_SETUP.md`
- **Android AVD directory**: 
  - Windows: `%USERPROFILE%\.android\avd\`
  - Linux/Mac: `~/.android/avd/`

