package com.titankeys.keyboard.clipboard

/**
 * Represents a single clipboard history entry.
 * Can contain either text or an image (screenshot).
 *
 * @param id Unique identifier for the entry
 * @param timeStamp When the entry was created/last updated (System.currentTimeMillis())
 * @param isPinned Whether the entry is pinned (won't be auto-deleted)
 * @param text The clipboard text content (null for image entries)
 * @param imagePath Path to the saved image file (null for text entries)
 */
data class ClipboardHistoryEntry(
    val id: Long,
    var timeStamp: Long,
    var isPinned: Boolean,
    val text: String?,
    val imagePath: String? = null
) : Comparable<ClipboardHistoryEntry> {

    /**
     * Whether this entry contains an image.
     */
    val isImage: Boolean get() = imagePath != null

    /**
     * Whether this entry contains text.
     */
    val isText: Boolean get() = text != null && imagePath == null

    /**
     * Display text for the entry (text content or placeholder for images).
     */
    val displayText: String get() = text ?: "[Image]"

    /**
     * Default comparator for sorting clipboard entries:
     * - Pinned items come first
     * - Within same pinned state, sort by timestamp (most recent first)
     * Note: ClipboardDao.sort() uses a custom comparator that respects user settings.
     */
    override fun compareTo(other: ClipboardHistoryEntry): Int {
        val result = other.isPinned.compareTo(isPinned)
        if (result == 0) return other.timeStamp.compareTo(timeStamp)
        return result
    }
}
