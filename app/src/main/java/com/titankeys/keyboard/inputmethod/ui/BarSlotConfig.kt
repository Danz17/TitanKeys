package com.titankeys.keyboard.inputmethod.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Available actions that can be assigned to bar slots.
 */
enum class BarSlotAction {
  NONE,           // Empty slot (shows only when overlay hidden)
  VOICE,          // Speech recognition
  CLIPBOARD,      // Quick paste (tap) / History (long-press)
  CLIPBOARD_HISTORY, // Open clipboard history directly
  EMOJI,          // Open emoji picker
  SETTINGS,       // Open keyboard settings
  LANGUAGE_SWITCH, // Switch keyboard language/layout
  APP_LAUNCH;     // Launch an app (requires packageName param)

  companion object {
    fun fromString(value: String): BarSlotAction {
      return try {
        valueOf(value.uppercase())
      } catch (e: IllegalArgumentException) {
        NONE
      }
    }
  }
}

/**
 * Configuration for a single bar slot.
 * Each slot can have separate tap and long-press actions.
 */
data class BarSlotConfig(
  val slotIndex: Int,
  val tapAction: BarSlotAction = BarSlotAction.NONE,
  val tapParam: String? = null,      // e.g., package name for APP_LAUNCH
  val longPressAction: BarSlotAction = BarSlotAction.NONE,
  val longPressParam: String? = null
) {
  /**
   * Converts this config to JSON for storage.
   */
  fun toJson(): JSONObject {
    return JSONObject().apply {
      put("slotIndex", slotIndex)
      put("tapAction", tapAction.name)
      if (tapParam != null) put("tapParam", tapParam)
      put("longPressAction", longPressAction.name)
      if (longPressParam != null) put("longPressParam", longPressParam)
    }
  }

  companion object {
    /**
     * Creates a BarSlotConfig from JSON.
     */
    fun fromJson(json: JSONObject): BarSlotConfig {
      return BarSlotConfig(
        slotIndex = json.optInt("slotIndex", 0),
        tapAction = BarSlotAction.fromString(json.optString("tapAction", "NONE")),
        tapParam = json.optString("tapParam").takeIf { it.isNotEmpty() },
        longPressAction = BarSlotAction.fromString(json.optString("longPressAction", "NONE")),
        longPressParam = json.optString("longPressParam").takeIf { it.isNotEmpty() }
      )
    }
  }
}

/**
 * Complete bar layout configuration.
 * Contains the slot count and configuration for each slot.
 */
data class BarLayoutConfig(
  val slotCount: Int = DEFAULT_SLOT_COUNT,
  val slots: List<BarSlotConfig> = createDefaultSlots(slotCount)
) {
  /**
   * Converts this config to JSON for storage.
   */
  fun toJson(): JSONObject {
    return JSONObject().apply {
      put("slotCount", slotCount)
      put("slots", JSONArray().apply {
        slots.forEach { put(it.toJson()) }
      })
    }
  }

  /**
   * Gets the slot configuration at the given index.
   * Returns a default NONE config if index is out of bounds.
   */
  fun getSlot(index: Int): BarSlotConfig {
    return slots.getOrNull(index) ?: BarSlotConfig(slotIndex = index)
  }

  /**
   * Creates a new config with an updated slot.
   */
  fun withSlot(slot: BarSlotConfig): BarLayoutConfig {
    val newSlots = slots.toMutableList()
    val existingIndex = newSlots.indexOfFirst { it.slotIndex == slot.slotIndex }
    if (existingIndex >= 0) {
      newSlots[existingIndex] = slot
    } else if (slot.slotIndex < slotCount) {
      newSlots.add(slot)
      newSlots.sortBy { it.slotIndex }
    }
    return copy(slots = newSlots)
  }

  /**
   * Creates a new config with a different slot count.
   * Redistributes existing slots to fit the new count.
   */
  fun withSlotCount(newCount: Int): BarLayoutConfig {
    val clampedCount = newCount.coerceIn(MIN_SLOT_COUNT, MAX_SLOT_COUNT)
    if (clampedCount == slotCount) return this

    // Keep existing edge slots (0 and last), redistribute middle
    val newSlots = mutableListOf<BarSlotConfig>()

    // Keep slot 0 (left edge)
    slots.firstOrNull { it.slotIndex == 0 }?.let {
      newSlots.add(it.copy(slotIndex = 0))
    } ?: newSlots.add(BarSlotConfig(slotIndex = 0, tapAction = BarSlotAction.VOICE))

    // Add empty middle slots
    for (i in 1 until clampedCount - 1) {
      newSlots.add(BarSlotConfig(slotIndex = i))
    }

    // Keep last slot (right edge)
    slots.lastOrNull { it.slotIndex == slotCount - 1 }?.let {
      newSlots.add(it.copy(slotIndex = clampedCount - 1))
    } ?: newSlots.add(BarSlotConfig(
      slotIndex = clampedCount - 1,
      tapAction = BarSlotAction.CLIPBOARD,
      longPressAction = BarSlotAction.CLIPBOARD_HISTORY
    ))

    return BarLayoutConfig(slotCount = clampedCount, slots = newSlots)
  }

  companion object {
    const val MIN_SLOT_COUNT = 4
    const val MAX_SLOT_COUNT = 8
    const val DEFAULT_SLOT_COUNT = 6

    /**
     * Creates a BarLayoutConfig from JSON.
     */
    fun fromJson(json: JSONObject): BarLayoutConfig {
      val slotCount = json.optInt("slotCount", DEFAULT_SLOT_COUNT)
        .coerceIn(MIN_SLOT_COUNT, MAX_SLOT_COUNT)

      val slotsArray = json.optJSONArray("slots")
      val slots = if (slotsArray != null) {
        (0 until slotsArray.length()).map { i ->
          BarSlotConfig.fromJson(slotsArray.getJSONObject(i))
        }
      } else {
        createDefaultSlots(slotCount)
      }

      return BarLayoutConfig(slotCount = slotCount, slots = slots)
    }

    /**
     * Creates default slots for the given count.
     * Slot 0 = Voice, last slot = Clipboard, middle = empty.
     */
    fun createDefaultSlots(count: Int): List<BarSlotConfig> {
      val slots = mutableListOf<BarSlotConfig>()

      for (i in 0 until count) {
        val config = when (i) {
          0 -> BarSlotConfig(
            slotIndex = i,
            tapAction = BarSlotAction.VOICE
          )
          count - 1 -> BarSlotConfig(
            slotIndex = i,
            tapAction = BarSlotAction.CLIPBOARD,
            longPressAction = BarSlotAction.CLIPBOARD_HISTORY
          )
          else -> BarSlotConfig(slotIndex = i)
        }
        slots.add(config)
      }

      return slots
    }

    /**
     * Returns the default configuration.
     */
    fun default(): BarLayoutConfig {
      return BarLayoutConfig()
    }
  }
}
