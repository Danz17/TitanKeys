# Testing Workflow Guide

Complete guide for testing TitanKeys keyboard on Titan 2 device with wireless ADB.

## Quick Start

### One-Command Testing

Run the complete live testing workflow:

**Windows**:
```batch
scripts\live_test.bat
```

**Linux/Mac**:
```bash
bash scripts/live_test.sh
```

This script:
1. Connects to device via wireless ADB
2. Builds the APK
3. Installs to device
4. Launches the app
5. Streams logcat output

## Manual Testing Workflow

### 1. Connect Device

**First Time**:
```bash
# Run connection script
scripts/wireless_adb_connect.bat  # Windows
# or
bash scripts/wireless_adb_connect.sh  # Linux/Mac
```

**Subsequent Times**:
```bash
adb connect <IP>:<PORT>
```

### 2. Build and Install

**Quick Build**:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Or use build script**:
```bash
build_and_run.bat  # Windows
# or
./build_and_run.sh  # Linux/Mac
```

### 3. Launch App

```bash
adb shell am start -n it.palsoftware.titankeys/.MainActivity
```

### 4. View Logs

**Filtered logs** (TitanKeys only):
```bash
adb logcat | grep -i "TitanKeys\|SuggestionController\|DictionaryRepo\|NgramLanguageModel"
```

**All logs**:
```bash
adb logcat
```

**Clear and view**:
```bash
adb logcat -c
adb logcat
```

## Testing Checklist

### Next-Word Prediction

- [ ] **Basic Prediction**: Type a word, press space, verify next-word predictions appear
- [ ] **Context Awareness**: Type multiple words, verify predictions improve with context
- [ ] **User Learning**: Type same phrase multiple times, verify it learns
- [ ] **Mode Switching**: Verify switches between current-word and next-word modes
- [ ] **Accuracy**: Test common phrases, verify top-3 accuracy >70%

### Clipboard Features

- [ ] **Quick Paste**: Tap clipboard button, verify current clipboard pastes
- [ ] **History Popup**: Long-press clipboard button, verify history appears
- [ ] **History Selection**: Select item from history, verify it pastes
- [ ] **Badge Count**: Verify badge shows correct count
- [ ] **Flash Animation**: Copy something new, verify button flashes

### Dictionary Performance

- [ ] **Loading Time**: Check logcat for dictionary load time (<500ms)
- [ ] **Memory Usage**: Monitor memory usage (should be <50MB per language)
- [ ] **Lookup Speed**: Test suggestion response time (<50ms)
- [ ] **N-gram Loading**: Verify n-grams load correctly (check logs)

### User Learning

- [ ] **Pattern Recording**: Type phrases, verify they're learned
- [ ] **Persistence**: Restart app, verify learned patterns persist
- [ ] **Boost Effect**: Verify learned patterns appear higher in predictions
- [ ] **Clear Function**: Test clearing learned patterns (if implemented)

### UI/UX

- [ ] **Suggestions Bar**: Verify suggestions display correctly
- [ ] **Mode Transitions**: Verify smooth transitions between modes
- [ ] **Visual Feedback**: Check haptic feedback on actions
- [ ] **Responsiveness**: Verify UI responds quickly to input

## Performance Monitoring

### Memory Usage

```bash
adb shell dumpsys meminfo it.palsoftware.titankeys
```

### CPU Usage

```bash
adb shell top -n 1 | grep titankeys
```

### Battery Impact

Monitor battery usage in device settings after extended use.

## Debugging

### Enable Debug Logging

In app settings, enable "Suggestion debug logging" for detailed logs.

### View Specific Logs

**Dictionary Loading**:
```bash
adb logcat | grep DictionaryRepo
```

**Predictions**:
```bash
adb logcat | grep NgramLanguageModel
```

**User Learning**:
```bash
adb logcat | grep UserLearningStore
```

**Clipboard**:
```bash
adb logcat | grep ClipboardHistoryManager
```

### Common Issues

**Dictionary Not Loading**:
- Check logcat for errors
- Verify .dict files exist in assets
- Check file permissions

**Predictions Not Appearing**:
- Verify next-word prediction is enabled in settings
- Check if n-gram data is loaded (check logs)
- Verify context tracker is working

**Wireless ADB Disconnects**:
- Keep device screen on
- Use stable WiFi network
- Reconnect with `adb connect <IP>:<PORT>`

## Automated Testing

### Build Test Script

Create a test script for automated validation:

```bash
#!/bin/bash
# test_features.sh

echo "Testing TitanKeys features..."

# Test 1: Dictionary loads
adb logcat -d | grep -q "Dictionary loaded" && echo "✓ Dictionary loaded" || echo "✗ Dictionary failed"

# Test 2: App launches
adb shell pm list packages | grep -q titankeys && echo "✓ App installed" || echo "✗ App not found"

# Add more tests...
```

## Best Practices

1. **Test on Real Device**: Always test on Titan 2, not just emulator
2. **Monitor Performance**: Watch memory and CPU during testing
3. **Test Edge Cases**: Empty clipboard, no network, etc.
4. **User Scenarios**: Test real typing scenarios, not just isolated features
5. **Log Everything**: Keep logs for debugging issues
6. **Iterate Quickly**: Use live_test script for fast iteration

## Testing Scenarios

### Scenario 1: Typing a Message

1. Open messaging app
2. Type: "How are you"
3. Verify next-word predictions after "How" and "are"
4. Test selecting predictions
5. Verify user learning records the sequence

### Scenario 2: Copy-Paste Workflow

1. Copy text from browser
2. Open text editor
3. Tap clipboard button (quick paste)
4. Verify text is pasted
5. Long-press clipboard button
6. Verify history shows copied item

### Scenario 3: Multi-language

1. Switch to Italian keyboard
2. Type Italian text
3. Verify Italian dictionary loads
4. Switch to English
5. Verify English dictionary loads
6. Test predictions in both languages

## Performance Benchmarks

Target metrics for acceptable performance:

- **Dictionary Load**: <500ms
- **Prediction Lookup**: <50ms
- **UI Response**: <100ms
- **Memory per Language**: <50MB
- **Battery Impact**: Minimal (no excessive wake locks)

## Reporting Issues

When reporting issues, include:
- Device model (Titan 2)
- Android version
- Logcat output
- Steps to reproduce
- Expected vs actual behavior

---

*Made by Phenix with love and frustration*

