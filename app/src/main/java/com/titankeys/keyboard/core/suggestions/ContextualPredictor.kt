package com.titankeys.keyboard.core.suggestions

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Contextual AI predictor using lightweight transformer model for next-word prediction.
 * Uses TensorFlow Lite for efficient inference on mobile devices.
 * Provides better context understanding beyond n-grams with attention mechanisms.
 */
class ContextualPredictor(
    private val context: Context,
    private val dictionaryRepository: DictionaryRepository,
    private val locale: java.util.Locale = java.util.Locale.getDefault(),
    private val debugLogging: Boolean = false
) {
    private val tag = "ContextualPredictor"

    // Model configuration
    private val maxSequenceLength = 32  // Maximum input sequence length
    private val vocabSize = 30000       // Vocabulary size (should match model)
    private val hiddenSize = 256        // Hidden dimension
    private val maxPredictions = 10     // Maximum predictions to generate

    // Performance constraints
    private val maxInferenceTimeMs = 100L  // Maximum allowed inference time

    // TensorFlow Lite interpreter
    private var interpreter: Interpreter? = null
    private val modelLoaded = AtomicReference(false)

    // Caching
    private val predictionCache = mutableMapOf<String, CachedPrediction>()
    private val maxCacheSize = 1000
    private var cacheHits = 0
    private var cacheMisses = 0

    // Vocabulary mapping (word -> token id)
    private val vocabMap = mutableMapOf<String, Int>()
    private val reverseVocabMap = mutableMapOf<Int, String>()

    // Special tokens
    private val padToken = 0
    private val unkToken = 1
    private val clsToken = 2
    private val sepToken = 3
    private val maskToken = 4

    // Performance monitoring
    private var totalInferenceTime = 0L
    private var inferenceCount = 0

    data class CachedPrediction(
        val predictions: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Initializes the predictor by loading the TensorFlow Lite model.
     * Should be called on a background thread.
     */
    fun initialize(): Boolean {
        return try {
            if (modelLoaded.get()) return true

            // Load model from assets
            val modelBuffer = loadModelFromAssets("models/contextual_predictor.tflite")
            if (modelBuffer == null) {
                Log.w(tag, "Model file not found in assets")
                return false
            }

            // Create interpreter with optimized options
            val options = Interpreter.Options().apply {
                setNumThreads(2)  // Use 2 threads for mobile performance
                setUseXNNPACK(true)  // Enable XNNPACK acceleration
            }

            interpreter = Interpreter(modelBuffer, options)

            // Load vocabulary
            loadVocabulary()

            modelLoaded.set(true)
            Log.d(tag, "Contextual predictor initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize contextual predictor", e)
            false
        }
    }

    /**
     * Predicts next words using the transformer model.
     * Falls back to n-gram prediction if model fails or times out.
     *
     * @param context List of previous words (most recent last)
     * @param limit Maximum number of predictions to return
     * @param fallbackPredictor Fallback predictor for error cases
     * @return List of predicted words
     */
    fun predict(
        context: List<String>,
        limit: Int = 3,
        fallbackPredictor: NextWordPredictor? = null
    ): List<String> {
        // Privacy safeguard: Don't process if context contains sensitive patterns
        if (containsSensitiveContent(context)) {
            Log.d(tag, "Skipping prediction for sensitive content")
            return fallbackPredictor?.predict(context, limit) ?: emptyList()
        }

        if (!modelLoaded.get() || interpreter == null) {
            Log.w(tag, "Model not loaded, using fallback")
            return fallbackPredictor?.predict(context, limit) ?: emptyList()
        }

        // Check cache first
        val cacheKey = context.joinToString(" ")
        val cached = predictionCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 30000) { // 30s cache
            cacheHits++
            return cached.predictions.take(limit)
        }
        cacheMisses++

        return try {
            val startTime = System.currentTimeMillis()

            // Prepare input sequence
            val inputIds = prepareInputSequence(context)

            // Run inference
            val predictions = runInference(inputIds, limit)

            val inferenceTime = System.currentTimeMillis() - startTime
            totalInferenceTime += inferenceTime
            inferenceCount++

            // Check performance constraint
            if (inferenceTime > maxInferenceTimeMs) {
                Log.w(tag, "Inference too slow: ${inferenceTime}ms, using fallback")
                return fallbackPredictor?.predict(context, limit) ?: emptyList()
            }

            if (debugLogging) {
                Log.d(tag, "Inference completed in ${inferenceTime}ms, predictions: ${predictions.size}")
            }

            // Cache result
            cachePrediction(cacheKey, predictions)

            predictions
        } catch (e: Exception) {
            Log.e(tag, "Inference failed, using fallback", e)
            // Don't cache failed predictions
            fallbackPredictor?.predict(context, limit) ?: emptyList()
        }
    }

    /**
     * Prepares input sequence for the model.
     */
    private fun prepareInputSequence(context: List<String>): IntArray {
        val tokens = mutableListOf<Int>()

        // Add context words
        for (word in context.take(maxSequenceLength - 2)) { // Reserve space for CLS and SEP
            val tokenId = vocabMap.getOrDefault(word.lowercase(locale), unkToken)
            tokens.add(tokenId)
        }

        // Add special tokens
        tokens.add(0, clsToken)  // CLS token at start
        tokens.add(sepToken)     // SEP token at end

        // Pad or truncate to max length
        return when {
            tokens.size < maxSequenceLength -> {
                tokens.toIntArray() + IntArray(maxSequenceLength - tokens.size) { padToken }
            }
            tokens.size > maxSequenceLength -> {
                tokens.take(maxSequenceLength).toIntArray()
            }
            else -> tokens.toIntArray()
        }
    }

    /**
     * Runs inference on the prepared input.
     */
    private fun runInference(inputIds: IntArray, limit: Int): List<String> {
        val interpreter = interpreter ?: return emptyList()

        // Prepare input buffer
        val inputBuffer = ByteBuffer.allocateDirect(4 * maxSequenceLength)
            .order(ByteOrder.nativeOrder())
        inputIds.forEach { inputBuffer.putInt(it) }
        inputBuffer.rewind()

        // Prepare output buffer (logits for each token in vocabulary)
        val outputBuffer = ByteBuffer.allocateDirect(4 * vocabSize)
            .order(ByteOrder.nativeOrder())

        // Run inference
        interpreter.run(inputBuffer, outputBuffer)

        // Process output logits
        outputBuffer.rewind()
        val logits = FloatArray(vocabSize)
        for (i in 0 until vocabSize) {
            logits[i] = outputBuffer.getFloat()
        }

        // Convert logits to probabilities and get top predictions
        return getTopPredictions(logits, limit)
    }

    /**
     * Gets top predictions from logits.
     */
    private fun getTopPredictions(logits: FloatArray, limit: Int): List<String> {
        // Convert logits to probabilities using softmax
        val maxLogit = logits.maxOrNull() ?: 0f
        val expLogits = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
        val sumExp = expLogits.sum()
        val probabilities = expLogits.map { it / sumExp }

        // Get top predictions (excluding special tokens)
        val candidates = mutableListOf<Pair<String, Float>>()
        for (i in 5 until min(vocabSize, probabilities.size)) { // Skip special tokens 0-4
            val word = reverseVocabMap[i]
            if (word != null && word.isNotBlank()) {
                candidates.add(word to probabilities[i])
            }
        }

        // Sort by probability and filter to valid dictionary words
        return candidates
            .sortedByDescending { it.second }
            .take(limit * 2) // Get more candidates for filtering
            .map { it.first }
            .filter { isValidPrediction(it) }
            .take(limit)
    }

    /**
     * Checks if a prediction is valid (exists in dictionary).
     */
    private fun isValidPrediction(word: String): Boolean {
        return dictionaryRepository.getExactEntry(word) != null
    }

    /**
     * Loads the TensorFlow Lite model from assets.
     */
    private fun loadModelFromAssets(modelPath: String): MappedByteBuffer? {
        return try {
            context.assets.openFd(modelPath).use { fileDescriptor ->
                FileInputStream(fileDescriptor.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load model from assets: $modelPath", e)
            null
        }
    }

    /**
     * Loads vocabulary mapping from assets.
     */
    private fun loadVocabulary() {
        try {
            val vocabText = context.assets.open("models/vocab.txt").bufferedReader().use { it.readText() }
            vocabText.lines().forEachIndexed { index, word ->
                if (word.isNotBlank()) {
                    vocabMap[word] = index
                    reverseVocabMap[index] = word
                }
            }
            Log.d(tag, "Loaded vocabulary with ${vocabMap.size} words")
        } catch (e: Exception) {
            Log.e(tag, "Failed to load vocabulary", e)
        }
    }

    /**
     * Caches a prediction result.
     */
    private fun cachePrediction(key: String, predictions: List<String>) {
        if (predictionCache.size >= maxCacheSize) {
            // Remove oldest entries
            val oldestKey = predictionCache.entries
                .minByOrNull { it.value.timestamp }?.key
            oldestKey?.let { predictionCache.remove(it) }
        }
        predictionCache[key] = CachedPrediction(predictions)
    }

    /**
     * Gets performance statistics.
     */
    fun getPerformanceStats(): Map<String, Any> {
        val avgInferenceTime = if (inferenceCount > 0) totalInferenceTime / inferenceCount else 0L
        val cacheHitRate = if (cacheHits + cacheMisses > 0) cacheHits.toFloat() / (cacheHits + cacheMisses) else 0f

        return mapOf(
            "average_inference_time_ms" to avgInferenceTime,
            "total_inferences" to inferenceCount,
            "cache_hit_rate" to cacheHitRate,
            "cache_size" to predictionCache.size,
            "model_loaded" to modelLoaded.get()
        )
    }

    /**
     * Clears caches and resets performance counters.
     */
    fun clearCache() {
        predictionCache.clear()
        cacheHits = 0
        cacheMisses = 0
        totalInferenceTime = 0L
        inferenceCount = 0
    }

    /**
     * Checks if the context contains sensitive content that should not be processed.
     * Privacy safeguard to prevent processing of potentially sensitive information.
     */
    private fun containsSensitiveContent(context: List<String>): Boolean {
        // Simple pattern-based detection for common sensitive content
        val sensitivePatterns = listOf(
            Regex("\\b\\d{3,4}\\b"), // Credit card numbers, SSN parts
            Regex("\\b\\d{4}\\s\\d{4}\\s\\d{4}\\s\\d{4}\\b"), // Full credit card
            Regex("\\bpassword\\b", RegexOption.IGNORE_CASE),
            Regex("\\bsecret\\b", RegexOption.IGNORE_CASE),
            Regex("\\bprivate\\b", RegexOption.IGNORE_CASE),
            Regex("\\bconfidential\\b", RegexOption.IGNORE_CASE)
        )

        val contextText = context.joinToString(" ").lowercase(locale)
        return sensitivePatterns.any { it.containsMatchIn(contextText) }
    }

    /**
     * Releases resources.
     */
    fun shutdown() {
        try {
            interpreter?.close()
            interpreter = null
            modelLoaded.set(false)
            clearCache()
            Log.d(tag, "Contextual predictor shutdown")
        } catch (e: Exception) {
            Log.e(tag, "Error during shutdown", e)
        }
    }
}