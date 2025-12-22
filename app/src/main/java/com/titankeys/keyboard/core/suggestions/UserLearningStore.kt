package com.titankeys.keyboard.core.suggestions

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.titankeys.keyboard.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Stores and manages user learning data (word sequences the user types).
 * Learns bigrams and trigrams from user input to improve predictions.
 */
class UserLearningStore(
    private val locale: Locale = Locale.getDefault(),
    private val debugLogging: Boolean = false
) {
    private val tag = "UserLearningStore"
    
    // In-memory storage for user n-grams
    private val userBigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private val userTrigrams = mutableMapOf<String, MutableMap<String, MutableMap<String, Int>>>()
    
    // Cache for normalized words
    private val normalizeCache = mutableMapOf<String, String>()
    
    companion object {
        private const val KEY_USER_BIGRAMS = "user_learning_bigrams"
        private const val KEY_USER_TRIGRAMS = "user_learning_trigrams"
        private const val MAX_STORED_SEQUENCES = 10000  // Limit to prevent excessive storage
    }
    
    /**
     * Records a sequence of words typed by the user.
     * Extracts bigrams and trigrams and updates frequencies.
     * 
     * @param words List of words in sequence (should be normalized)
     */
    fun recordSequence(words: List<String>) {
        if (words.isEmpty()) return
        
        val normalizedWords = words.map { normalize(it) }.filter { it.isNotBlank() }
        if (normalizedWords.size < 2) return
        
        // Extract bigrams
        for (i in 0 until normalizedWords.size - 1) {
            val word1 = normalizedWords[i]
            val word2 = normalizedWords[i + 1]
            incrementBigram(word1, word2)
        }
        
        // Extract trigrams
        for (i in 0 until normalizedWords.size - 2) {
            val word1 = normalizedWords[i]
            val word2 = normalizedWords[i + 1]
            val word3 = normalizedWords[i + 2]
            incrementTrigram(word1, word2, word3)
        }
        
        if (debugLogging && normalizedWords.size >= 2) {
            Log.d(tag, "Recorded sequence: ${normalizedWords.joinToString(" ")}")
        }
    }
    
    /**
     * Increments the frequency of a bigram.
     */
    private fun incrementBigram(word1: String, word2: String) {
        val bigramMap = userBigrams.getOrPut(word1) { mutableMapOf() }
        bigramMap[word2] = bigramMap.getOrDefault(word2, 0) + 1
    }
    
    /**
     * Increments the frequency of a trigram.
     */
    private fun incrementTrigram(word1: String, word2: String, word3: String) {
        val trigramMap1 = userTrigrams.getOrPut(word1) { mutableMapOf() }
        val trigramMap2 = trigramMap1.getOrPut(word2) { mutableMapOf() }
        trigramMap2[word3] = trigramMap2.getOrDefault(word3, 0) + 1
    }
    
    /**
     * Gets user bigrams as a map: word1 -> (word2 -> frequency).
     */
    fun getUserBigrams(): Map<String, Map<String, Int>> {
        return userBigrams.mapValues { it.value.toMap() }
    }
    
    /**
     * Gets user trigrams as a map: word1 -> word2 -> (word3 -> frequency).
     */
    fun getUserTrigrams(): Map<String, Map<String, Map<String, Int>>> {
        return userTrigrams.mapValues { word1Map ->
            word1Map.value.mapValues { word2Map ->
                word2Map.value.toMap()
            }
        }
    }
    
    /**
     * Gets the frequency of a specific bigram from user data.
     */
    fun getBigramFrequency(word1: String, word2: String): Int {
        val word1Norm = normalize(word1)
        val word2Norm = normalize(word2)
        return userBigrams[word1Norm]?.get(word2Norm) ?: 0
    }
    
    /**
     * Gets the frequency of a specific trigram from user data.
     */
    fun getTrigramFrequency(word1: String, word2: String, word3: String): Int {
        val word1Norm = normalize(word1)
        val word2Norm = normalize(word2)
        val word3Norm = normalize(word3)
        return userTrigrams[word1Norm]?.get(word2Norm)?.get(word3Norm) ?: 0
    }
    
    /**
     * Persists user learning data to SharedPreferences.
     */
    fun persist(context: Context) {
        try {
            val prefs = SettingsManager.getPreferences(context)
            val editor = prefs.edit()
            
            // Serialize bigrams
            val bigramsJson = JSONObject()
            userBigrams.forEach { (word1, word2Map) ->
                val word2Json = JSONObject()
                word2Map.forEach { (word2, freq) ->
                    word2Json.put(word2, freq)
                }
                bigramsJson.put(word1, word2Json)
            }
            editor.putString(KEY_USER_BIGRAMS, bigramsJson.toString())
            
            // Serialize trigrams
            val trigramsJson = JSONObject()
            userTrigrams.forEach { (word1, word2Map) ->
                val word2Json = JSONObject()
                word2Map.forEach { (word2, word3Map) ->
                    val word3Json = JSONObject()
                    word3Map.forEach { (word3, freq) ->
                        word3Json.put(word3, freq)
                    }
                    word2Json.put(word2, word3Json)
                }
                trigramsJson.put(word1, word2Json)
            }
            editor.putString(KEY_USER_TRIGRAMS, trigramsJson.toString())
            
            editor.apply()
            
            if (debugLogging) {
                Log.d(tag, "Persisted user learning: bigrams=${userBigrams.size}, trigrams=${userTrigrams.size}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to persist user learning data", e)
        }
    }
    
    /**
     * Loads user learning data from SharedPreferences.
     */
    fun load(context: Context) {
        try {
            val prefs = SettingsManager.getPreferences(context)
            
            // Load bigrams
            val bigramsJsonStr = prefs.getString(KEY_USER_BIGRAMS, null)
            if (bigramsJsonStr != null) {
                val bigramsJson = JSONObject(bigramsJsonStr)
                userBigrams.clear()
                
                bigramsJson.keys().forEach { word1 ->
                    val word2Json = bigramsJson.getJSONObject(word1)
                    val word2Map = mutableMapOf<String, Int>()
                    word2Json.keys().forEach { word2 ->
                        word2Map[word2] = word2Json.getInt(word2)
                    }
                    userBigrams[word1] = word2Map
                }
            }
            
            // Load trigrams
            val trigramsJsonStr = prefs.getString(KEY_USER_TRIGRAMS, null)
            if (trigramsJsonStr != null) {
                val trigramsJson = JSONObject(trigramsJsonStr)
                userTrigrams.clear()
                
                trigramsJson.keys().forEach { word1 ->
                    val word2Json = trigramsJson.getJSONObject(word1)
                    val word2Map = mutableMapOf<String, MutableMap<String, Int>>()
                    word2Json.keys().forEach { word2 ->
                        val word3Json = word2Json.getJSONObject(word2)
                        val word3Map = mutableMapOf<String, Int>()
                        word3Json.keys().forEach { word3 ->
                            word3Map[word3] = word3Json.getInt(word3)
                        }
                        word2Map[word2] = word3Map
                    }
                    userTrigrams[word1] = word2Map
                }
            }
            
            if (debugLogging) {
                Log.d(tag, "Loaded user learning: bigrams=${userBigrams.size}, trigrams=${userTrigrams.size}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load user learning data", e)
        }
    }
    
    /**
     * Clears all user learning data.
     */
    fun clear(context: Context) {
        userBigrams.clear()
        userTrigrams.clear()
        normalizeCache.clear()
        
        val prefs = SettingsManager.getPreferences(context)
        prefs.edit()
            .remove(KEY_USER_BIGRAMS)
            .remove(KEY_USER_TRIGRAMS)
            .apply()
        
        if (debugLogging) {
            Log.d(tag, "Cleared user learning data")
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

