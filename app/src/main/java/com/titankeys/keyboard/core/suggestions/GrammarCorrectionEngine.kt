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
            val success = withTimeoutOrNull(GRAMMAR_TIMEOUT_MS) {
                model.runInference(inputBuffer, outputBuffers)
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

        // Basic rule checks (simplified examples)
        suggestions.addAll(checkVerbTense(sentence))
        suggestions.addAll(checkArticles(sentence))
        suggestions.addAll(checkSubjectVerbAgreement(sentence))

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

            // Mock suggestions based on specification examples
            if (sentence.contains("goed")) {
                suggestions.add(GrammarSuggestion(
                    errorSpan = sentence.indexOf("goed")..sentence.indexOf("goed") + 3,
                    suggestedText = "went",
                    originalText = "goed",
                    confidence = 0.95f,
                    errorType = GrammarErrorType.VERB_TENSE,
                    explanation = "Incorrect past tense of 'go'"
                ))
            }
        }

        return suggestions
    }

    // Rule-based checkers (simplified examples)

    private fun checkVerbTense(sentence: String): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()

        // Simple pattern: "goed" -> "went"
        val goedIndex = sentence.indexOf("goed")
        if (goedIndex != -1) {
            suggestions.add(GrammarSuggestion(
                errorSpan = goedIndex..goedIndex + 3,
                suggestedText = "went",
                originalText = "goed",
                confidence = 0.9f,
                errorType = GrammarErrorType.VERB_TENSE
            ))
        }

        return suggestions
    }

    private fun checkArticles(sentence: String): List<GrammarSuggestion> {
        // Simplified article checking
        return emptyList()
    }

    private fun checkSubjectVerbAgreement(sentence: String): List<GrammarSuggestion> {
        // Simplified subject-verb agreement checking
        return emptyList()
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        scope.cancel()
        modelManager.unloadAllModels()
    }
}