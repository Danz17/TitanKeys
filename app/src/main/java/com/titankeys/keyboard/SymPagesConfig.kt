package com.titankeys.keyboard

/**
 * Configuration for SYM pages order and visibility.
 */
data class SymPagesConfig(
    val emojiEnabled: Boolean = true,
    val symbolsEnabled: Boolean = true,
    val clipboardEnabled: Boolean = false,
    val emojiFirst: Boolean = true
)
