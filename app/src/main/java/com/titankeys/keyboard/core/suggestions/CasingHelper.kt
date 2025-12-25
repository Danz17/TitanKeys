package com.titankeys.keyboard.core.suggestions

import java.util.Locale

/**
 * Helper to apply correct capitalization to suggestions
 * based on the pattern of the word typed by the user.
 */
object CasingHelper {

    private fun capitalizeFirstLetter(candidate: String): String {
        val idx = candidate.indexOfFirst { it.isLetter() }
        if (idx < 0) return candidate
        val first = candidate[idx]
        val cap = if (first.isLowerCase()) first.titlecase(Locale.getDefault()) else first.toString()
        return candidate.substring(0, idx) + cap + candidate.substring(idx + 1)
    }

    /**
     * Applies suggestion capitalization based on the pattern of the original word.
     *
     * @param candidate The suggested word (e.g. "Parenzo")
     * @param original The word typed by the user (e.g. "parenz", "Parenz", "PARENZ")
     * @param forceLeadingCapital If true, forces first letter uppercase (for auto-capitalize)
     * @return The word with correct capitalization
     */
    fun applyCasing(
        candidate: String,
        original: String,
        forceLeadingCapital: Boolean = false
    ): String {
        if (candidate.isEmpty()) return candidate
        
        // Se il campo richiede capitalizzazione forzata, applica titlecase
        if (forceLeadingCapital) {
            return capitalizeFirstLetter(candidate)
        }
        
        if (original.isEmpty()) return candidate
        
        // Determina il pattern di capitalizzazione considerando solo le lettere (ignora apostrofi/punteggiatura)
        val letters = original.filter { it.isLetter() }
        if (letters.isEmpty()) return candidate

        val allUpper = letters.length > 1 && letters.all { it.isUpperCase() }
        val allLower = letters.all { it.isLowerCase() }
        val firstLetter = letters.first()
        val restLetters = letters.drop(1)
        val firstUpper = firstLetter.isUpperCase()
        val restLower = restLetters.all { it.isLowerCase() }

        // If candidate contains uppercase and we're not in "allUpper" case (>=2 uppercase letters),
        // respect the dictionary casing as-is.
        val candidateHasUpper = candidate.any { it.isUpperCase() }
        val candidateLettersUpperCount = candidate.count { it.isUpperCase() }
        if (!forceLeadingCapital && candidateHasUpper && candidateLettersUpperCount < 2) {
            return candidate
        }
        // If original is all lowercase but candidate has uppercase (e.g. "mccartney" -> "McCartney"),
        // preserve the candidate casing.
        if (allLower && candidateHasUpper) {
            return candidate
        }
        
        return when {
            // Case: PARENZ -> PARENZO (all uppercase)
            allUpper -> candidate.uppercase(Locale.getDefault())
            // Case: Parenz -> Parenzo (first uppercase, rest lowercase)
            firstUpper && restLower -> capitalizeFirstLetter(candidate)
            // Case: parenz -> parenzo (all lowercase)
            allLower -> candidate.lowercase(Locale.getDefault())
            // Other cases: use suggestion as-is
            else -> candidate
        }
    }
}

