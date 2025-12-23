package com.titankeys.keyboard.core.suggestions

/**
 * Data classes and enums for grammar correction functionality
 */

enum class GrammarErrorType {
    VERB_TENSE,
    SUBJECT_VERB_AGREEMENT,
    ARTICLE_USAGE,
    PREPOSITION_ERROR,
    WORD_ORDER,
    PLURALIZATION,
    CAPITALIZATION,
    PUNCTUATION,
    SPELLING,  // For grammar-related spelling issues
    STYLE_IMPROVEMENT,
    UNKNOWN
}

enum class GrammarFallbackLevel {
    FULL_AI,        // Full TensorFlow Lite model
    HYBRID,         // AI + rule-based
    RULES_ONLY,     // Pattern-based rules only
    DISABLED        // Grammar checking off
}

data class GrammarSuggestion(
    val errorSpan: IntRange,           // Character positions of the error in the sentence
    val suggestedText: String,         // The corrected text
    val originalText: String,          // The original erroneous text
    val confidence: Float,             // Confidence score (0.0 to 1.0)
    val errorType: GrammarErrorType,   // Type of grammar error
    val explanation: String? = null,   // Optional explanation for the correction
    val alternatives: List<String> = emptyList() // Alternative suggestions
)

data class SentenceContext(
    val fullText: String,
    val sentenceStart: Int,
    val sentenceEnd: Int,
    val cursorPosition: Int,
    val language: String = "en"
)

data class GrammarSettings(
    val grammarCheckEnabled: Boolean = true,
    val automaticChecking: Boolean = true,
    val manualCheckEnabled: Boolean = true,
    val minConfidence: Float = 0.7f,
    val maxSuggestions: Int = 3,
    val contextWindowSize: Int = 50,
    val fallbackLevel: GrammarFallbackLevel = GrammarFallbackLevel.FULL_AI,
    val supportedLanguages: Set<String> = setOf("en", "es", "fr", "de", "it")
)

data class GrammarModelMetadata(
    val language: String,
    val modelVersion: String,
    val modelSizeBytes: Long,
    val expectedInferenceTimeMs: Long,
    val supportedErrorTypes: Set<GrammarErrorType>
)

data class GrammarPerformanceMetric(
    val timestamp: Long,
    val operation: String, // "inference", "tokenization", "postprocessing"
    val durationMs: Long,
    val success: Boolean,
    val modelLanguage: String? = null,
    val sentenceLength: Int? = null
)

enum class SuggestionSource {
    MAIN,       // Main dictionary
    USER,       // User dictionary
    GRAMMAR,    // Grammar correction
    NEXT_WORD   // Next word prediction
}