package com.titankeys.keyboard.core.suggestions

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Core engine for AI-powered grammar correction using TensorFlow Lite
 */
class GrammarCorrectionEngine(
    private val context: Context,
    private val modelManager: GrammarModelManager,
    private val settings: GrammarSettings
) {
    companion object {
        private const val TAG = "GrammarCorrectionEngine"
        private const val GRAMMAR_TIMEOUT_MS = 100L
        private const val MIN_SENTENCE_LENGTH = 10
        private const val MAX_SENTENCE_LENGTH = 500
        private const val GRAMMAR_SCORE_MULTIPLIER = 2.0

        // Common irregular verb corrections (incorrect -> correct)
        private val IRREGULAR_VERBS = mapOf(
            "goed" to "went", "wented" to "went", "runned" to "ran", "runed" to "ran",
            "thinked" to "thought", "buyed" to "bought", "bringed" to "brought",
            "catched" to "caught", "teached" to "taught", "leaved" to "left",
            "sleeped" to "slept", "keeped" to "kept", "feeled" to "felt",
            "falled" to "fell", "telled" to "told", "selled" to "sold",
            "heared" to "heard", "finded" to "found", "knowed" to "knew",
            "growed" to "grew", "throwed" to "threw", "drawed" to "drew",
            "flyed" to "flew", "drived" to "drove", "writed" to "wrote",
            "rided" to "rode", "speaked" to "spoke", "breaked" to "broke",
            "choosed" to "chose", "freezed" to "froze", "gived" to "gave",
            "taked" to "took", "eated" to "ate", "seed" to "saw", "sawed" to "saw",
            "drinked" to "drank", "singed" to "sang", "ringed" to "rang",
            "swinged" to "swung", "bited" to "bit", "hided" to "hid",
            "layed" to "lay", "payed" to "paid", "sayed" to "said",
            "maked" to "made", "builded" to "built", "sended" to "sent",
            "spended" to "spent", "lended" to "lent", "bended" to "bent",
            "meaned" to "meant", "dealed" to "dealt", "feeded" to "fed",
            "readed" to "read", "leaded" to "led", "holded" to "held",
            "standed" to "stood", "understanded" to "understood", "winned" to "won",
            "becomed" to "became", "beginned" to "began", "forgeted" to "forgot",
            "getted" to "got", "sitted" to "sat", "setted" to "set",
            "cutted" to "cut", "putted" to "put", "hurted" to "hurt",
            "costed" to "cost", "shutted" to "shut", "splitted" to "split",
            "spreaded" to "spread", "striked" to "struck", "sticked" to "stuck",
            "digged" to "dug", "hitted" to "hit", "letted" to "let",
            "quitted" to "quit", "ridded" to "rid", "sheded" to "shed"
        )

        // Common misspellings and confusion pairs
        private val COMMON_MISSPELLINGS = mapOf(
            "alot" to "a lot", "definately" to "definitely", "definatly" to "definitely",
            "seperate" to "separate", "occured" to "occurred", "recieve" to "receive",
            "beleive" to "believe", "belive" to "believe", "accomodate" to "accommodate",
            "occurence" to "occurrence", "neccessary" to "necessary", "necessery" to "necessary",
            "independant" to "independent", "goverment" to "government",
            "enviroment" to "environment", "tommorrow" to "tomorrow", "tommorow" to "tomorrow",
            "untill" to "until", "wierd" to "weird", "truely" to "truly",
            "arguement" to "argument", "begining" to "beginning", "bussiness" to "business",
            "buisness" to "business", "calender" to "calendar", "catagory" to "category",
            "comming" to "coming", "commited" to "committed", "concious" to "conscious",
            "embarass" to "embarrass", "existance" to "existence", "experiance" to "experience",
            "foriegn" to "foreign", "freind" to "friend", "grammer" to "grammar",
            "happend" to "happened", "immediatly" to "immediately", "intresting" to "interesting",
            "knowlege" to "knowledge", "mispell" to "misspell", "noticable" to "noticeable",
            "ocasion" to "occasion", "occassion" to "occasion", "persue" to "pursue",
            "posession" to "possession", "prefered" to "preferred", "priviledge" to "privilege",
            "recomend" to "recommend", "reccommend" to "recommend", "refered" to "referred",
            "relevent" to "relevant", "rember" to "remember", "remeber" to "remember",
            "similer" to "similar", "similiar" to "similar", "sucess" to "success",
            "suprise" to "surprise", "thru" to "through", "tounge" to "tongue",
            "vaccum" to "vacuum", "writting" to "writing", "thier" to "their"
        )

        // Contraction fixes (missing apostrophes)
        private val CONTRACTION_FIXES = mapOf(
            "youre" to "you're", "dont" to "don't", "cant" to "can't", "wont" to "won't",
            "isnt" to "isn't", "wasnt" to "wasn't", "werent" to "weren't",
            "didnt" to "didn't", "doesnt" to "doesn't", "hasnt" to "hasn't",
            "havent" to "haven't", "hadnt" to "hadn't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "arent" to "aren't",
            "im" to "I'm", "ive" to "I've", "ill" to "I'll", "id" to "I'd",
            "thats" to "that's", "whats" to "what's", "hes" to "he's",
            "shes" to "she's", "lets" to "let's", "whos" to "who's"
        )

        // Context patterns for your/you're confusion
        private val YOUR_YOURE_PATTERNS = mapOf(
            "your welcome" to "you're welcome", "your right" to "you're right",
            "your wrong" to "you're wrong", "your the" to "you're the",
            "your not" to "you're not", "your going" to "you're going",
            "your coming" to "you're coming", "your doing" to "you're doing",
            "your being" to "you're being", "your looking" to "you're looking",
            "your making" to "you're making", "your getting" to "you're getting",
            "if your " to "if you're ", "think your" to "think you're"
        )

        // Context patterns for their/there/they're confusion
        private val THEIR_PATTERNS = mapOf(
            "their is" to "there is", "their are" to "there are",
            "their was" to "there was", "their were" to "there were",
            "their going" to "they're going", "their coming" to "they're coming",
            "their not" to "they're not", "over their" to "over there",
            "go their" to "go there", "went their" to "went there"
        )

        // Context patterns for its/it's confusion
        private val ITS_PATTERNS = mapOf(
            "its a " to "it's a ", "its an " to "it's an ", "its the" to "it's the",
            "its not" to "it's not", "its been" to "it's been", "its going" to "it's going",
            "its time" to "it's time", "its just" to "it's just", "its only" to "it's only",
            "its too" to "it's too", "its so" to "it's so", "its very" to "it's very",
            "its okay" to "it's okay", "its ok" to "it's ok", "its fine" to "it's fine",
            "its good" to "it's good", "its great" to "it's great",
            "if its" to "if it's", "think its" to "think it's"
        )

        // Words requiring "an" (start with vowel sounds) - exceptions that use "a"
        private val AN_EXCEPTIONS = setOf(
            "university", "unicorn", "uniform", "united", "unique", "union", "unit",
            "universal", "user", "usual", "utility", "european", "one", "once"
        )
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val performanceMonitor = GrammarPerformanceMonitor()

    /**
     * Analyze a sentence for grammar errors and return suggestions
     */
    fun analyzeSentence(sentence: String, cursorPosition: Int): List<GrammarSuggestion> {
        if (!settings.grammarCheckEnabled) {
            return emptyList()
        }

        // Validate sentence
        if (!isValidSentence(sentence)) {
            return emptyList()
        }

        return when (settings.fallbackLevel) {
            GrammarFallbackLevel.FULL_AI -> analyzeWithAI(sentence, cursorPosition)
            GrammarFallbackLevel.HYBRID -> analyzeHybrid(sentence, cursorPosition)
            GrammarFallbackLevel.RULES_ONLY -> analyzeWithRules(sentence, cursorPosition)
            GrammarFallbackLevel.DISABLED -> emptyList()
        }
    }

    /**
     * Analyze sentence using TensorFlow Lite model
     */
    private fun analyzeWithAI(sentence: String, cursorPosition: Int): List<GrammarSuggestion> {
        val startTime = System.nanoTime()

        return try {
            // Detect language (simplified - would use more sophisticated detection)
            val language = detectLanguage(sentence)

            if (!modelManager.isModelAvailable(language)) {
                Log.d(TAG, "No AI model available for language: $language, falling back to rules")
                return analyzeWithRules(sentence, cursorPosition)
            }

            // Get model (this would be async in real implementation)
            val model = runBlocking { modelManager.getModelForLanguage(language) }
            if (model == null) {
                Log.w(TAG, "Failed to load model for language: $language")
                return analyzeWithRules(sentence, cursorPosition)
            }

            // Tokenize input
            val tokenizedInput = tokenizeInput(sentence, language)
            val inputBuffer = prepareModelInput(tokenizedInput, model)

            // Prepare output buffers
            val outputBuffers = prepareOutputBuffers(model)

            // Run inference with timeout
            val inferenceStart = System.nanoTime()
            val success = runBlocking {
                withTimeoutOrNull(GRAMMAR_TIMEOUT_MS) {
                    model.runInference(inputBuffer, outputBuffers)
                }
            } ?: false

            val inferenceTime = (System.nanoTime() - inferenceStart) / 1_000_000
            performanceMonitor.recordInferenceTime(language, inferenceTime, success)

            if (!success) {
                Log.w(TAG, "Inference failed or timed out for language: $language")
                return analyzeWithRules(sentence, cursorPosition)
            }

            // Process results
            val suggestions = processModelOutput(outputBuffers, sentence, language)

            // Filter and rank suggestions
            val filteredSuggestions = suggestions
                .filter { it.confidence >= settings.minConfidence }
                .sortedByDescending { it.confidence }
                .take(settings.maxSuggestions)

            val totalTime = (System.nanoTime() - startTime) / 1_000_000
            Log.d(TAG, "Grammar analysis completed in ${totalTime}ms, found ${filteredSuggestions.size} suggestions")

            filteredSuggestions

        } catch (e: Exception) {
            Log.e(TAG, "Error during AI grammar analysis", e)
            performanceMonitor.recordInferenceTime("unknown", -1, false)
            analyzeWithRules(sentence, cursorPosition)
        }
    }

    /**
     * Hybrid analysis combining AI and rule-based approaches
     */
    private fun analyzeHybrid(sentence: String, cursorPosition: Int): List<GrammarSuggestion> {
        val aiSuggestions = analyzeWithAI(sentence, cursorPosition)
        val ruleSuggestions = analyzeWithRules(sentence, cursorPosition)

        // Combine and deduplicate suggestions
        return (aiSuggestions + ruleSuggestions)
            .distinctBy { it.errorSpan }
            .sortedByDescending { it.confidence }
            .take(settings.maxSuggestions)
    }

    /**
     * Rule-based grammar analysis as fallback
     */
    private fun analyzeWithRules(sentence: String, cursorPosition: Int): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()

        // Comprehensive rule checks
        suggestions.addAll(checkIrregularVerbs(sentence))
        suggestions.addAll(checkCommonMisspellings(sentence))
        suggestions.addAll(checkContractions(sentence))
        suggestions.addAll(checkContextualConfusions(sentence))
        suggestions.addAll(checkArticles(sentence))

        return suggestions.sortedByDescending { it.confidence }
    }

    /**
     * Validate if sentence is suitable for grammar checking
     */
    private fun isValidSentence(sentence: String): Boolean {
        if (sentence.length < MIN_SENTENCE_LENGTH || sentence.length > MAX_SENTENCE_LENGTH) {
            return false
        }

        // Skip sentences that are likely code, URLs, etc.
        if (sentence.contains("://") || sentence.contains("```") || sentence.contains("@")) {
            return false
        }

        // Must contain at least one letter
        return sentence.any { it.isLetter() }
    }

    /**
     * Simple language detection (would be more sophisticated in real implementation)
     */
    private fun detectLanguage(sentence: String): String {
        // Default to English for now
        // In real implementation, would use language detection library
        return "en"
    }

    /**
     * Tokenize input for model (simplified)
     */
    private fun tokenizeInput(sentence: String, language: String): List<String> {
        // Simplified tokenization - real implementation would use proper tokenizer
        return sentence.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    /**
     * Prepare input buffer for TensorFlow Lite model
     */
    private fun prepareModelInput(tokens: List<String>, model: GrammarModel): ByteBuffer {
        val inputShape = model.getInputShape()
        val buffer = ByteBuffer.allocateDirect(inputShape[0] * inputShape[1] * 4) // FLOAT32
        buffer.order(ByteOrder.nativeOrder())

        // Simplified input preparation
        // Real implementation would properly encode tokens
        for (i in 0 until min(tokens.size, inputShape[1])) {
            // Placeholder encoding - real implementation would use vocabulary
            buffer.putFloat(tokens[i].length.toFloat())
        }

        // Pad remaining space
        while (buffer.hasRemaining()) {
            buffer.putFloat(0.0f)
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Prepare output buffers for model inference
     */
    private fun prepareOutputBuffers(model: GrammarModel): Array<TensorBuffer> {
        val outputShapes = (0 until model.interpreter.outputTensorCount).map { model.getOutputShape(it) }

        return outputShapes.map { shape ->
            val bufferSize = shape.reduce { acc, i -> acc * i } * 4 // FLOAT32
            TensorBuffer.createFixedSize(shape, org.tensorflow.lite.DataType.FLOAT32)
        }.toTypedArray()
    }

    /**
     * Process model output into grammar suggestions
     */
    private fun processModelOutput(
        outputBuffers: Array<TensorBuffer>,
        sentence: String,
        language: String
    ): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()

        // Simplified output processing
        // Real implementation would parse actual model outputs
        if (outputBuffers.isNotEmpty()) {
            val outputData = outputBuffers[0].floatArray

            // Use rule-based suggestions as fallback until model properly integrated
            suggestions.addAll(checkIrregularVerbs(sentence))
        }

        return suggestions
    }

    // ==================== RULE-BASED CHECKERS ====================

    /**
     * Check for incorrect irregular verb forms
     */
    private fun checkIrregularVerbs(sentence: String): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()
        val lowerSentence = sentence.lowercase()

        for ((incorrect, correct) in IRREGULAR_VERBS) {
            var searchStart = 0
            while (true) {
                val index = lowerSentence.indexOf(incorrect, searchStart)
                if (index == -1) break

                // Check word boundaries
                val beforeOk = index == 0 || !lowerSentence[index - 1].isLetter()
                val afterOk = index + incorrect.length >= lowerSentence.length ||
                    !lowerSentence[index + incorrect.length].isLetter()

                if (beforeOk && afterOk) {
                    suggestions.add(GrammarSuggestion(
                        errorSpan = index until (index + incorrect.length),
                        suggestedText = correct,
                        originalText = sentence.substring(index, index + incorrect.length),
                        confidence = 0.95f,
                        errorType = GrammarErrorType.VERB_TENSE,
                        explanation = "Incorrect past tense form"
                    ))
                }
                searchStart = index + 1
            }
        }

        return suggestions
    }

    /**
     * Check for common misspellings
     */
    private fun checkCommonMisspellings(sentence: String): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()
        val lowerSentence = sentence.lowercase()

        for ((incorrect, correct) in COMMON_MISSPELLINGS) {
            var searchStart = 0
            while (true) {
                val index = lowerSentence.indexOf(incorrect, searchStart)
                if (index == -1) break

                // Check word boundaries
                val beforeOk = index == 0 || !lowerSentence[index - 1].isLetter()
                val afterOk = index + incorrect.length >= lowerSentence.length ||
                    !lowerSentence[index + incorrect.length].isLetter()

                if (beforeOk && afterOk) {
                    suggestions.add(GrammarSuggestion(
                        errorSpan = index until (index + incorrect.length),
                        suggestedText = correct,
                        originalText = sentence.substring(index, index + incorrect.length),
                        confidence = 0.92f,
                        errorType = GrammarErrorType.SPELLING,
                        explanation = "Common misspelling"
                    ))
                }
                searchStart = index + 1
            }
        }

        return suggestions
    }

    /**
     * Check for missing apostrophes in contractions
     */
    private fun checkContractions(sentence: String): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()
        val lowerSentence = sentence.lowercase()

        for ((incorrect, correct) in CONTRACTION_FIXES) {
            var searchStart = 0
            while (true) {
                val index = lowerSentence.indexOf(incorrect, searchStart)
                if (index == -1) break

                // Check word boundaries
                val beforeOk = index == 0 || !lowerSentence[index - 1].isLetter()
                val afterOk = index + incorrect.length >= lowerSentence.length ||
                    !lowerSentence[index + incorrect.length].isLetter()

                if (beforeOk && afterOk) {
                    // Preserve original case for "I'm", "I've", etc.
                    val replacement = if (correct.startsWith("I'")) correct else correct
                    suggestions.add(GrammarSuggestion(
                        errorSpan = index until (index + incorrect.length),
                        suggestedText = replacement,
                        originalText = sentence.substring(index, index + incorrect.length),
                        confidence = 0.88f,
                        errorType = GrammarErrorType.PUNCTUATION,
                        explanation = "Missing apostrophe in contraction"
                    ))
                }
                searchStart = index + 1
            }
        }

        return suggestions
    }

    /**
     * Check for contextual confusions (your/you're, their/there/they're, its/it's)
     */
    private fun checkContextualConfusions(sentence: String): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()
        val lowerSentence = sentence.lowercase()

        // Check your/you're patterns
        for ((incorrect, correct) in YOUR_YOURE_PATTERNS) {
            val index = lowerSentence.indexOf(incorrect)
            if (index != -1) {
                suggestions.add(GrammarSuggestion(
                    errorSpan = index until (index + incorrect.length),
                    suggestedText = correct,
                    originalText = sentence.substring(index, index + incorrect.length),
                    confidence = 0.85f,
                    errorType = GrammarErrorType.WORD_CHOICE,
                    explanation = "Possible confusion: 'your' (possessive) vs 'you're' (you are)"
                ))
            }
        }

        // Check their/there/they're patterns
        for ((incorrect, correct) in THEIR_PATTERNS) {
            val index = lowerSentence.indexOf(incorrect)
            if (index != -1) {
                suggestions.add(GrammarSuggestion(
                    errorSpan = index until (index + incorrect.length),
                    suggestedText = correct,
                    originalText = sentence.substring(index, index + incorrect.length),
                    confidence = 0.85f,
                    errorType = GrammarErrorType.WORD_CHOICE,
                    explanation = "Possible confusion: their/there/they're"
                ))
            }
        }

        // Check its/it's patterns
        for ((incorrect, correct) in ITS_PATTERNS) {
            val index = lowerSentence.indexOf(incorrect)
            if (index != -1) {
                suggestions.add(GrammarSuggestion(
                    errorSpan = index until (index + incorrect.length),
                    suggestedText = correct,
                    originalText = sentence.substring(index, index + incorrect.length),
                    confidence = 0.85f,
                    errorType = GrammarErrorType.WORD_CHOICE,
                    explanation = "Possible confusion: 'its' (possessive) vs 'it's' (it is)"
                ))
            }
        }

        return suggestions
    }

    /**
     * Check for article usage (a/an)
     */
    private fun checkArticles(sentence: String): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()
        val words = sentence.split("\\s+".toRegex())

        for (i in 0 until words.size - 1) {
            val article = words[i].lowercase()
            val nextWord = words[i + 1].lowercase().trimStart('"', '\'', '(', '[')

            if (nextWord.isEmpty()) continue

            // Check "a" before vowel sounds
            if (article == "a" && nextWord.isNotEmpty()) {
                val firstChar = nextWord[0]
                val startsWithVowel = firstChar in listOf('a', 'e', 'i', 'o', 'u')
                val isException = AN_EXCEPTIONS.any { nextWord.startsWith(it) }

                if (startsWithVowel && !isException) {
                    // Find position in original sentence
                    val articleIndex = sentence.lowercase().indexOf(" $article $nextWord")
                    if (articleIndex != -1) {
                        val actualIndex = articleIndex + 1 // Skip the leading space
                        suggestions.add(GrammarSuggestion(
                            errorSpan = actualIndex until (actualIndex + 1),
                            suggestedText = "an",
                            originalText = "a",
                            confidence = 0.90f,
                            errorType = GrammarErrorType.ARTICLE_USAGE,
                            explanation = "Use 'an' before words starting with vowel sounds"
                        ))
                    }
                }
            }

            // Check "an" before consonant sounds
            if (article == "an" && nextWord.isNotEmpty()) {
                val firstChar = nextWord[0]
                val startsWithConsonant = firstChar !in listOf('a', 'e', 'i', 'o', 'u')
                val isException = AN_EXCEPTIONS.any { nextWord.startsWith(it) }

                if (startsWithConsonant || isException) {
                    val articleIndex = sentence.lowercase().indexOf(" $article $nextWord")
                    if (articleIndex != -1) {
                        val actualIndex = articleIndex + 1
                        suggestions.add(GrammarSuggestion(
                            errorSpan = actualIndex until (actualIndex + 2),
                            suggestedText = "a",
                            originalText = "an",
                            confidence = 0.90f,
                            errorType = GrammarErrorType.ARTICLE_USAGE,
                            explanation = "Use 'a' before words starting with consonant sounds"
                        ))
                    }
                }
            }
        }

        return suggestions
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        scope.cancel()
        modelManager.unloadAllModels()
    }
}
