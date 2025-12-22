package com.titankeys.keyboard.core.suggestions

import android.util.Log
import java.text.Normalizer
import java.util.Locale

/**
 * Orchestrates next-word predictions by combining n-gram language model with user learning.
 * Ranks predictions by probability, user frequency, and dictionary frequency.
 */
class NextWordPredictor(
    private val ngramModel: NgramLanguageModel,
    private val userLearningStore: UserLearningStore,
    private val dictionaryRepository: DictionaryRepository,
    private val locale: Locale = Locale.getDefault(),
    private val debugLogging: Boolean = false
) {
    private val tag = "NextWordPredictor"
    
    // Cache for normalized words
    private val normalizeCache = mutableMapOf<String, String>()
    
    // User learning boost factor (multiply user frequencies by this)
    private val userLearningBoost = 2.0
    
    /**
     * Predicts the next word based on context.
     * Combines base n-gram model with user learning data.
     * 
     * @param context List of previous words (most recent last)
     * @param limit Maximum number of predictions to return
     * @return List of predicted words sorted by score (highest first)
     */
    fun predict(context: List<String>, limit: Int = 3): List<String> {
        if (!dictionaryRepository.isReady) {
            return emptyList()
        }
        
        if (context.isEmpty()) {
            // No context, return empty (or could return most frequent words)
            return emptyList()
        }
        
        val normalizedContext = context.map { normalize(it) }
        
        // Get base predictions from n-gram model
        val basePredictions = ngramModel.predictNext(normalizedContext, limit * 3)
        
        // Merge with user learning data
        val enhancedPredictions = mutableMapOf<String, Double>()
        
        // Add base predictions
        basePredictions.forEach { prediction ->
            val word = prediction.word
            val baseScore = prediction.score
            
            // Apply user learning boost
            val userBoost = getUserLearningBoost(normalizedContext, word)
            val enhancedScore = baseScore * (1.0 + userBoost * userLearningBoost)
            
            enhancedPredictions[word] = enhancedScore
        }
        
        // Add user-specific predictions that might not be in base model
        addUserPredictions(normalizedContext, enhancedPredictions, limit)
        
        // Sort by score and return top predictions
        val sorted = enhancedPredictions.entries
            .sortedByDescending { it.value }
            .take(limit)
        
        val result = sorted.map { it.key }
        
        if (debugLogging && result.isNotEmpty()) {
            Log.d(tag, "Predicted next words for context '${normalizedContext.joinToString(" ")}': ${result.joinToString(", ")}")
        }
        
        return result
    }
    
    /**
     * Gets the user learning boost factor for a word given context.
     * Returns a value between 0.0 and 1.0 based on user frequency.
     */
    private fun getUserLearningBoost(context: List<String>, word: String): Double {
        if (context.isEmpty()) return 0.0
        
        val wordNorm = normalize(word)
        var maxBoost = 0.0
        
        // Check trigram (if we have 2+ words of context)
        if (context.size >= 2) {
            val word1 = context[context.size - 2]
            val word2 = context[context.size - 1]
            val userFreq = userLearningStore.getTrigramFrequency(word1, word2, wordNorm)
            if (userFreq > 0) {
                // Normalize frequency to 0-1 range (log scale to prevent dominance)
                maxBoost = kotlin.math.max(maxBoost, kotlin.math.log10((userFreq + 1).toDouble()) / 3.0)
            }
        }
        
        // Check bigram (if we have 1+ words of context)
        if (context.isNotEmpty()) {
            val lastWord = context.last()
            val userFreq = userLearningStore.getBigramFrequency(lastWord, wordNorm)
            if (userFreq > 0) {
                val boost = kotlin.math.log10((userFreq + 1).toDouble()) / 3.0
                maxBoost = kotlin.math.max(maxBoost, boost)
            }
        }
        
        return maxBoost.coerceIn(0.0, 1.0)
    }
    
    /**
     * Adds user-specific predictions that might not be in the base model.
     */
    private fun addUserPredictions(
        context: List<String>,
        predictions: MutableMap<String, Double>,
        limit: Int
    ) {
        if (context.isEmpty()) return
        
        val userBigrams = userLearningStore.getUserBigrams()
        val userTrigrams = userLearningStore.getUserTrigrams()
        
        // Check trigram predictions
        if (context.size >= 2) {
            val word1 = context[context.size - 2]
            val word2 = context[context.size - 1]
            val trigramMap = userTrigrams[word1]?.get(word2)
            trigramMap?.forEach { (word3, freq) ->
                if (!predictions.containsKey(word3)) {
                    // User has typed this sequence, but it's not in base model
                    // Give it a moderate score based on user frequency
                    val score = kotlin.math.log10((freq + 1).toDouble()) / 2.0
                    predictions[word3] = score
                }
            }
        }
        
        // Check bigram predictions
        if (context.isNotEmpty()) {
            val lastWord = context.last()
            val bigramMap = userBigrams[lastWord]
            bigramMap?.forEach { (word2, freq) ->
                if (!predictions.containsKey(word2)) {
                    // User has typed this sequence, but it's not in base model
                    val score = kotlin.math.log10((freq + 1).toDouble()) / 2.0
                    predictions[word2] = score
                }
            }
        }
    }
    
    /**
     * Normalizes a word: lowercase, remove accents, keep only letters.
     */
    private fun normalize(word: String): String {
        return normalizeCache.getOrPut(word) {
            val normalizedApostrophes = word
                .replace("'", "'")
                .replace("'", "'")
                .replace("ʼ", "'")
            
            val lowercased = normalizedApostrophes.lowercase(locale)
            
            val withoutAccents = Normalizer.normalize(lowercased, Normalizer.Form.NFD)
                .replace("\\p{Mn}".toRegex(), "")
            
            // Keep only letters (remove apostrophes for n-gram matching)
            withoutAccents.replace("[^\\p{L}]".toRegex(), "")
        }
    }
}

