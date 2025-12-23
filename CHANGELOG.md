# Changelog

## New Features TitanKeys 0.9 (In Development)

### Contextual AI Predictions (December 2025)
- **TensorFlow Lite Integration**: Added TensorFlow Lite for on-device AI inference
- **Contextual Predictor**: New transformer-based next-word prediction system with better context understanding
- **Smart Fallback**: Automatically falls back to n-gram prediction if AI inference times out or fails
- **Privacy-First Design**: Sensitive content detection to skip processing of passwords, credit card numbers, etc.
- **Performance Caching**: Prediction caching for faster repeated queries (30s cache, 1000 entries max)
- **Settings UI**: Toggle for enabling/disabling Contextual AI suggestions in Text Input settings

### Grammar Correction Engine (December 2025)
- **AI-Powered Grammar Check**: New grammar correction system using TensorFlow Lite models
- **Multiple Fallback Levels**: Full AI, Hybrid (AI + rules), Rules-only, or Disabled modes
- **Supported Error Types**: Verb tense, subject-verb agreement, article usage, preposition errors, and more
- **Automatic Sentence Analysis**: Grammar suggestions appear after sentence completion
- **Multi-Language Support**: Architecture supports English, Spanish, French, German, Italian models
- **Performance Constraints**: 100ms timeout ensures grammar checking doesn't slow down typing

### Dictionary Preprocessing Enhanced (December 2025)
- **N-gram Data Support**: Preprocessing script now includes bigram and trigram data in serialized dictionaries
- **Domain-Specific Words**: Support for loading domain-specific word lists from `{lang}_domain_words.json`
- **Common Phrases**: Support for common phrases from `{lang}_common_phrases.json`
- **Frequency Filtering**: N-gram data filtered by minimum frequency (default: 2) for quality
- **Enhanced File Discovery**: Script checks multiple locations for corpora files
- **Comprehensive Logging**: Detailed statistics output for n-gram counts and dictionary sizes

### Clipboard UI Improvements (December 2025)
- **Pin Icon**: Added Material Design push pin icon for clipboard pinning
- **Delete Icon**: Added delete icon for clipboard item removal
- **Visual Feedback**: Pin icon turns yellow when item is pinned, gray otherwise

### Keyboard Bar Layout Improvements (December 2025)
- **Reorganized Button Layout**: New layout: [Hide Keyboard] ... [Voice][Clipboard] ... [Language Switcher]
- **Hide Keyboard Button**: Added dedicated button to dismiss the virtual keyboard
- **Fade Animation for Suggestions**: Predictive words bar now fades in when typing and fades out after 500ms delay
- **Improved Window Insets**: Apps now properly resize when keyboard is shown/hidden

### Clipboard Image Capture (December 2025)
- **Universal Image Detection**: Clipboard now captures ALL copied images, not just screenshots
- **Multi-Method Detection**: Uses MIME type, ContentResolver query, and bitmap header detection
- **Cross-App Support**: Works with images from WhatsApp, Gallery, browsers, and any other app

### Language Improvements (December 2025)
- **English Default**: Changed default locale from Italian to English for better international support
- **Fixed Prediction Language**: Predictions now correctly default to English when no subtype is selected
- **Strict Language Filtering**: Improved dictionary filtering to prevent cross-language contamination

### Development Tools & Workflow
- **Tools Folder Reorganization**: Support scripts organized into `tools/` with subfolders:
  - `tools/dictionaries/` - Dictionary download, merge, and preprocessing scripts
  - `tools/testing/` - Live testing and debug scripts
  - `tools/adb/` - Wireless ADB connection utilities
  - `tools/corpora/` - Downloaded frequency data (gitignored)
- **Wireless ADB Support**: Automated scripts for connecting to Titan 2 via wireless ADB
- **Live Testing Scripts**: One-command build, install, and test workflow
- **Dictionary Enhancement Tools**: Scripts for downloading corpora, extracting n-grams, and merging dictionaries
- **Complete Dictionary Pipeline**: Automated script for building enhanced dictionaries with n-gram data
- **Enhanced Build Scripts**: Build scripts now automatically attempt wireless ADB connection

### Clipboard Quick Paste
- **Quick Paste on Tap**: Tap the clipboard button to instantly paste the current clipboard content
- **Clipboard History on Long-Press**: Long-press the clipboard button to show a popup with recent clipboard items
- **Native Android Integration**: Uses Android's ClipboardManager API for seamless clipboard access
- **Haptic Feedback**: Provides tactile feedback on both quick paste and history access

### Next-Word Prediction System
- **N-gram Language Model**: Implemented bigram and trigram models for context-aware next-word prediction
- **Adaptive Suggestion Modes**: System automatically switches between current-word suggestions and next-word predictions based on typing context
- **User Learning**: Keyboard learns from user typing patterns and adapts predictions over time
- **Context Tracking**: Tracks previous words in sentences to provide better predictions
- **BlackBerry-style Predictions**: Shows next-word predictions after space/punctuation, similar to classic BlackBerry keyboards

### Enhanced Dictionary Infrastructure
- **N-gram Support**: Dictionary format extended to support bigram and trigram data
- **Context-Aware Predictions**: Predictions based on word sequences, not just individual words
- **User Learning Store**: Persists learned word patterns to improve predictions over time
- **N-gram Extraction Tools**: Python script for extracting n-grams from text corpora

### Dictionary Updates (December 2025)
- **Expanded Word Lists**: All 9 language dictionaries updated with FrequencyWords (OpenSubtitles) and Wikipedia frequency data
- **Multiple Data Sources**: Combined word lists from OpenSubtitles corpus and Wikipedia word frequencies
- **Final Word Counts**:
  - English: 50,000 → 74,611 words (+49%)
  - Italian: 50,000 → 79,193 words (+58%)
  - German: 50,000 → 84,268 words (+69%)
  - Spanish: 50,000 → 81,072 words (+62%)
  - French: 50,000 → 76,754 words (+53%)
  - Polish: 50,000 → 84,574 words (+69%)
  - Portuguese: 50,000 → 82,268 words (+65%)
  - Russian: 50,000 → 83,550 words (+67%)
  - Lithuanian: 50,000 → 79,946 words (+60%, OpenSubtitles only)
- **Improved Frequency Data**: Word frequencies merged using weighted average strategy for better suggestion ranking
- **Tools Reorganization**: Dictionary tools moved to `tools/dictionaries/` for better project organization

### Settings & Configuration
- **Next-Word Prediction Toggle**: Enable/disable next-word prediction in settings
- **Prediction Mode Selection**: Choose between current-word, next-word, or hybrid modes
- **User Learning Toggle**: Enable/disable learning from typing patterns
- **Settings Manager Integration**: All prediction settings integrated into SettingsManager

## Documentation Updates

### README Translation (v0.8)
- **English Translation**: Added complete English translation to README.md for better international accessibility
- **Maintained Technical Accuracy**: All technical terms, file paths, and code references preserved
- **Structure Preserved**: Original markdown formatting and organization maintained

## New Features TitanKeys 0.2

### Keyboard Enhancements
- **Swipe Pad Navigation**: The keyboard status bar now doubles as a swipe pad, allowing you to move the cursor by swiping
- **Touch-Enabled Emojis and Symbols**: Emojis and symbols on the SYM keyboard are now also directly touchable for easier input
- **Keyboard Layout Conversion**: Added support for converting between different keyboard layouts (AZERTY, QWERTZ, etc.)

### Auto-Capitalization
- **Smart Sentence Capitalization**: Automatically capitalizes the first letter after sentences ending with periods, exclamation marks, or question marks

### Settings & Customization
- **Customizable Navigation Mode**: Navigation mode and Ctrl+key assignments can now be configured directly from the app settings
- **Quick Settings Access**: Added a quick toggle button (gear icon) to access settings directly from the keyboard
- **Enhanced Dictionary Management**: 
  - Added search functionality in the dictionary corrections interface
  - Custom dictionary entries now appear at the top of the list for easier access
  - Ricette TitanKeys: autocorrections that are valid in all the languages. (such as ppp-> %)
  - Added a lot of new unicode chara for sym layer page 2

### User Interface
- **UI Improvements**: Redesigned and improved the app's user interface, various issues solved (white font on light background in android light mode)
- **Multi-Language Support**: Added translations for multiple languages (may require manual review and corrections)

## Bug Fixes

- **Fixed Alt+Space Pop-up Issue**: Resolved a bug that caused an unwanted pop-up to appear when pressing Alt+Space or Alt+Letter+Space
- **Fixed Speech Recognition Focus**: Fixed an issue where Google Voice Typing would incorrectly shift focus to another app when activated


*This changelog covers all changes since the last release (v0.1-alpha).*

