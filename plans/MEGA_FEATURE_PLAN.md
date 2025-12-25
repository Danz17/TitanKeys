# TitanKeys Mega Feature Implementation Plan

**Created**: December 24, 2025
**Status**: Active Development

---

## 📦 Titan 2 System APKs to Extract

When physical Titan 2 device is connected, pull these APKs for reverse engineering:

```bash
# Create destination folder
mkdir -p ideas/apks

# 1. KiKa Keyboard (BBKeyboard) - Main keyboard IME
adb pull /product/app/KikaInputMethod/KikaInputMethod.apk ideas/apks/

# 2. Agui Keyboard Shortcut - Programmable Fn/SYM keys
adb pull /system_ext/app/AguiKeyBoardShortcut/AguiKeyBoardShortcut.apk ideas/apks/

# 3. Keyboard Backlight Controller
adb shell pm path com.unihertz.keyboardlight 2>/dev/null | cut -d: -f2 | xargs -I {} adb pull {} ideas/apks/keyboardlight.apk

# 4. Keyboard Touch Sensor
adb shell pm path com.unihertz.touchsensor 2>/dev/null | cut -d: -f2 | xargs -I {} adb pull {} ideas/apks/touchsensor.apk

# Find all keyboard-related packages
adb shell pm list packages | grep -iE "keyboard|key|input|kika|agui|touch|light"
```

### Features to Study from Each APK

| APK | Features to Extract |
|-----|---------------------|
| KiKa Keyboard | LED indicator logic, modifier key handling, Mozc protocol |
| Agui Shortcut | Fn/SYM programmable keys, Settings.System integration |
| Keyboard Backlight | LED color control, brightness levels, timeout settings |
| Touch Sensor | Touch sensitivity, gesture detection, touch-to-wake |

### Decompile with jadx
```bash
jadx -d ideas/decompiled/kika ideas/apks/KikaInputMethod.apk
jadx -d ideas/decompiled/agui ideas/apks/AguiKeyBoardShortcut.apk
jadx -d ideas/decompiled/backlight ideas/apks/keyboardlight.apk
jadx -d ideas/decompiled/touchsensor ideas/apks/touchsensor.apk
```

---

## 🎯 Feature Priority Matrix

| Priority | Feature | Complexity | Impact | Status |
|----------|---------|------------|--------|--------|
| P0 | LED Indicator Fix (Alt/Shift popup issue) | Medium | Critical | ✅ Fixed |
| P1 | Grammar Correction Testing | Low | High | ✅ Model uploaded to GitHub |
| P1 | Contextual AI Predictions Testing | Low | High | ✅ Reviewed (needs model) |
| P2 | Clipboard Pin Order Settings | Low | Medium | ✅ Done |
| P2 | Programmable Fn/SYM Keys | Medium | High | ✅ Done |
| P3 | Android Native Shortcuts (TYPE_SHORTCUT) | Medium | Medium | ✅ Done |
| P3 | N-gram Data Generation | High | High | ✅ Wikipedia data merged |
| P4 | InputContextState Cleanup | Low | Low | ✅ Done |
| P4 | Code Refactoring (Phase 7A/7B) | High | Medium | 🔴 Not Started |

---

## 🔴 P0: LED Indicator Fix (CRITICAL)

### Problem
Alt and Shift keys show system popup instead of LED indicators in IME bar.

### Root Cause
Modifier key events are not being consumed, allowing system to show default popup.

### Solution
Intercept modifier key events in `onKeyDown`/`onKeyUp` and return `true` to consume them.

### Files to Modify
1. `PhysicalKeyboardInputMethodService.kt` - Key event handling
2. `StatusBarController.kt` - LED state management
3. Create: `ModifierStateManager.kt` - Centralized modifier tracking

### Implementation Steps
```
[ ] 1. Create ModifierStateManager class
    - Track state for each modifier (OFF/ONE_SHOT/LOCKED)
    - Handle single-press (one-shot) and double-press (lock) logic
    - Emit state changes via callback

[ ] 2. Update PhysicalKeyboardInputMethodService.onKeyDown()
    - Intercept KEYCODE_SHIFT_LEFT, KEYCODE_SHIFT_RIGHT
    - Intercept KEYCODE_ALT_LEFT, KEYCODE_ALT_RIGHT
    - Intercept KEYCODE_CTRL_LEFT, KEYCODE_CTRL_RIGHT
    - Return true to consume event (prevents popup)
    - Forward to ModifierStateManager

[ ] 3. Update PhysicalKeyboardInputMethodService.onKeyUp()
    - Handle modifier release
    - Clear one-shot state after next key press

[ ] 4. Update StatusBarController
    - Listen to ModifierStateManager state changes
    - Update LED colors: Blue (one-shot), Orange (locked), None (off)

[ ] 5. Test on Titan 2 device
    - Verify no popup appears
    - Verify LED indicators work correctly
    - Verify modifier functions still work with other keys
```

### Test Cases
- [ ] Single press Shift → LED blue, next letter uppercase, LED off
- [ ] Double press Shift → LED orange, all letters uppercase until pressed again
- [ ] Single press Alt → LED blue, next key uses alt character
- [ ] Double press Alt → LED orange (alt lock)
- [ ] Ctrl same behavior

---

## 🟡 P1: Grammar Correction Testing

### Current State
Grammar correction engine added but not tested on device.

### Files Involved
- `GrammarCorrectionEngine.kt`
- `GrammarModelManager.kt`
- `GrammarTypes.kt`
- `GrammarPerformanceMonitor.kt`
- `SuggestionController.kt` (integration)

### Testing Steps
```
[ ] 1. Build and install on device
[ ] 2. Enable grammar checking in settings (if UI exists)
[ ] 3. Type sentences with intentional errors:
    - "I goed to the store" → should suggest "went"
    - Subject-verb agreement errors
    - Article usage errors
[ ] 4. Verify suggestions appear in suggestion bar
[ ] 5. Check performance (should be <100ms)
[ ] 6. Test fallback modes (AI → Rules → Disabled)
```

### Known Limitations
- No TensorFlow Lite model file yet (`models/grammar_*.tflite`)
- Will fall back to rule-based checking

---

## 🟡 P1: Contextual AI Predictions Testing

### Current State
ContextualPredictor added with TensorFlow Lite integration, not tested.

### Files Involved
- `ContextualPredictor.kt`
- `SuggestionSettings.kt`
- `TextInputSettingsScreen.kt` (UI toggle added)

### Testing Steps
```
[ ] 1. Enable "Contextual AI Suggestions" in Text Input settings
[ ] 2. Type sentences and observe next-word predictions
[ ] 3. Verify fallback to n-gram when model not loaded
[ ] 4. Check performance caching (30s cache)
[ ] 5. Verify privacy safeguards (sensitive content skipped)
```

### Known Limitations
- No TensorFlow Lite model file yet (`models/contextual_predictor.tflite`)
- No vocabulary file yet (`models/vocab.txt` is placeholder)
- Will fall back to n-gram predictions

---

## 🔴 P2: Clipboard Pin Order Settings

### Problem
Pinned items always appear first, no user preference for order.

### Files to Modify
1. `SettingsManager.kt` - Add setting
2. `ClipboardHistoryEntry.kt` - Update sorting logic
3. `TextInputSettingsScreen.kt` or new settings screen - Add UI toggle

### Implementation Steps
```
[ ] 1. Add setting to SettingsManager
    - KEY_CLIPBOARD_PINNED_FIRST = "clipboard_pinned_first"
    - Default: true (current behavior)

[ ] 2. Update ClipboardHistoryEntry.compareTo()
    - Check setting and adjust sorting

[ ] 3. Add UI toggle in appropriate settings screen

[ ] 4. Test clipboard ordering
```

---

## 🔴 P2: Programmable Fn/SYM Keys

### Research Source
Based on Agui keyboard shortcut system analysis.

### System Settings (Already Exist)
```
fn_programmable_key_enable=1
fn_programmable_key_function=1
sym_programmable_key_enable=1
sym_programmable_key_function=2
```

### Implementation Steps
```
[ ] 1. Read system settings for Fn/SYM key configuration
    - Settings.System.getInt("fn_programmable_key_function")
    - Settings.System.getInt("sym_programmable_key_function")

[ ] 2. Create FnKeyHandler class
    - Define function mappings (0=disabled, 1=app, 2=action, etc.)
    - Handle Fn key press based on configuration

[ ] 3. Integrate with LauncherShortcutController
    - Allow Fn+key combinations for shortcuts

[ ] 4. Add settings UI (optional)
    - Allow users to customize Fn/SYM behavior from TitanKeys
```

---

## 🔴 P3: Android Native Shortcuts (TYPE_SHORTCUT)

### Problem
Only TYPE_APP shortcuts implemented, not TYPE_SHORTCUT.

### File
`LauncherShortcutController.kt` (line 104 TODO)

### Implementation Steps
```
[ ] 1. Implement ShortcutManager integration
    - Get pinned shortcuts
    - Get dynamic shortcuts

[ ] 2. Create shortcut picker UI
    - List available shortcuts from all apps
    - Allow assignment to keys

[ ] 3. Execute shortcuts via ShortcutManager
    - startShortcut() API

[ ] 4. Test with various app shortcuts
```

---

## 🔴 P3: N-gram Data Generation

### Goal
Generate bigram/trigram data for all 9 supported languages.

### Languages
- English, Italian, German, Spanish, French, Polish, Portuguese, Russian, Lithuanian

### Implementation Steps
```
[ ] 1. Download text corpora for each language
    - Wikipedia dumps
    - OpenSubtitles data
    - News articles

[ ] 2. Run n-gram extraction
    python tools/dictionaries/extract_ngrams.py --input corpus.txt --output {lang}_bigrams.json

[ ] 3. Run dictionary preprocessing
    kotlinc -script tools/dictionaries/preprocess-dictionaries.main.kts

[ ] 4. Verify dictionary sizes and n-gram counts

[ ] 5. Test predictions with real n-gram data
```

---

## 🔴 P4: InputContextState Cleanup

### Problem
`shouldDisableSmartFeatures` is legacy flag, should use specific flags.

### File
`InputContextState.kt` (line 87 TODO)

### Implementation Steps
```
[ ] 1. Identify all usages of shouldDisableSmartFeatures
[ ] 2. Replace with specific flags:
    - shouldDisableSuggestions
    - shouldDisablePrediction
    - shouldDisableAutoCorrect
[ ] 3. Remove legacy flag
[ ] 4. Test affected features
```

---

## 🔴 P4: Code Refactoring (Phase 7A/7B)

### Phase 7A: UI Surface Controllers
```
[ ] Extract CandidatesBarController
[ ] Create KeyboardVisibilityController
[ ] Reduce PhysicalKeyboardInputMethodService size
```

### Phase 7B: Input Event Router
```
[ ] Create inputmethod/events/ package
[ ] Create InputEventRouter class
[ ] Route events: Nav-mode, Shortcuts, Text pipeline, SYM, Fallback
[ ] Reduce onKeyDown/Up complexity
```

---

## 📋 Testing Checklist (Per Feature)

### Before Testing
- [ ] Build debug APK
- [ ] Install on Titan 2 via ADB
- [ ] Clear app data (if needed)

### During Testing
- [ ] Test happy path
- [ ] Test edge cases
- [ ] Test error conditions
- [ ] Check performance
- [ ] Verify no regressions

### After Testing
- [ ] Document issues found
- [ ] Fix critical bugs
- [ ] Update feature status

---

## 🚀 Implementation Order

### Sprint 1: Critical Fixes
1. **LED Indicator Fix** - Highest priority, user-facing bug

### Sprint 2: Feature Testing
2. **Grammar Correction Testing** - Already implemented
3. **Contextual AI Testing** - Already implemented

### Sprint 3: Quick Wins
4. **Clipboard Pin Settings** - Low complexity
5. **Programmable Fn/SYM Keys** - Medium complexity

### Sprint 4: Enhancements
6. **Android Native Shortcuts** - Medium complexity
7. **N-gram Data Generation** - Time-consuming but impactful

### Sprint 5: Technical Debt
8. **InputContextState Cleanup** - Low priority
9. **Code Refactoring** - Can be done incrementally

---

## 📝 Notes

- Each feature should be tested individually before moving to next
- Commits should be atomic (one feature per commit)
- Update this plan as features are completed
- Test on actual Titan 2 device for accurate feedback

---

*Last Updated: December 26, 2025*
