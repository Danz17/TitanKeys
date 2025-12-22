package com.titankeys.keyboard.inputmethod.suggestions.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.StateListDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.titankeys.keyboard.R
import com.titankeys.keyboard.SettingsActivity
import com.titankeys.keyboard.SettingsManager
import com.titankeys.keyboard.inputmethod.NotificationHelper
import com.titankeys.keyboard.inputmethod.TextSelectionHelper
import com.titankeys.keyboard.inputmethod.suggestions.SuggestionButtonHandler
import com.titankeys.keyboard.inputmethod.VariationButtonHandler
import com.titankeys.keyboard.inputmethod.SubtypeCycler
import com.titankeys.keyboard.core.suggestions.SuggestionMode
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import com.titankeys.keyboard.core.suggestions.DictionaryRepository
import kotlin.math.abs

/**
 * Renders the full-width suggestion bar with up to 3 items. Always occupies
 * a row (with placeholders) so the UI stays stable. Hidden when minimal UI
 * is forced or smart features are disabled by the caller.
 * Includes a language button on the right that cycles through IME subtypes.
 */
class FullSuggestionsBar(private val context: Context) {

    companion object {
        private val PRESSED_BLUE = Color.rgb(100, 150, 255) // Align with variation bar press state
        private val DEFAULT_SUGGESTION_COLOR = Color.rgb(17, 17, 17)
        private val NEXT_WORD_PREDICTION_COLOR = Color.rgb(25, 40, 65) // Subtle dark blue for predictions
        private const val FLASH_DURATION_MS = 160L
    }

    // Callbacks for side buttons
    var onSpeechRecognitionRequested: (() -> Unit)? = null
    var onClipboardRequested: (() -> Unit)? = null
    var onQuickPasteRequested: ((android.view.inputmethod.InputConnection?) -> Unit)? = null

    private var container: LinearLayout? = null
    private var frameContainer: FrameLayout? = null
    private var voiceButton: ImageView? = null
    private var clipboardButton: ImageView? = null
    private var clipboardBadge: TextView? = null
    private var lastSlots: List<String?> = emptyList()
    private var lastMode: SuggestionMode = SuggestionMode.CURRENT_WORD
    private var assets: AssetManager? = null
    private var imeServiceClass: Class<*>? = null
    private val suggestionButtons: MutableList<TextView> = mutableListOf()
    private var isTyping: Boolean = false
    private var fadeAnimator: android.view.ViewPropertyAnimator? = null
    private var currentInputConnection: android.view.inputmethod.InputConnection? = null
    private var lastClipboardCount: Int = 0

    // Cursor scroll state
    private var swipeHintView: TextView? = null
    private var isSwipeInProgress = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastCursorMoveX = 0f
    private val cursorMoveThreshold = 15f // pixels per cursor move
    private val swipeStartThresholdX = 10f // minimum horizontal movement to start swipe
    private val swipeStartThresholdY = 30f // max vertical movement before cancelling swipe
    private val cursorMoveHandler = Handler(Looper.getMainLooper())

    private val targetHeightPx: Int by lazy {
        // Compact row sized around three suggestion pills
        dpToPx(36f)
    }
    private val buttonSize: Int by lazy {
        dpToPx(32f)
    }

    /**
     * Sets the assets and IME service class needed for subtype cycling.
     */
    fun setSubtypeCyclingParams(assets: AssetManager, imeServiceClass: Class<*>) {
        this.assets = assets
        this.imeServiceClass = imeServiceClass
    }

    /**
     * Controls the typing state for fade animation.
     * When typing starts, the suggestions fade in (over the swipe hint).
     * When typing stops, suggestions fade out after a configurable delay to reveal the swipe hint underneath.
     * The frame stays visible so buttons and swipe hint remain accessible.
     */
    fun setTypingState(typing: Boolean) {
        if (isTyping == typing) return
        isTyping = typing

        val suggestionsView = container ?: return

        // Cancel any ongoing animation
        fadeAnimator?.cancel()
        fadeAnimator = null

        if (typing) {
            // Fade in suggestions when typing
            suggestionsView.visibility = View.VISIBLE
            fadeAnimator = suggestionsView.animate()
                .alpha(1f)
                .setDuration(150)
                .setStartDelay(0)
                .setListener(null)
            fadeAnimator?.start()
        } else {
            // Get configurable fade delay (default 3 seconds)
            val fadeDelayMs = SettingsManager.getSuggestionFadeDelayMs(context)

            // Fade out suggestions after delay when stopped typing
            fadeAnimator = suggestionsView.animate()
                .alpha(0f)
                .setStartDelay(fadeDelayMs) // Configurable delay before fading out
                .setDuration(200)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isTyping) {
                            suggestionsView.visibility = View.INVISIBLE
                        }
                    }
                })
            fadeAnimator?.start()
        }
    }

    /**
     * Gets the current typing state.
     */
    fun isCurrentlyTyping(): Boolean = isTyping

    fun ensureView(): FrameLayout {
        if (frameContainer == null) {
            // Create frame container for the whole bar
            frameContainer = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    targetHeightPx
                )
                visibility = View.GONE
                minimumHeight = targetHeightPx
            }

            // Swipe hint (behind suggestions, visible when not typing)
            swipeHintView = TextView(context).apply {
                text = "← swipe to scroll →"
                setTextColor(Color.argb(120, 255, 255, 255))
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    targetHeightPx,
                    Gravity.CENTER
                )
                visibility = View.VISIBLE
            }
            frameContainer?.addView(swipeHintView)

            // Main horizontal layout: [Voice] [Suggestions] [Clipboard]
            val mainLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    targetHeightPx
                )
                setPadding(dpToPx(4f), 0, dpToPx(4f), 0)
            }

            // Voice button (left)
            voiceButton = createVoiceButton()
            mainLayout.addView(voiceButton, LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                marginEnd = dpToPx(4f)
            })

            // Suggestions container (center, takes remaining space)
            container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
                minimumHeight = targetHeightPx
            }
            mainLayout.addView(container)

            // Clipboard button with badge (right)
            val clipboardFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                    marginStart = dpToPx(4f)
                }
            }
            clipboardButton = createClipboardButton()
            clipboardFrame.addView(clipboardButton, FrameLayout.LayoutParams(buttonSize, buttonSize))
            clipboardBadge = createClipboardBadge()
            clipboardFrame.addView(clipboardBadge, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.TOP
            ).apply {
                setMargins(0, dpToPx(2f), dpToPx(2f), 0)
            })
            mainLayout.addView(clipboardFrame)

            frameContainer?.addView(mainLayout)

            // Add touch listener for cursor scroll (on the whole frame)
            frameContainer?.setOnTouchListener { _, event ->
                handleCursorScrollTouch(event)
            }

            // Ensure the outer layout keeps the target height
            frameContainer?.layoutParams = (frameContainer?.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeightPx)
        }
        return frameContainer!!
    }

    /**
     * Handles touch events for cursor scrolling when not typing.
     * Swipe left/right moves the cursor.
     */
    private fun handleCursorScrollTouch(event: MotionEvent): Boolean {
        // Only handle cursor scroll when not actively showing suggestions
        if (isTyping && container?.alpha == 1f) {
            return false // Let suggestions handle the touch
        }

        val ic = currentInputConnection ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                lastCursorMoveX = event.x
                isSwipeInProgress = false
                return false // Don't consume yet, might be a button click
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - touchStartX
                val deltaY = event.y - touchStartY

                // Check if this is a horizontal swipe
                if (!isSwipeInProgress) {
                    if (abs(deltaX) > swipeStartThresholdX && abs(deltaY) < swipeStartThresholdY) {
                        isSwipeInProgress = true
                        lastCursorMoveX = event.x
                        // Update hint text while swiping
                        swipeHintView?.text = "scrolling..."
                        swipeHintView?.setTextColor(Color.argb(200, 255, 255, 255))
                    }
                }

                if (isSwipeInProgress) {
                    val moveDelta = event.x - lastCursorMoveX
                    if (abs(moveDelta) >= cursorMoveThreshold) {
                        val moves = (moveDelta / cursorMoveThreshold).toInt()
                        if (moves != 0) {
                            // Move cursor
                            if (moves > 0) {
                                repeat(moves) {
                                    TextSelectionHelper.moveCursorRight(ic)
                                }
                            } else {
                                repeat(-moves) {
                                    TextSelectionHelper.moveCursorLeft(ic)
                                }
                            }
                            lastCursorMoveX = event.x
                            // Haptic feedback
                            NotificationHelper.triggerHapticFeedback(context)
                        }
                    }
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwipeInProgress) {
                    isSwipeInProgress = false
                    // Reset hint text
                    swipeHintView?.text = "← swipe to scroll →"
                    swipeHintView?.setTextColor(Color.argb(120, 255, 255, 255))
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun createVoiceButton(): ImageView {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = dpToPx(4f).toFloat()
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = dpToPx(4f).toFloat()
        }
        val stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_mic_24)
            setColorFilter(Color.WHITE)
            background = stateList
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                NotificationHelper.triggerHapticFeedback(context)
                onSpeechRecognitionRequested?.invoke()
            }
        }
    }

    private fun createClipboardButton(): ImageView {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = dpToPx(4f).toFloat()
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = dpToPx(4f).toFloat()
        }
        val stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_content_paste_24)
            setColorFilter(Color.WHITE)
            background = stateList
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                NotificationHelper.triggerHapticFeedback(context)
                onQuickPasteRequested?.invoke(currentInputConnection)
            }
            setOnLongClickListener {
                NotificationHelper.triggerHapticFeedback(context)
                isPressed = false
                onClipboardRequested?.invoke()
                true
            }
        }
    }

    private fun createClipboardBadge(): TextView {
        return TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 9f
            gravity = Gravity.CENTER
            setPadding(dpToPx(3f), dpToPx(1f), dpToPx(3f), dpToPx(1f))
            background = GradientDrawable().apply {
                setColor(Color.RED)
                cornerRadius = dpToPx(6f).toFloat()
            }
            visibility = View.GONE
        }
    }

    fun updateClipboardCount(count: Int) {
        lastClipboardCount = count
        clipboardBadge?.let { badge ->
            if (count > 0) {
                badge.text = if (count > 99) "99+" else count.toString()
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }
        }
    }

    fun updateInputConnection(ic: android.view.inputmethod.InputConnection?) {
        currentInputConnection = ic
    }
    
    private fun getCurrentLanguageCode(): String {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentSubtype = imm?.currentInputMethodSubtype
            val locale = currentSubtype?.locale ?: "en_US"
            // Extract country code from locale (e.g., "it_IT" -> "IT", "en_US" -> "US")
            val parts = locale.split("_")
            if (parts.size >= 2) {
                parts[1].uppercase()
            } else {
                // Fallback: use first two letters of language code
                parts[0].uppercase().take(2)
            }
        } catch (e: Exception) {
            "EN"
        }
    }
    
    private fun cycleToNextSubtype() {
        val assets = this.assets
        val imeServiceClass = this.imeServiceClass
        if (assets != null && imeServiceClass != null) {
            SubtypeCycler.cycleToNextSubtype(context, imeServiceClass, assets, showToast = true)
        }
    }

    /**
     * Checks if a dictionary file exists for the current IME subtype.
     * Returns true if a dictionary is found (serialized format).
     */
    private fun hasDictionaryForCurrentSubtype(): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentSubtype = imm?.currentInputMethodSubtype
            val locale = currentSubtype?.locale ?: return false
            
            // Extract language code from locale (e.g., "it_IT" -> "it", "en_US" -> "en")
            val langCode = locale.split("_")[0]
            DictionaryRepository.hasDictionaryForLocale(context, langCode)
        } catch (e: Exception) {
            false
        }
    }

    fun update(
        suggestions: List<String>,
        shouldShow: Boolean,
        inputConnection: android.view.inputmethod.InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener?,
        shouldDisableSuggestions: Boolean,
        addWordCandidate: String?,
        onAddUserWord: ((String) -> Unit)?,
        suggestionMode: SuggestionMode = SuggestionMode.CURRENT_WORD
    ) {
        val bar = container ?: return
        val frame = frameContainer ?: return
        
        // Hide bar if shouldShow is false or if no dictionary exists for current subtype
        val hasDictionary = hasDictionaryForCurrentSubtype()
        if (!shouldShow || !hasDictionary) {
            suggestionButtons.clear()
            frame.visibility = View.GONE
            bar.visibility = View.GONE
            bar.removeAllViews()
            lastSlots = emptyList()
            return
        }

        frame.visibility = View.VISIBLE

        val slots = buildSlots(suggestions)
        if (slots == lastSlots && lastMode == suggestionMode && bar.childCount > 0) {
            bar.visibility = View.VISIBLE
            return
        }

        renderSlots(bar, slots, inputConnection, listener, shouldDisableSuggestions, addWordCandidate, onAddUserWord, suggestionMode)
        lastSlots = slots
        lastMode = suggestionMode
    }

    private fun openSettings() {
        try {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignore failures to avoid crashing the suggestions bar
        }
    }

    private fun renderSlots(
        bar: LinearLayout,
        slots: List<String?>,
        inputConnection: android.view.inputmethod.InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener?,
        shouldDisableSuggestions: Boolean,
        addWordCandidate: String?,
        onAddUserWord: ((String) -> Unit)?,
        suggestionMode: SuggestionMode
    ) {
        bar.removeAllViews()
        suggestionButtons.clear()
        bar.visibility = View.VISIBLE

        // Force bar and frame to the target height to avoid fallback to wrap_content.
        // Note: bar (container) is now inside a LinearLayout, so use LinearLayout.LayoutParams
        (bar.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            bar.layoutParams = lp
        }
        (frameContainer?.layoutParams as? ViewGroup.LayoutParams)?.let { lp ->
            lp.height = targetHeightPx
            frameContainer?.layoutParams = lp
        }
        bar.minimumHeight = targetHeightPx
        frameContainer?.minimumHeight = targetHeightPx

        val padV = dpToPx(3f) // tighter vertical padding to further reduce height
        val padH = dpToPx(12f)
        val weightLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginEnd = dpToPx(3f)
        }

        val isNextWordMode = suggestionMode == SuggestionMode.NEXT_WORD
        val slotOrder = listOf(slots[0], slots[1], slots[2]) // left, center, right
        for (suggestion in slotOrder) {
            val slotIndex = suggestionButtons.size
            val button = TextView(context).apply {
                text = (suggestion ?: "")
                gravity = Gravity.CENTER
                textSize = 14f // keep readable while shrinking the bar
                includeFontPadding = false
                minHeight = 0
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.NORMAL)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(padH, padV, padH, padV)
                background = buildSuggestionBackground(isNextWordMode)
                layoutParams = weightLayoutParams
                isClickable = suggestion != null
                isFocusable = suggestion != null
                if (suggestion != null) {
                    if (addWordCandidate != null && suggestion.equals(addWordCandidate, ignoreCase = true)) {
                        val addDrawable = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)?.mutate()
                        addDrawable?.setTint(Color.YELLOW)
                        addDrawable?.setBounds(0, 0, dpToPx(18f), dpToPx(18f))
                        setCompoundDrawables(null, null, addDrawable, null)
                        compoundDrawablePadding = dpToPx(6f)
                        setOnClickListener {
                            flashSlot(slotIndex)
                            onAddUserWord?.invoke(suggestion)
                        }
                    } else if (isNextWordMode) {
                        // Next-word prediction: insert word + space directly
                        setOnClickListener {
                            flashSlot(slotIndex)
                            inputConnection?.commitText("$suggestion ", 1)
                        }
                    } else {
                        val clickListener = SuggestionButtonHandler.createSuggestionClickListener(
                            suggestion,
                            inputConnection,
                            listener,
                            shouldDisableSuggestions
                        )
                        setOnClickListener { view ->
                            flashSlot(slotIndex)
                            clickListener.onClick(view)
                        }
                    }
                }
            }
            bar.addView(button)
            suggestionButtons.add(button)
        }
    }

    private fun buildSlots(suggestions: List<String>): List<String?> {
        val s0 = suggestions.getOrNull(0)
        val s1 = suggestions.getOrNull(1)
        val s2 = suggestions.getOrNull(2)
        return listOf(
            // left
            if (suggestions.size >= 3) s2 else null,
            // center
            s0,
            // right
            if (suggestions.size >= 2) s1 else null
        )
    }

    /**
     * Briefly highlights the slot that corresponds to the given suggestion index.
     * suggestionIndex uses the original ordering (0=center, 1=right, 2=left).
     */
    fun flashSuggestionAtIndex(suggestionIndex: Int) {
        val slotIndex = when (suggestionIndex) {
            0 -> 1 // center
            1 -> 2 // right
            2 -> 0 // left
            else -> return
        }
        flashSlot(slotIndex)
    }

    private fun flashSlot(slotIndex: Int) {
        val button = suggestionButtons.getOrNull(slotIndex) ?: return
        button.isPressed = true
        button.refreshDrawableState()
        button.postDelayed({
            button.isPressed = false
            button.refreshDrawableState()
        }, FLASH_DURATION_MS)
    }

    private fun buildSuggestionBackground(isNextWordMode: Boolean = false): StateListDrawable {
        val normalColor = if (isNextWordMode) NEXT_WORD_PREDICTION_COLOR else DEFAULT_SUGGESTION_COLOR
        val normalDrawable = GradientDrawable().apply {
            setColor(normalColor)
            cornerRadius = 0f
            alpha = 255 // placeholders look identical; they stay non-clickable
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
