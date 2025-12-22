package com.titankeys.keyboard.core.suggestions

import android.util.Log
import java.text.Normalizer
import java.util.Locale

/**
 * Tracks the context (previous words) in the current sentence for next-word prediction.
 * Maintains a sliding window of the last 2-3 words and resets on sentence boundaries.
 */
class ContextTracker(
    private val maxContextLength: Int = 3,
    private val locale: Locale = Locale.getDefault(),
    private val debugLogging: Boolean = false
) {
    private val contextWords = mutableListOf<String>()
    private val tag = "ContextTracker"
    
    /**
     * Adds a word to the context. The word should be normalized (lowercase, no accents).
     */
    fun addWord(word: String) {
        if (word.isBlank()) return
        
        val normalized = normalizeWord(word)
        if (normalized.isBlank()) return
        
        contextWords.add(normalized)
        
        // Keep only the last maxContextLength words
        if (contextWords.size > maxContextLength) {
            contextWords.removeAt(0)
        }
        
        if (debugLogging) {
            Log.d(tag, "Added word: '$word' -> '$normalized', context: ${contextWords.joinToString(" ")}")
        }
    }
    
    /**
     * Returns the current context as a list of words (most recent last).
     * Returns empty list if no context available.
     */
    fun getContext(): List<String> {
        return contextWords.toList()
    }
    
    /**
     * Returns the last N words, where N is at most maxContextLength.
     */
    fun getLastWords(count: Int): List<String> {
        val start = (contextWords.size - count).coerceAtLeast(0)
        return contextWords.subList(start, contextWords.size)
    }
    
    /**
     * Returns the last word in context, or null if empty.
     */
    fun getLastWord(): String? {
        return contextWords.lastOrNull()
    }
    
    /**
     * Returns the last two words in context, or empty list if insufficient context.
     */
    fun getLastTwoWords(): List<String> {
        return if (contextWords.size >= 2) {
            contextWords.takeLast(2)
        } else {
            emptyList()
        }
    }
    
    /**
     * Resets the context (clears all words).
     */
    fun reset() {
        if (contextWords.isNotEmpty()) {
            if (debugLogging) {
                Log.d(tag, "Resetting context: ${contextWords.joinToString(" ")}")
            }
            contextWords.clear()
        }
    }
    
    /**
     * Called when a sentence boundary is reached (., !, ?, newline).
     * Resets the context to start a new sentence.
     */
    fun onSentenceBoundary() {
        if (debugLogging) {
            Log.d(tag, "Sentence boundary reached, resetting context")
        }
        reset()
    }
    
    /**
     * Checks if context is empty.
     */
    fun isEmpty(): Boolean = contextWords.isEmpty()
    
    /**
     * Returns the number of words in context.
     */
    fun size(): Int = contextWords.size
    
    /**
     * Normalizes a word: lowercase, remove accents, keep only letters and apostrophes.
     */
    private fun normalizeWord(word: String): String {
        // Normalize apostrophes
        val normalizedApostrophes = word
            .replace("'", "'")
            .replace("'", "'")
            .replace("ʼ", "'")
        
        // Lowercase
        val lowercased = normalizedApostrophes.lowercase(locale)
        
        // Remove accents
        val withoutAccents = Normalizer.normalize(lowercased, Normalizer.Form.NFD)
            .replace("\\p{Mn}".toRegex(), "")
        
        // Keep only letters and apostrophes (for contractions like "don't")
        return withoutAccents.replace("[^\\p{L}']".toRegex(), "")
    }
}

