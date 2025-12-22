# TitanKeys

**Physical Keyboard IME for Unihertz Titan 2 and BlackBerry devices**

A powerful input method designed for physical keyboards, making typing faster with extensive shortcuts, customizations, and intelligent predictions.

## Key Features

### Keyboard Layout & UI
- Compact status bar with LEDs for Shift/SYM/Ctrl/Alt
- Suggestions bar with swipe-pad gesture to move cursor
- Voice input and clipboard buttons always accessible
- Multiple layouts (QWERTY/AZERTY/QWERTZ, Greek, Cyrillic, Arabic, translit, etc.)
- JSON import/export for custom layouts
- Two SYM pages (emoji + symbols) with integrated editor

### Typing & Modifiers
- Long press: Alt+key by default or uppercase; configurable timing
- Shift/Ctrl/Alt in one-shot or latch mode (double tap)
- Double Shift for Caps Lock
- Multi-tap for keys with variants
- Standard shortcuts: Ctrl+C/X/V, Ctrl+A, Ctrl+Backspace, arrow navigation

### Smart Predictions
- Next-word prediction based on context (bigram/trigram models)
- User learning - keyboard adapts to your typing patterns
- Adaptive modes switching between current-word and next-word suggestions
- Auto-correction with undo support
- Per-language user dictionaries

### Clipboard & Input
- **Quick Paste**: Tap clipboard button to instantly paste
- **Clipboard History**: Long-press to view history popup with images
- **Universal Image Capture**: Captures ALL copied images from any app (WhatsApp, Gallery, browsers)
- Voice input via microphone button or Alt+Ctrl shortcut

### Navigation
- Nav Mode: double Ctrl outside text fields for arrow navigation
- Swipe on suggestions bar to move cursor
- Text selection helper (Ctrl+W/R)
- Launcher shortcuts: press letter to open/assign apps
- Power shortcuts: press SYM then letter for shortcuts anywhere

## Installation

1. Build the APK or install from releases
2. Android Settings -> System -> Languages & input -> Virtual keyboard -> Manage keyboards
3. Enable "TitanKeys" and select it from the input selector when typing

## Requirements
- Android 10 (API 29) or higher
- Device with physical keyboard (optimized for Unihertz Titan 2, adaptable via JSON)

## Build
Android project in Kotlin/Jetpack Compose. Open in Android Studio and build normally.

```bash
./gradlew assembleDebug
```

## Credits
- **Developer**: Phenix
- **Original Codebase**: Based on Pastiera by Andrea Palumbo (Palsoftware)

## License
Open source project. See LICENSE file for details.
