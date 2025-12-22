package com.titankeys.keyboard.core.suggestions

/**
 * Determines which suggestion mode to use based on context and settings.
 * 
 * - CURRENT_WORD: Show suggestions for the word being typed
 * - NEXT_WORD: Show predictions for the next word (after space/punctuation)
 * - HYBRID: Show both (next-word predictions + current-word if typing)
 */
enum class SuggestionMode {
    CURRENT_WORD,      // Show suggestions for word being typed
    NEXT_WORD,         // Show predictions for next word
    HYBRID             // Show both (next-word predictions + current-word if typing)
}

/**
 * Determines the appropriate suggestion mode based on current state.
 */
class AdaptiveSuggestionMode(
    private val settings: SuggestionSettings
) {
    /**
     * Determines the suggestion mode based on current state.
     * 
     * @param hasCurrentWord True if user is currently typing a word (has partial word)
     * @param contextLength Number of words in context (previous words in sentence)
     * @param nextWordPredictionEnabled Whether next-word prediction is enabled in settings
     * @return The appropriate suggestion mode
     */
    fun determineMode(
        hasCurrentWord: Boolean,
        contextLength: Int,
        nextWordPredictionEnabled: Boolean = true
    ): SuggestionMode {
        // If next-word prediction is disabled, always use current-word mode
        if (!nextWordPredictionEnabled) {
            return SuggestionMode.CURRENT_WORD
        }
        
        // If user is typing a word, show current-word suggestions
        if (hasCurrentWord) {
            // In hybrid mode, we could show both, but for now just show current-word
            // when actively typing
            return if (settings.nextWordPredictionMode == SuggestionMode.HYBRID) {
                SuggestionMode.CURRENT_WORD  // Prioritize current word when typing
            } else {
                SuggestionMode.CURRENT_WORD
            }
        }
        
        // No current word - user just finished a word (space/punctuation)
        // Show next-word predictions if we have context
        if (contextLength > 0) {
            return when (settings.nextWordPredictionMode) {
                SuggestionMode.NEXT_WORD -> SuggestionMode.NEXT_WORD
                SuggestionMode.HYBRID -> SuggestionMode.NEXT_WORD  // Show next-word when no current word
                SuggestionMode.CURRENT_WORD -> SuggestionMode.CURRENT_WORD  // User disabled next-word
            }
        }
        
        // No context and no current word - default to current-word mode
        return SuggestionMode.CURRENT_WORD
    }
}

