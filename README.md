[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C31OHWF2)
# Pastiera

Input method for physical keyboards (e.g. Unihertz Titan 2) designed to make typing faster, with many shortcuts and customizations via JSON files.

## Quick Overview
- Compact status bar with LEDs for Shift/SYM/Ctrl/Alt, variants/suggestions bar with swipe-pad gesture to move the cursor, and reorganized button layout: [Hide Keyboard] [Voice] [Clipboard] [Language Switcher].
- Multiple layouts (QWERTY/AZERTY/QWERTZ, Greek, Cyrillic, Arabic, translit, etc.) configurable; JSON import/export from the app. Frontend for map customization available at https://pastierakeyedit.vercel.app/
- Two SYM pages launchable from touch or physical keys (emoji + symbols) reorderable/disableable, with integrated editor and many new Unicode characters.
- Complete backup/restore (settings, layouts, variations, dictionaries), UI translated in multiple languages and GitHub update checker.

## Typing and Modifiers
- Long press: Alt+key by default or uppercase; configurable timing.
- Shift/Ctrl/Alt in one-shot or latch mode (double tap), double Shift for Caps Lock; option to clear Alt on space.
- Multi-tap for keys with variants defined by the layout.
- Standard shortcuts: Ctrl+C/X/V, Ctrl+A, Ctrl+Backspace, Ctrl+E/D/S/F or I/J/K/L for arrows, Ctrl+W/R for selection, Ctrl+T for Tab, Ctrl+Y/H for Page Up/Down, Ctrl+Q for Esc (All customizable in the Customize Nav screen)

## Navigation and Gestures
- Nav Mode: double Ctrl outside text fields to use ESDF/IJKL/T as arrows/Tab; remappable for each alphabetic key from the "Nav Mode" menu.
- Variants bar as swipe pad: drag to move cursor with adjustable threshold; automatic hint if there are no variants.
- Text selection helper (Ctrl+W/R) and undo for auto-replace.
- Launcher shortcuts: in the launcher press a letter to open/assign an app.
- Power shortcuts: press SYM (5s timeout) and then a letter to use the same shortcuts anywhere, even outside the launcher.
- Gear icon on the keyboard bar to open settings without leaving the text field.

## Keyboard Layout
- Included layouts: qwerty, azerty, qwertz, greek, arabic, russian/armenian phonetic, translit, plus Alt maps dedicated to Titan 2.
- Layout conversion: layout selection from list enabled (configurable).
- Multi-tap support and mapping for complex characters in layouts.
- Import/export from JSON files directly from the app, graphical preview and list management (enable/disable, delete).
- Maps are saved in `files/keyboard_layouts` and can also be edited manually.

## Symbols, Emojis and Variations
- Two SYM pages (emoji + symbols) touch: you can reorder/activate them, automatic closure after input, customizable keycaps.
- In-app SYM editor with emoji grid, Unicode picker and second page full of new characters.
- Variations bar above the keyboard: shows accents/variants of the letter just typed or static sets (utility/email) when needed.
- Dedicated variations editor to replace/add variants via JSON or Unicode picker; optional static bar.
- Emoji/symbols mode also accessible by directly touching the bar.

## Suggestions and Auto-correction
- Optional auto-replace on space/enter; smart auto-cap after ., !, ?.
- User dictionary with frequency/priority and search; personal entries always on top.
- Auto-corrections editor per language, quick search, and global "Ricette Pastiera" set valid for all languages.
- Debug/experiments mode disableable; smart functions automatically disabled in password/email fields where they're not needed.
- **Next-Word Prediction**: BlackBerry-style next-word predictions based on context (bigram/trigram models)
- **User Learning**: Keyboard learns from your typing patterns to improve predictions over time
- **Adaptive Modes**: Automatically switches between current-word suggestions and next-word predictions

## Comfort and Extra Input
- Double space -> period+space+uppercase; swipe left on keyboard to delete word (Titan 2).
- SYM key for double emoji/symbols page; SYM auto close configurable.
- Alt+Ctrl shortcut (optional) to launch Google Voice Typing microphone; microphone always available on the variants bar.
- **Quick Clipboard Paste**: Tap the clipboard button to instantly paste the current clipboard content; long-press to view clipboard history popup
- **Universal Clipboard Image Capture**: Captures ALL copied images from any app (WhatsApp, Gallery, browsers), not just screenshots
- Compact status bar to occupy little vertical space. With on-screen keyboard off from the IME selector it occupies even less space thanks to Pastierina mode.
- UI translated (it/en/de/es/fr/pl/ru/hy) and initial tutorial.

## Development Tools

### Wireless ADB Setup
- Automated scripts for wireless ADB connection to Titan 2 device
- One-command live testing workflow (`scripts/live_test.bat` or `scripts/live_test.sh`)
- Enhanced build scripts with automatic wireless ADB support

### Dictionary Enhancement
- Scripts for downloading text corpora from OpenSubtitles and Wikipedia
- N-gram extraction tools for building language models
- Dictionary merging utilities with multiple merge strategies
- Complete pipeline script for building enhanced dictionaries

See `docs/` directory for detailed guides:
- `WIRELESS_ADB_SETUP.md` - Wireless ADB setup instructions
- `DICTIONARY_IMPROVEMENTS.md` - Dictionary enhancement guide
- `TESTING_WORKFLOW.md` - Complete testing procedures

## Backup, Update and Data
- Backup/restore from UI in ZIP format: includes preferences, custom layouts, variations, sym/ctrl maps, user dictionaries.
- Restore merges saved variations with default ones to avoid losing new keys.
- GitHub update checker integrated on settings opening (with possibility to ignore a release).
- Customizable files in `files/`: `variations.json`, `ctrl_key_mappings.json`, `sym_key_mappings*.json`, `keyboard_layouts/*.json`, user dictionaries.

## Installation
1. Build the APK or install an existing build.
2. Android Settings -> System -> Languages & input -> Virtual keyboard -> Manage keyboards.
3. Enable "Pastiera Physical Keyboard" and select it from the input selector when typing.

## Quick Configuration
- Press timing: Settings -> Keyboard timing (long press, multi-tap).
- Text: auto-uppercase (sentence start and post-punctuation), double space, clear Alt on space, swipe-to-delete, show keyboard, voice shortcut Alt+Ctrl.
- Auto-correction: toggle suggestions/accents/auto-replace, active languages, user dictionary, Ricette Pastiera, debug.
- Customization: keyboard layout (choice, import/export, cycle), SYM/emoji (mapping, page order, automatic closure), variations, Nav Mode mapping, launcher/power shortcuts, cursor swipe sensitivity.
- Advanced: backup/restore, IME test, build info.

## Requirements
- Android 10 (API 29) or higher.
- Device with physical keyboard (profiled for Unihertz Titan 2, adaptable via JSON).

## Build
Android project in Kotlin/Jetpack Compose. Open in Android Studio and build normally.
