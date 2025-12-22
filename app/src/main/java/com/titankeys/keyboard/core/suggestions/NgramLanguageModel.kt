package com.titankeys.keyboard.core.suggestions

import android.util.Log
import java.text.Normalizer
import java.util.Locale

/**
 * Result of a next-word prediction with score.
 */
data class PredictionResult(
    val word: String,
    val score: Double,
    val source: PredictionSource
)

enum class PredictionSource {
    TRIGRAM,  // Predicted from trigram model
    BIGRAM,   // Predicted from bigram model
    UNIGRAM   // Predicted from unigram (word frequency)
}

/**
 * N-gram language model for next-word prediction.
 * Supports bigrams (word1 -> word2) and trigrams (word1 word2 -> word3).
 * Falls back to unigram (single word frequency) when context is insufficient.
 */
class NgramLanguageModel(
    private val dictionaryRepository: DictionaryRepository,
    private val locale: Locale = Locale.getDefault(),
    private val debugLogging: Boolean = false
) {
    private val tag = "NgramLanguageModel"
    
    // N-gram data loaded from dictionary
    private var bigrams: Map<String, Map<String, Int>> = emptyMap()
    private var trigrams: Map<String, Map<String, Map<String, Int>>> = emptyMap()
    
    // Cache for normalized words
    private val normalizeCache = mutableMapOf<String, String>()
    
    /**
     * Loads n-gram data from the dictionary repository.
     * Should be called after dictionary is loaded.
     */
    fun loadNgrams(bigrams: Map<String, Map<String, Int>>?, trigrams: Map<String, Map<String, Map<String, Int>>>?) {
        this.bigrams = bigrams ?: emptyMap()
        this.trigrams = trigrams ?: emptyMap()
        
        if (debugLogging) {
            Log.d(tag, "Loaded n-grams: bigrams=${this.bigrams.size}, trigrams=${this.trigrams.size}")
        }
    }
    
    /**
     * Gets the loaded bigrams (for checking if loaded).
     */
    fun getBigrams(): Map<String, Map<String, Int>> = bigrams
    
    /**
     * Predicts the next word based on context.
     * Returns a list of predictions sorted by score (highest first).
     * 
     * @param context List of previous words (most recent last)
     * @param limit Maximum number of predictions to return
     * @return List of prediction results
     */
    fun predictNext(context: List<String>, limit: Int = 5): List<PredictionResult> {
        if (!dictionaryRepository.isReady) {
            return emptyList()
        }
        
        val normalizedContext = context.map { normalize(it) }
        
        // Try trigram first (if we have at least 2 words of context)
        if (normalizedContext.size >= 2) {
            val word1 = normalizedContext[normalizedContext.size - 2]
            val word2 = normalizedContext[normalizedContext.size - 1]
            val trigramResults = predictFromTrigram(word1, word2, limit * 2)
            if (trigramResults.isNotEmpty()) {
                return trigramResults.take(limit)
            }
        }
        
        // Fall back to bigram (if we have at least 1 word of context)
        if (normalizedContext.isNotEmpty()) {
            val lastWord = normalizedContext.last()
            val bigramResults = predictFromBigram(lastWord, limit * 2)
            if (bigramResults.isNotEmpty()) {
                return bigramResults.take(limit)
            }
        }
        
        // Final fallback to unigram (most frequent words)
        return predictFromUnigram(limit)
    }
    
    /**
     * Predicts next word using trigram model (word1 word2 -> word3).
     */
    private fun predictFromTrigram(word1: String, word2: String, limit: Int): List<PredictionResult> {
        val word1Norm = normalize(word1)
        val word2Norm = normalize(word2)
        
        val trigramMap = trigrams[word1Norm]?.get(word2Norm) ?: return emptyList()
        
        val results = mutableListOf<PredictionResult>()
        val totalFreq = trigramMap.values.sum().toDouble()
        
        if (totalFreq == 0.0) return emptyList()
        
        trigramMap.forEach { (word3, freq) ->
            val probability = freq / totalFreq
            // Boost by frequency to prefer common words
            val wordFreq = dictionaryRepository.getExactWordFrequency(word3)
            val score = probability * (1.0 + wordFreq / 1000.0)
            
            results.add(PredictionResult(
                word = word3,
                score = score,
                source = PredictionSource.TRIGRAM
            ))
        }
        
        return results.sortedByDescending { it.score }.take(limit)
    }
    
    /**
     * Predicts next word using bigram model (word1 -> word2).
     */
    private fun predictFromBigram(word1: String, limit: Int): List<PredictionResult> {
        val word1Norm = normalize(word1)
        val bigramMap = bigrams[word1Norm] ?: return emptyList()
        
        val results = mutableListOf<PredictionResult>()
        val totalFreq = bigramMap.values.sum().toDouble()
        
        if (totalFreq == 0.0) return emptyList()
        
        bigramMap.forEach { (word2, freq) ->
            val probability = freq / totalFreq
            // Boost by frequency to prefer common words
            val wordFreq = dictionaryRepository.getExactWordFrequency(word2)
            val score = probability * (1.0 + wordFreq / 1000.0)
            
            results.add(PredictionResult(
                word = word2,
                score = score,
                source = PredictionSource.BIGRAM
            ))
        }
        
        return results.sortedByDescending { it.score }.take(limit)
    }
    
    /**
     * Predicts next word using unigram model (most frequent words).
     */
    private fun predictFromUnigram(limit: Int): List<PredictionResult> {
        // Get top words from dictionary by frequency
        // This is a fallback when we have no context
        val allCandidates = dictionaryRepository.allCandidates()
            .sortedByDescending { dictionaryRepository.effectiveFrequency(it) }
            .take(limit * 2)
        
        return allCandidates.map { entry ->
            val freq = dictionaryRepository.effectiveFrequency(entry)
            PredictionResult(
                word = entry.word,
                score = freq / 1000.0,  // Normalize frequency to score
                source = PredictionSource.UNIGRAM
            )
        }.take(limit)
    }
    
    /**
     * Gets the bigram probability P(word2 | word1).
     */
    fun getBigramProbability(word1: String, word2: String): Double {
        val word1Norm = normalize(word1)
        val word2Norm = normalize(word2)
        
        val bigramMap = bigrams[word1Norm] ?: return 0.0
        val freq = bigramMap[word2Norm] ?: return 0.0
        val totalFreq = bigramMap.values.sum().toDouble()
        
        return if (totalFreq > 0) freq / totalFreq else 0.0
    }
    
    /**
     * Gets the trigram probability P(word3 | word1, word2).
     */
    fun getTrigramProbability(word1: String, word2: String, word3: String): Double {
        val word1Norm = normalize(word1)
        val word2Norm = normalize(word2)
        val word3Norm = normalize(word3)
        
        val trigramMap = trigrams[word1Norm]?.get(word2Norm) ?: return 0.0
        val freq = trigramMap[word3Norm] ?: return 0.0
        val totalFreq = trigramMap.values.sum().toDouble()
        
        return if (totalFreq > 0) freq / totalFreq else 0.0
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

