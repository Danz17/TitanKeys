package com.titankeys.keyboard.core.suggestions

data class SuggestionSettings(
    val suggestionsEnabled: Boolean = true,
    val accentMatching: Boolean = true,
    val autoReplaceOnSpaceEnter: Boolean = false,
    val maxAutoReplaceDistance: Int = 1,
    val maxSuggestions: Int = 3,
    val useKeyboardProximity: Boolean = false,
    val useEditTypeRanking: Boolean = false,
    // Next-word prediction settings
    val nextWordPredictionEnabled: Boolean = true,
    val nextWordPredictionMode: SuggestionMode = SuggestionMode.NEXT_WORD,
    val userLearningEnabled: Boolean = true
)
