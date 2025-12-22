package com.titankeys.keyboard.core.suggestions

import kotlinx.serialization.Serializable

/**
 * Serialized dictionary index structure.
 * Contains pre-built normalized index and prefix cache for fast loading.
 * Enhanced with n-gram data for next-word prediction.
 */
@Serializable
data class DictionaryIndex(
    val normalizedIndex: Map<String, List<SerializableDictionaryEntry>>,
    val prefixCache: Map<String, List<SerializableDictionaryEntry>>,
    val symDeletes: Map<String, List<String>>? = null,
    val symMeta: SymSpellMeta? = null,
    // N-gram data for next-word prediction
    val bigrams: Map<String, Map<String, Int>>? = null,  // word1 -> word2 -> frequency
    val trigrams: Map<String, Map<String, Map<String, Int>>>? = null,  // word1 -> word2 -> word3 -> frequency
    val domainWords: Map<String, List<String>>? = null,  // domain -> word list
    val commonPhrases: List<PhraseEntry>? = null  // common multi-word phrases
)

/**
 * Represents a common phrase with frequency.
 */
@Serializable
data class PhraseEntry(
    val phrase: String,  // e.g., "how are you"
    val frequency: Int,
    val words: List<String>  // split phrase for easier lookup
)

/**
 * Serializable version of DictionaryEntry.
 * Uses Int for source instead of enum for serialization compatibility.
 */
@Serializable
data class SerializableDictionaryEntry(
    val word: String,
    val frequency: Int,
    val source: Int // 0 = MAIN, 1 = USER
)

@Serializable
data class SymSpellMeta(
    val maxEditDistance: Int,
    val prefixLength: Int
)

/**
 * Converts DictionaryEntry to SerializableDictionaryEntry.
 */
fun DictionaryEntry.toSerializable(): SerializableDictionaryEntry {
    return SerializableDictionaryEntry(
        word = this.word,
        frequency = this.frequency,
        source = this.source.ordinal
    )
}

/**
 * Converts SerializableDictionaryEntry to DictionaryEntry.
 */
fun SerializableDictionaryEntry.toDictionaryEntry(): DictionaryEntry {
    return DictionaryEntry(
        word = this.word,
        frequency = this.frequency,
        source = SuggestionSource.values()[this.source]
    )
}

