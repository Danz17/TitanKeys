package com.titankeys.keyboard.core.suggestions

data class DictionaryEntry(
    val word: String,
    val frequency: Int,
    val source: SuggestionSource = SuggestionSource.MAIN
)

enum class SuggestionSource {
    MAIN,       // Main dictionary
    USER,       // User dictionary
    GRAMMAR,    // Grammar correction
    NEXT_WORD   // Next word prediction
}
