package com.titankeys.keyboard.inputmethod.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.UnderlineSpan
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.titankeys.keyboard.R
import com.titankeys.keyboard.SettingsActivity
import com.titankeys.keyboard.SettingsManager
import com.titankeys.keyboard.inputmethod.StatusBarController
import com.titankeys.keyboard.inputmethod.TextSelectionHelper
import com.titankeys.keyboard.inputmethod.NotificationHelper
import com.titankeys.keyboard.inputmethod.VariationButtonHandler
import com.titankeys.keyboard.inputmethod.suggestions.SuggestionButtonHandler
import com.titankeys.keyboard.inputmethod.SpeechRecognitionActivity
import com.titankeys.keyboard.data.variation.VariationRepository
import android.graphics.Paint
import kotlin.math.abs
import com.titankeys.keyboard.inputmethod.ui.BarLayoutConfig
import com.titankeys.keyboard.inputmethod.ui.BarSlotAction
import com.titankeys.keyboard.inputmethod.ui.BarSlotConfig
import kotlin.math.max
import kotlin.math.min
import android.content.res.AssetManager
import com.titankeys.keyboard.inputmethod.SubtypeCycler

/**
 * Handles the variations row (suggestions + microphone/language) rendered above the LED strip.
 */
class VariationBarView(
    private val context: Context,
    private val assets: AssetManager? = null,
    private val imeServiceClass: Class<*>? = null
) {
    companion object {
        private const val TAG = "VariationBarView"
        private const val SWIPE_HINT_SHOW_DELAY_MS = 1000L
        private val PRESSED_BLUE = Color.rgb(100, 150, 255) // Same as LED active blue
        private val RECOGNITION_RED = Color.rgb(255, 80, 80) // Red color for active recognition
    }

    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
    var onCursorMovedListener: (() -> Unit)? = null
    var onSpeechRecognitionRequested: (() -> Unit)? = null
    var onAddUserWord: ((String) -> Unit)? = null
    var onLanguageSwitchRequested: (() -> Unit)? = null
    var onClipboardRequested: (() -> Unit)? = null
    var onClipboardLongPressed: (() -> Unit)? = null
    var onQuickPasteRequested: ((android.view.inputmethod.InputConnection?) -> Unit)? = null

    /**
     * Sets the microphone button active state (red pulsing background) during speech recognition.
     */
    fun setMicrophoneButtonActive(isActive: Boolean) {
        microphoneButtonView?.let { button ->
            isMicrophoneActive = isActive
            if (isActive) {
                // Initialize red background (will be updated by audio level)
                startMicrophoneAudioFeedback(button)
            } else {
                // Stop animation and restore normal state
                stopMicrophoneAudioFeedback(button)
            }
        }
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     * Changes the red color intensity based on audio volume.
     * @param rmsdB The RMS audio level in decibels (typically -10 to 0, lower is quieter)
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        microphoneButtonView?.let { button ->
            if (!isMicrophoneActive) return@let
            
            // Map RMS value (-10 to 0) to a normalized value (0.0 to 1.0)
            // Clamp the value to reasonable bounds
            val minRms = -10f
            val maxRms = 0f
            val normalizedLevel = ((rmsdB - minRms) / (maxRms - minRms)).coerceIn(0f, 1f)
            
            // Map to color intensity: darker red at 0.0, brighter red at 1.0
            // Use a curve to make the effect more visible (power of 2)
            val intensity = normalizedLevel * normalizedLevel // Quadratic curve for more noticeable effect
            
            // Calculate red color values: from dark red (128, 0, 0) to bright red (255, 50, 50)
            // Keep it red by maintaining lower G and B values relative to R
            val r = (128 + (255 - 128) * intensity).toInt()
            val g = (0 + (50 - 0) * intensity).toInt()
            val b = (0 + (50 - 0) * intensity).toInt()
            val color = Color.rgb(r, g, b)
            
            // Update the drawable color
            currentMicrophoneDrawable?.setColor(color)
            button.background?.invalidateSelf()
        }
    }
    
    /**
     * Initializes the microphone button for audio feedback (red background).
     */
    private fun startMicrophoneAudioFeedback(button: ImageView) {
        // Stop any existing animation
        stopMicrophoneAudioFeedback(button)
        
        // Create base drawable with initial red color (medium intensity)
        currentMicrophoneDrawable = GradientDrawable().apply {
            setColor(RECOGNITION_RED)
            cornerRadius = 0f
        }
        
        // Store original background for pressed state
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        
        // Create state list with red as normal state
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), currentMicrophoneDrawable)
        }
        button.background = stateList
        
        // Set initial alpha
        button.alpha = 1f
    }
    
    /**
     * Stops the audio feedback and restores normal button state.
     */
    private fun stopMicrophoneAudioFeedback(button: ImageView) {
        // Cancel any pulse animation if still running
        microphonePulseAnimator?.cancel()
        microphonePulseAnimator = null
        
        // Reset alpha
        button.alpha = 1f
        
        // Clear reference to drawable
        currentMicrophoneDrawable = null
        
        // Restore normal state
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        button.background = stateList
    }


    private var wrapper: FrameLayout? = null
    private var container: LinearLayout? = null
    private var buttonsContainer: LinearLayout? = null
    private var overlay: FrameLayout? = null
    private var swipeIndicator: View? = null
    private var emptyHintView: TextView? = null
    private var microphonePulseAnimator: ValueAnimator? = null
    private var shouldShowSwipeHint: Boolean = false
    private var currentVariationsRow: LinearLayout? = null
    private var variationButtons: MutableList<TextView> = mutableListOf()
    private var microphoneButtonView: ImageView? = null
    private var settingsButtonView: ImageView? = null
    private var languageButtonView: TextView? = null
    private var isMicrophoneActive: Boolean = false
    private var lastLanguageSwitchTime: Long = 0
    private val LANGUAGE_SWITCH_DEBOUNCE_MS = 500L // Minimum time between language switches
    private var currentMicrophoneDrawable: GradientDrawable? = null
    private var lastDisplayedVariations: List<String> = emptyList()
    private var isSymModeActive = false
    private var isActivelyTyping = false
    private var isShowingSpeechRecognitionHint: Boolean = false
    private var originalHintText: CharSequence? = null
    private var isSwipeInProgress = false
    private var swipeDirection: Int? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastCursorMoveX = 0f
    private var currentInputConnection: android.view.inputmethod.InputConnection? = null
    private var staticVariations: List<String> = emptyList()
    private var emailVariations: List<String> = emptyList()
    private var lastInputConnectionUsed: android.view.inputmethod.InputConnection? = null
    private var lastIsStaticContent: Boolean? = null
    private var pressedView: View? = null
    private var longPressHandler: Handler? = null
    private var longPressRunnable: Runnable? = null
    private var longPressExecuted: Boolean = false
    private var clipboardButtonView: ImageView? = null
    private var clipboardContainer: FrameLayout? = null
    private var clipboardBadgeView: TextView? = null
    private var clipboardFlashOverlay: View? = null
    private var clipboardFlashAnimator: ValueAnimator? = null
    private var lastClipboardCount: Int? = null
    private var variationsContainerHeight: Int = 0
    private var variationsVerticalPadding: Int = 0

    // Two-layer bar system
    private var barLayoutConfig: BarLayoutConfig = BarLayoutConfig.default()
    private var underlayContainer: LinearLayout? = null
    private var suggestionsOverlay: LinearLayout? = null
    private var underlaySlotViews: MutableList<View> = mutableListOf()

    // Action callbacks for slot actions
    var onEmojiRequested: (() -> Unit)? = null
    var onSettingsRequested: (() -> Unit)? = null
    var onAppLaunchRequested: ((String) -> Unit)? = null

    fun ensureView(): FrameLayout {
        if (wrapper != null) {
            return wrapper!!
        }

        val basePadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            64f,
            context.resources.displayMetrics
        ).toInt()
        val leftPadding = basePadding // 64dp to avoid Android system [˅] button
        val rightPadding = basePadding // 64dp to avoid Android system [⌨] button
        variationsVerticalPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f,
            context.resources.displayMetrics
        ).toInt()
        variationsContainerHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            55f,
            context.resources.displayMetrics
        ).toInt()

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL  // Only vertical centering, buttons pushed to corners by spacers
            setPadding(leftPadding, variationsVerticalPadding, rightPadding, variationsVerticalPadding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                variationsContainerHeight
            )
            visibility = View.GONE
        }

        // Full-width container: [Voice] ... [Suggestions] ... [Clipboard]
        buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container?.addView(buttonsContainer)

        wrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                variationsContainerHeight
            )
            visibility = View.GONE
            addView(container)
        }

        overlay = FrameLayout(context).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }.also { overlayView ->
            val indicator = createSwipeIndicator()
            swipeIndicator = indicator
            overlayView.addView(indicator)
            val hint = createSwipeHintView()
            emptyHintView = hint
            overlayView.addView(hint)
            wrapper?.addView(overlayView)
            installOverlayTouchListener(overlayView)
        }

        return wrapper!!
    }

    fun getWrapper(): FrameLayout? = wrapper

    fun setSymModeActive(active: Boolean) {
        isSymModeActive = active
        if (active) {
            hideSwipeIndicator(immediate = true)
            hideSwipeHintImmediate()
            overlay?.visibility = View.GONE
        }
    }

    /**
     * Sets the typing state. When not actively typing, the swipe hint will be shown.
     */
    fun setTypingState(typing: Boolean) {
        isActivelyTyping = typing
        if (!typing) {
            // Show swipe hint when not actively typing
            shouldShowSwipeHint = true
            updateSwipeHintVisibility(animate = true)
        } else {
            // Hide swipe hint when typing (suggestions will show instead)
            shouldShowSwipeHint = false
            updateSwipeHintVisibility(animate = true)
        }
    }

    /**
     * Updates only the clipboard badge count without rebuilding the row.
     */
    fun updateClipboardCount(count: Int) {
        updateClipboardBadge(count)
        if (count > 0 && count != lastClipboardCount) {
            flashClipboardButton()
        }
        lastClipboardCount = count
    }

    fun updateInputConnection(inputConnection: android.view.inputmethod.InputConnection?) {
        currentInputConnection = inputConnection
    }

    fun resetVariationsState() {
        lastDisplayedVariations = emptyList()
        lastInputConnectionUsed = null
        lastIsStaticContent = null
    }

    fun hideImmediate() {
        currentVariationsRow?.let { row ->
            (row.parent as? ViewGroup)?.removeView(row)
        }
        currentVariationsRow = null
        variationButtons.clear()
        removeMicrophoneImmediate()
        removeSettingsImmediate()
        removeLanguageButtonImmediate()
        removeClipboardButtonImmediate()
        hideSwipeIndicator(immediate = true)
        hideSwipeHintImmediate()
        shouldShowSwipeHint = false
        container?.visibility = View.GONE
        wrapper?.visibility = View.GONE
        overlay?.visibility = View.GONE
    }

    fun hideForSym(onHidden: () -> Unit) {
        val containerView = container ?: run {
            onHidden()
            return
        }
        val row = currentVariationsRow
        val overlayView = overlay

        removeMicrophoneImmediate()
        removeSettingsImmediate()
        removeLanguageButtonImmediate()
        removeClipboardButtonImmediate()
        hideSwipeIndicator(immediate = true)
        hideSwipeHintImmediate()
        shouldShowSwipeHint = false

        if (row != null && row.parent == containerView && row.visibility == View.VISIBLE) {
            animateVariationsOut(row) {
                (row.parent as? ViewGroup)?.removeView(row)
                if (currentVariationsRow == row) {
                    currentVariationsRow = null
                }
                containerView.visibility = View.GONE
                wrapper?.visibility = View.GONE
                overlayView?.visibility = View.GONE
                onHidden()
            }
        } else {
            currentVariationsRow = null
            containerView.visibility = View.GONE
            wrapper?.visibility = View.GONE
            overlayView?.visibility = View.GONE
            onHidden()
        }
    }

    fun showVariations(snapshot: StatusBarController.StatusSnapshot, inputConnection: android.view.inputmethod.InputConnection?) {
        val containerView = container ?: run {
            Log.w(TAG, "showVariations: container is null")
            return
        }
        val wrapperView = wrapper ?: run {
            Log.w(TAG, "showVariations: wrapper is null")
            return
        }
        val overlayView = overlay ?: run {
            Log.w(TAG, "showVariations: overlay is null")
            return
        }

        Log.d(TAG, "showVariations called: variations=${snapshot.variations.size}, suggestions=${snapshot.suggestions.size}")

        currentInputConnection = inputConnection

        // Decide whether to use suggestions, dynamic variations (from cursor) or static utility keys.
        val staticModeEnabled = SettingsManager.isStaticVariationBarModeEnabled(context)
        val canShowVariations = !snapshot.shouldDisableVariations
        val canShowSuggestions = !snapshot.shouldDisableSuggestions
        val hasDynamicVariations = canShowVariations && snapshot.variations.isNotEmpty()
        val hasSuggestions = canShowSuggestions && snapshot.suggestions.isNotEmpty()
        val useDynamicVariations = !staticModeEnabled && hasDynamicVariations
        val allowStaticFallback = staticModeEnabled || snapshot.shouldDisableVariations ||
            (!hasDynamicVariations && !hasSuggestions)

        val effectiveVariations: List<String>
        val isStaticContent: Boolean
        when {
            hasSuggestions -> {
                effectiveVariations = snapshot.suggestions
                isStaticContent = false
            }
            useDynamicVariations -> {
                effectiveVariations = snapshot.variations
                isStaticContent = false
            }
            allowStaticFallback -> {
                val variations = if (snapshot.isEmailField) {
                    if (emailVariations.isEmpty()) {
                        emailVariations = VariationRepository.loadEmailVariations(context.assets, context)
                    }
                    emailVariations
                } else {
                    if (staticVariations.isEmpty()) {
                        staticVariations = VariationRepository.loadStaticVariations(context.assets, context)
                    }
                    staticVariations
                }
                effectiveVariations = variations
                isStaticContent = true
            }
            else -> {
                effectiveVariations = emptyList()
                isStaticContent = false
            }
        }

        val limitedVariations = effectiveVariations.take(7)
        val showSwipeHint = effectiveVariations.isEmpty() && !allowStaticFallback
        shouldShowSwipeHint = showSwipeHint

        containerView.visibility = View.VISIBLE
        wrapperView.visibility = View.VISIBLE
        overlayView.visibility = if (isSymModeActive) View.GONE else View.VISIBLE
        updateSwipeHintVisibility(animate = true)

        val variationsChanged = limitedVariations != lastDisplayedVariations
        val inputConnectionChanged = lastInputConnectionUsed !== inputConnection
        val contentModeChanged = lastIsStaticContent != isStaticContent
        val hasExistingRow = currentVariationsRow != null &&
            currentVariationsRow?.parent == containerView &&
            currentVariationsRow?.visibility == View.VISIBLE

        if (!variationsChanged && !inputConnectionChanged && !contentModeChanged && hasExistingRow) {
            return
        }

        variationButtons.clear()
        currentVariationsRow?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        currentVariationsRow = null

        val screenWidth = context.resources.displayMetrics.widthPixels
        val leftPadding = containerView.paddingLeft
        val rightPadding = containerView.paddingRight
        val availableWidth = screenWidth - leftPadding - rightPadding
        val containerHeight = variationsContainerHeight - (variationsVerticalPadding * 2)

        lastDisplayedVariations = limitedVariations
        lastInputConnectionUsed = inputConnection
        lastIsStaticContent = isStaticContent

        val buttonsContainerView = buttonsContainer ?: return
        buttonsContainerView.removeAllViews()
        buttonsContainerView.visibility = View.VISIBLE

        // === THREE-PART LAYOUT: [Voice] [Middle/Suggestions] [Clipboard] ===
        // Voice and Clipboard are ALWAYS visible at the edges
        val edgeButtonWidth = dpToPx(48f)
        val middleWidth = availableWidth - (edgeButtonWidth * 2)

        // Main horizontal container
        val mainRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // --- LEFT EDGE: Voice button (always visible) ---
        val voiceSlotConfig = barLayoutConfig.getSlot(0)  // Slot 0 is Voice by default
        val voiceButton = createSlotButton(voiceSlotConfig, edgeButtonWidth, containerHeight, inputConnection)
        mainRow.addView(voiceButton)

        // --- MIDDLE: Suggestions or empty space ---
        val displayVariations = limitedVariations.take(3)
        val hasSuggestionsToShow = displayVariations.isNotEmpty() && !isStaticContent

        val middleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(240, 17, 17, 17))  // Match bar background
            layoutParams = LinearLayout.LayoutParams(middleWidth, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        if (hasSuggestionsToShow) {
            val spacingBetweenButtons = dpToPx(3f)
            val totalSpacing = spacingBetweenButtons * (displayVariations.size - 1).coerceAtLeast(0)
            val suggestionWidth = max(1, (middleWidth - totalSpacing) / displayVariations.size)

            for ((index, variation) in displayVariations.withIndex()) {
                val isAddCandidate = snapshot.addWordCandidate == variation
                val variationButton = createVariationButton(
                    variation,
                    inputConnection,
                    suggestionWidth,
                    suggestionWidth,
                    false,
                    isAddCandidate,
                    isSuggestion = true,  // These are word suggestions, not accent variations
                    shouldDisableAutoCapitalize = snapshot.shouldDisableAutoCapitalize
                )
                variationButtons.add(variationButton)
                val params = LinearLayout.LayoutParams(suggestionWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                if (index > 0) {
                    params.marginStart = spacingBetweenButtons
                }
                middleContainer.addView(variationButton, params)
            }
        }
        suggestionsOverlay = middleContainer
        mainRow.addView(middleContainer)

        // --- RIGHT EDGE: Clipboard button (always visible) ---
        val lastSlotIndex = barLayoutConfig.slotCount - 1
        val clipboardSlotConfig = barLayoutConfig.getSlot(lastSlotIndex)  // Last slot is Clipboard by default
        val clipboardButton = createSlotButton(clipboardSlotConfig, edgeButtonWidth, containerHeight, inputConnection)
        mainRow.addView(clipboardButton)

        // Store references for underlay
        underlayContainer = mainRow
        underlaySlotViews.clear()
        underlaySlotViews.add(voiceButton)
        underlaySlotViews.add(clipboardButton)

        buttonsContainerView.addView(mainRow)

        Log.d(TAG, "showVariations: 3-part layout, middle=$middleWidth, suggestions=${displayVariations.size}, hasSuggestions=$hasSuggestionsToShow")
    }

    private fun installOverlayTouchListener(overlayView: FrameLayout) {
        val swipeThreshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )

            overlayView.setOnTouchListener { _, motionEvent ->
                if (isSymModeActive) {
                    return@setOnTouchListener false
                }

            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Track the view under the finger so we can show pressed state despite the overlay intercepting the touch.
                    pressedView?.isPressed = false
                    pressedView = container?.let { findClickableViewAt(it, motionEvent.x, motionEvent.y) }
                    pressedView?.isPressed = true
                    isSwipeInProgress = false
                    swipeDirection = null
                    touchStartX = motionEvent.x
                    touchStartY = motionEvent.y
                    lastCursorMoveX = motionEvent.x
                    hideSwipeHintImmediate()
                    
                    // Setup long press detection
                    cancelLongPress()
                    longPressExecuted = false
                    if (pressedView != null && pressedView?.isLongClickable == true) {
                        longPressHandler = Handler(Looper.getMainLooper())
                        longPressRunnable = Runnable {
                            longPressExecuted = true
                            // Reset pressed state before long click (callback may hide the bar)
                            pressedView?.isPressed = false
                            pressedView?.performLongClick()
                            pressedView = null
                        }
                        longPressHandler?.postDelayed(longPressRunnable!!, 500) // 500ms for long press
                    }
                    
                    Log.d(TAG, "Touch down on overlay at ($touchStartX, $touchStartY)")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = motionEvent.x - touchStartX
                    val deltaY = abs(motionEvent.y - touchStartY)
                    val incrementalDeltaX = motionEvent.x - lastCursorMoveX
                    updateSwipeIndicatorPosition(overlayView, motionEvent.x)

                    // Cancel long press if user moves too much
                    if (abs(deltaX) > swipeThreshold || deltaY > swipeThreshold) {
                        cancelLongPress()
                    }

                    if (isSwipeInProgress || (abs(deltaX) > swipeThreshold && abs(deltaX) > deltaY)) {
                        if (!isSwipeInProgress) {
                            isSwipeInProgress = true
                            swipeDirection = if (deltaX > 0) 1 else -1
                            // Clear pressed state when a swipe starts to avoid stuck highlights.
                            pressedView?.isPressed = false
                            pressedView = null
                            revealSwipeIndicator(overlayView, motionEvent.x)
                            Log.d(TAG, "Swipe started: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                        } else {
                            val currentDirection = if (incrementalDeltaX > 0) 1 else -1
                            if (currentDirection != swipeDirection && abs(incrementalDeltaX) > swipeThreshold) {
                                swipeDirection = currentDirection
                                Log.d(TAG, "Swipe direction changed: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                            }
                        }

                        if (isSwipeInProgress && swipeDirection != null) {
                            val inputConnection = currentInputConnection
                            if (inputConnection != null) {
                                // Read the threshold value dynamically to support real-time changes
                                val incrementalThresholdDp = SettingsManager.getSwipeIncrementalThreshold(context)
                                val incrementalThreshold = TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    incrementalThresholdDp,
                                    context.resources.displayMetrics
                                )
                                val movementInDirection = if (swipeDirection == 1) incrementalDeltaX else -incrementalDeltaX
                                if (movementInDirection > incrementalThreshold) {
                                    val moved = if (swipeDirection == 1) {
                                        TextSelectionHelper.moveCursorRight(inputConnection)
                                    } else {
                                        TextSelectionHelper.moveCursorLeft(inputConnection)
                                    }

                                    if (moved) {
                                        lastCursorMoveX = motionEvent.x
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            onCursorMovedListener?.invoke()
                                        }, 50)
                                    }
                                }
                            }
                        }
                        true
                    } else {
                        // No swipe detected yet: update pressed highlight if we moved onto another button.
                        val currentTarget = container?.let { findClickableViewAt(it, motionEvent.x, motionEvent.y) }
                        if (pressedView != currentTarget) {
                            pressedView?.isPressed = false
                            pressedView = currentTarget
                            pressedView?.isPressed = true
                        }
                        true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val wasLongPress = longPressExecuted
                    cancelLongPress()
                    pressedView?.isPressed = false
                    val pressedTarget = pressedView
                    pressedView = null
                    hideSwipeIndicator()
                    updateSwipeHintVisibility(animate = true)
                    if (isSwipeInProgress) {
                        isSwipeInProgress = false
                        swipeDirection = null
                        Log.d(TAG, "Swipe ended on overlay")
                        true
                    } else {
                        // Don't execute click if long press was executed
                        if (!wasLongPress) {
                            val x = motionEvent.x
                            val y = motionEvent.y
                            val clickedView = container?.let { findClickableViewAt(it, x, y) }
                            if (clickedView != null && clickedView == pressedTarget) {
                                clickedView.performClick()
                            }
                        }
                        true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    pressedView?.isPressed = false
                    pressedView = null
                    hideSwipeIndicator()
                    updateSwipeHintVisibility(animate = true)
                    isSwipeInProgress = false
                    swipeDirection = null
                    true
                }
                else -> true
            }
        }
    }

    private fun revealSwipeIndicator(overlayView: FrameLayout, x: Float) {
        val indicator = swipeIndicator ?: return
        updateSwipeIndicatorPosition(overlayView, x)
        indicator.animate().cancel()
        indicator.alpha = 0f
        indicator.visibility = View.VISIBLE
        indicator.animate()
            .alpha(1f)
            .setDuration(60)
            .setListener(null)
            .start()
    }

    private fun hideSwipeIndicator(immediate: Boolean = false) {
        val indicator = swipeIndicator ?: return
        indicator.animate().cancel()
        if (immediate) {
            indicator.alpha = 0f
            indicator.visibility = View.GONE
            return
        }
        indicator.animate()
            .alpha(0f)
            .setDuration(140)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    indicator.visibility = View.GONE
                    indicator.alpha = 0f
                }
            })
            .start()
    }

    private fun updateSwipeHintVisibility(animate: Boolean) {
        val hint = emptyHintView ?: return
        val overlayView = overlay ?: return
        // Don't show swipe hint if we're showing speech recognition hint
        val shouldShow = shouldShowSwipeHint && overlayView.visibility == View.VISIBLE && !isShowingSpeechRecognitionHint
        hint.animate().cancel()
        if (shouldShow) {
            if (hint.visibility != View.VISIBLE) {
                hint.visibility = View.VISIBLE
                hint.alpha = 0f
            }
            if (animate) {
                hint.animate()
                    .alpha(0.7f)
                    .setDuration(420)
                    .setStartDelay(SWIPE_HINT_SHOW_DELAY_MS)
                    .setListener(null)
                    .start()
            } else {
                hint.alpha = 0.7f
            }
        } else {
            // Don't hide if we're showing speech recognition hint
            if (!isShowingSpeechRecognitionHint) {
                if (animate) {
                    hint.animate()
                        .setStartDelay(0)
                        .alpha(0f)
                        .setDuration(120)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                hint.visibility = View.GONE
                                hint.alpha = 0f
                            }
                        })
                        .start()
                } else {
                    hint.alpha = 0f
                    hint.visibility = View.GONE
                }
            }
        }
    }

    private fun hideSwipeHintImmediate() {
        val hint = emptyHintView ?: return
        hint.animate().cancel()
        hint.alpha = 0f
        hint.visibility = View.GONE
    }
    
    /**
     * Shows or hides the speech recognition hint message in the hint view.
     * When showing, replaces the swipe hint text with speech recognition message.
     * When hiding, restores the original swipe hint behavior.
     */
    fun showSpeechRecognitionHint(show: Boolean) {
        val hint = emptyHintView ?: return
        val overlayView = overlay ?: return
        
        isShowingSpeechRecognitionHint = show
        
        if (show) {
            // Ensure overlay is visible
            overlayView.visibility = View.VISIBLE
            
            // Save original hint text if not already saved
            if (originalHintText == null) {
                originalHintText = hint.text
            }
            
            // Set speech recognition message
            hint.text = context.getString(R.string.speech_recognition_prompt)
            
            // Show hint immediately (no delay) with animation
            hint.animate().cancel()
            hint.visibility = View.VISIBLE
            hint.alpha = 0f
            hint.animate()
                .alpha(0.7f)
                .setDuration(300)
                .setStartDelay(0)
                .start()
        } else {
            // Restore original hint text
            if (originalHintText != null) {
                hint.text = originalHintText
                originalHintText = null
            }
            
            // Hide hint with animation
            hint.animate().cancel()
            hint.animate()
                .alpha(0f)
                .setDuration(200)
                .setStartDelay(0)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        hint.visibility = View.GONE
                        // Restore swipe hint visibility logic
                        updateSwipeHintVisibility(animate = false)
                    }
                })
                .start()
        }
    }

    private fun createSwipeHintView(): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.swipe_to_move_cursor)
            setTextColor(Color.argb(120, 255, 255, 255))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            alpha = 0f
            background = null
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            visibility = View.GONE
        }
    }

    private fun updateSwipeIndicatorPosition(overlayView: FrameLayout, x: Float) {
        val indicator = swipeIndicator ?: return
        val indicatorWidth = if (indicator.width > 0) indicator.width else (indicator.layoutParams?.width ?: 0)
        if (indicatorWidth <= 0 || overlayView.width <= 0) {
            return
        }
        val clampedX = x.coerceIn(0f, overlayView.width.toFloat())
        indicator.translationX = clampedX - (indicatorWidth / 2f)
        indicator.translationY = 0f
    }

    private fun startSpeechRecognition(inputConnection: android.view.inputmethod.InputConnection?) {
        try {
            val intent = Intent(context, SpeechRecognitionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            context.startActivity(intent)
            Log.d(TAG, "Speech recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch speech recognition", e)
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Settings", e)
        }
    }
    
    private fun cancelLongPress() {
        longPressRunnable?.let { runnable ->
            longPressHandler?.removeCallbacks(runnable)
        }
        longPressHandler = null
        longPressRunnable = null
        // Don't reset longPressExecuted here, it needs to persist until ACTION_UP
    }

    private fun removeMicrophoneImmediate() {
        microphoneButtonView?.let { microphone ->
            (microphone.parent as? ViewGroup)?.removeView(microphone)
            microphone.visibility = View.GONE
            microphone.alpha = 1f
        }
    }

    private fun removeLanguageButtonImmediate() {
        languageButtonView?.let { language ->
            (language.parent as? ViewGroup)?.removeView(language)
            language.visibility = View.GONE
            language.alpha = 1f
        }
    }
    
    private fun removeClipboardButtonImmediate() {
        clipboardContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
        }
        clipboardButtonView?.apply {
            isPressed = false // Reset pressed state to avoid stuck highlight
            visibility = View.GONE
            alpha = 1f
        }
        clipboardFlashAnimator?.cancel()
        clipboardFlashAnimator = null
        clipboardFlashOverlay = null
    }

    private fun removeSettingsImmediate() {
        settingsButtonView?.let { settings ->
            (settings.parent as? ViewGroup)?.removeView(settings)
            settings.visibility = View.GONE
            settings.alpha = 1f
        }
    }

    private fun createVariationButton(
        variation: String,
        inputConnection: android.view.inputmethod.InputConnection?,
        buttonWidth: Int,
        maxButtonWidth: Int,
        isStatic: Boolean,
        isAddCandidate: Boolean,
        isSuggestion: Boolean = false,
        shouldDisableAutoCapitalize: Boolean = false
    ): TextView {
        val dp2 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f,
            context.resources.displayMetrics
        ).toInt()
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()

        // Calculate text width needed
        val paint = Paint().apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                16f,
                context.resources.displayMetrics
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val textWidth = paint.measureText(variation).toInt()
        val horizontalPadding = 0 // Testing with 0 padding
        val requiredWidth = textWidth + horizontalPadding
        
        // Use max of minimum width (buttonWidth) and required width, but cap at maxButtonWidth
        val calculatedWidth = max(buttonWidth, min(requiredWidth, maxButtonWidth))
        
        // Keep height fixed (square based on minimum width)
        val buttonHeight = buttonWidth

        val drawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateListDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), drawable)
        }

        return TextView(context).apply {
            text = variation
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, 0, 0, 0) // Testing with 0 padding
            if (isAddCandidate) {
                val addDrawable = ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)?.mutate()
                addDrawable?.setTint(Color.YELLOW)
                setCompoundDrawablesWithIntrinsicBounds(null, null, addDrawable, null)
                compoundDrawablePadding = dp4
            }
            background = stateListDrawable
            layoutParams = LinearLayout.LayoutParams(calculatedWidth, buttonHeight).apply {
                marginEnd = dp3
            }
            isClickable = true
            isFocusable = true
            setOnClickListener(
                if (isAddCandidate) {
                    View.OnClickListener {
                        onAddUserWord?.invoke(variation)
                    }
                } else if (isStatic) {
                    VariationButtonHandler.createStaticVariationClickListener(
                        variation,
                        inputConnection,
                        context,
                        onVariationSelectedListener
                    )
                } else if (isSuggestion) {
                    // Use SuggestionButtonHandler for word suggestions (replaces full word)
                    SuggestionButtonHandler.createSuggestionClickListener(
                        variation,
                        inputConnection,
                        onVariationSelectedListener,
                        shouldDisableAutoCapitalize
                    )
                } else {
                    // Use VariationButtonHandler for accent variations (replaces 1 char)
                    VariationButtonHandler.createVariationClickListener(
                        variation,
                        inputConnection,
                        context,
                        onVariationSelectedListener
                    )
                }
            )
        }
    }

    private fun createPlaceholderButton(buttonWidth: Int): View {
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()
        val drawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 0f
        }
        return View(context).apply {
            background = drawable
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth).apply {
                marginEnd = dp3
            }
            isClickable = false
            isFocusable = false
        }
    }

    private fun createSwipeIndicator(): View {
        val barWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(12)
        val drawable = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.argb(50, 255, 204, 0),
                Color.argb(170, 255, 221, 0),
                Color.argb(50, 255, 204, 0)
            )
        )
        return View(context).apply {
            background = drawable
            alpha = 0f
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(barWidth, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }
    }

    private fun createMicrophoneButton(buttonSize: Int): ImageView {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_mic_24)
            setColorFilter(Color.WHITE)
            background = stateList
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }
    }

    private fun createClipboardButton(buttonSize: Int): ImageView {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_content_paste_24)
            setColorFilter(Color.WHITE)
            background = stateList
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }
    }

    private fun createClipboardBadge(): TextView {
        val padding = dpToPx(2f)
        return TextView(context).apply {
            background = null
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            minWidth = 0
            minHeight = 0
            visibility = View.GONE
        }
    }

    private fun updateClipboardBadge(count: Int) {
        val badge = clipboardBadgeView ?: return
        if (count <= 0) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = count.toString()
    }

    private fun flashClipboardButton() {
        val overlay = clipboardFlashOverlay ?: return
        clipboardFlashAnimator?.cancel()
        overlay.visibility = View.VISIBLE
        val animator = ValueAnimator.ofFloat(0f, 0.4f, 0f).apply {
            duration = 350L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                overlay.alpha = valueAnimator.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlay.alpha = 0f
                    overlay.visibility = View.GONE
                }
            })
        }
        clipboardFlashAnimator = animator
        animator.start()
    }

    private fun createStatusBarSettingsButton(buttonSize: Int): ImageView {
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_settings_24)
            setColorFilter(Color.rgb(100, 100, 100))
            background = null
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setPadding(dp3, dp3, dp3, dp3)
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }
    }

    private fun createLanguageButton(buttonSize: Int): TextView {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }

        return TextView(context).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = stateList
            isClickable = true
            isFocusable = true
            includeFontPadding = false
            minHeight = buttonSize
            maxHeight = buttonSize
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }
    }

    /**
     * Updates the language button text with the current language code.
     */
    private fun updateLanguageButtonText(button: TextView) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val currentSubtype = imm.currentInputMethodSubtype
            val languageCode = if (currentSubtype != null) {
                // Extract language code from locale (e.g., "en_US" -> "EN", "it_IT" -> "IT")
                val locale = currentSubtype.locale
                locale.split("_").firstOrNull()?.uppercase() ?: "??"
            } else {
                "??"
            }
            button.text = languageCode
            applyLanguageLongPressHint(button, languageCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating language button text", e)
            applyLanguageLongPressHint(button, "??")
        }
    }

    private fun animateVariationsIn(view: View) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 75
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.alpha = 1f
                }
            })
        }.start()
    }

    private fun animateVariationsOut(view: View, onAnimationEnd: (() -> Unit)? = null) {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 50
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.alpha = 1f
                    onAnimationEnd?.invoke()
                }
            })
        }.start()
    }

    private fun findClickableViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is ViewGroup) {
            return if (x >= 0 && x < parent.width &&
                y >= 0 && y < parent.height &&
                parent.isClickable) {
                parent
            } else {
                null
            }
        }

        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val childLeft = child.left.toFloat()
                val childTop = child.top.toFloat()
                val childRight = child.right.toFloat()
                val childBottom = child.bottom.toFloat()

                if (x >= childLeft && x < childRight &&
                    y >= childTop && y < childBottom) {
                    val childX = x - childLeft
                    val childY = y - childTop
                    val result = findClickableViewAt(child, childX, childY)
                    if (result != null) {
                        return result
                    }
                }
            }
        }

        return if (parent.isClickable) parent else null
    }

    fun invalidateStaticVariations() {
        staticVariations = emptyList()
        emailVariations = emptyList()
    }

    /**
     * Updates the bar layout configuration.
     * Call this when settings change or on initialization.
     */
    fun updateBarLayoutConfig(config: BarLayoutConfig) {
        barLayoutConfig = config
        // Force rebuild of the bar on next showVariations call
        lastDisplayedVariations = emptyList()
        lastInputConnectionUsed = null
        lastIsStaticContent = null
    }

    /**
     * Executes the action configured for a slot.
     */
    private fun executeSlotAction(action: BarSlotAction, param: String?, inputConnection: android.view.inputmethod.InputConnection?) {
        when (action) {
            BarSlotAction.NONE -> { /* no-op */ }
            BarSlotAction.VOICE -> onSpeechRecognitionRequested?.invoke()
            BarSlotAction.CLIPBOARD -> onQuickPasteRequested?.invoke(inputConnection)
            BarSlotAction.CLIPBOARD_HISTORY -> onClipboardRequested?.invoke()
            BarSlotAction.EMOJI -> onEmojiRequested?.invoke()
            BarSlotAction.SETTINGS -> onSettingsRequested?.invoke()
            BarSlotAction.LANGUAGE_SWITCH -> onLanguageSwitchRequested?.invoke()
            BarSlotAction.APP_LAUNCH -> param?.let { onAppLaunchRequested?.invoke(it) }
        }
    }

    /**
     * Gets the icon resource for a slot action.
     */
    private fun getSlotActionIcon(action: BarSlotAction): Int? {
        return when (action) {
            BarSlotAction.VOICE -> R.drawable.ic_baseline_mic_24
            BarSlotAction.CLIPBOARD, BarSlotAction.CLIPBOARD_HISTORY -> R.drawable.ic_content_paste_24
            BarSlotAction.EMOJI -> android.R.drawable.ic_menu_slideshow // TODO: Add emoji icon
            BarSlotAction.SETTINGS -> R.drawable.ic_settings_24
            BarSlotAction.LANGUAGE_SWITCH -> null // Uses text label
            BarSlotAction.APP_LAUNCH -> android.R.drawable.ic_menu_share // TODO: Load app icon
            BarSlotAction.NONE -> null
        }
    }

    /**
     * Creates a slot button with the configured action.
     * Also tracks special buttons (microphone, clipboard) for feature integration.
     */
    private fun createSlotButton(
        slotConfig: BarSlotConfig,
        slotWidth: Int,
        slotHeight: Int,
        inputConnection: android.view.inputmethod.InputConnection?
    ): View {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }

        val iconRes = getSlotActionIcon(slotConfig.tapAction)

        // Special handling for VOICE action - track the button for audio feedback
        if (slotConfig.tapAction == BarSlotAction.VOICE) {
            val micButton = ImageView(context).apply {
                setImageResource(R.drawable.ic_baseline_mic_24)
                setColorFilter(Color.WHITE)
                background = stateList
                scaleType = ImageView.ScaleType.CENTER
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(slotWidth, slotHeight)

                setOnClickListener {
                    executeSlotAction(slotConfig.tapAction, slotConfig.tapParam, inputConnection)
                }

                if (slotConfig.longPressAction != BarSlotAction.NONE) {
                    isLongClickable = true
                    setOnLongClickListener {
                        executeSlotAction(slotConfig.longPressAction, slotConfig.longPressParam, inputConnection)
                        true
                    }
                }
            }
            microphoneButtonView = micButton
            return micButton
        }

        // Special handling for CLIPBOARD actions - wrap in container for badge
        if (slotConfig.tapAction == BarSlotAction.CLIPBOARD || slotConfig.tapAction == BarSlotAction.CLIPBOARD_HISTORY) {
            val clipButton = ImageView(context).apply {
                setImageResource(R.drawable.ic_content_paste_24)
                setColorFilter(Color.WHITE)
                background = stateList
                scaleType = ImageView.ScaleType.CENTER
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    executeSlotAction(slotConfig.tapAction, slotConfig.tapParam, inputConnection)
                }

                if (slotConfig.longPressAction != BarSlotAction.NONE) {
                    isLongClickable = true
                    setOnLongClickListener {
                        executeSlotAction(slotConfig.longPressAction, slotConfig.longPressParam, inputConnection)
                        true
                    }
                }
            }
            clipboardButtonView = clipButton

            // Create container with badge
            val clipContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(slotWidth, slotHeight)
            }
            clipboardContainer = clipContainer

            clipContainer.addView(clipButton, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // Flash overlay
            val flashOverlay = View(context).apply {
                setBackgroundColor(Color.WHITE)
                alpha = 0f
                visibility = View.GONE
            }
            clipboardFlashOverlay = flashOverlay
            clipContainer.addView(flashOverlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // Badge
            val badge = clipboardBadgeView ?: createClipboardBadge()
            clipboardBadgeView = badge
            (badge.parent as? ViewGroup)?.removeView(badge)
            clipContainer.addView(badge, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.END })

            return clipContainer
        }

        // Standard icon button
        if (iconRes != null) {
            return ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                background = stateList
                scaleType = ImageView.ScaleType.CENTER
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(slotWidth, slotHeight)

                setOnClickListener {
                    executeSlotAction(slotConfig.tapAction, slotConfig.tapParam, inputConnection)
                }

                if (slotConfig.longPressAction != BarSlotAction.NONE) {
                    isLongClickable = true
                    setOnLongClickListener {
                        executeSlotAction(slotConfig.longPressAction, slotConfig.longPressParam, inputConnection)
                        true
                    }
                }
            }
        }

        // Language switch button (text-based)
        if (slotConfig.tapAction == BarSlotAction.LANGUAGE_SWITCH) {
            return createLanguageButton(slotWidth).apply {
                updateLanguageButtonText(this)
                layoutParams = LinearLayout.LayoutParams(slotWidth, slotHeight)
                setOnClickListener {
                    executeSlotAction(slotConfig.tapAction, slotConfig.tapParam, inputConnection)
                }
                if (slotConfig.longPressAction != BarSlotAction.NONE) {
                    isLongClickable = true
                    setOnLongClickListener {
                        executeSlotAction(slotConfig.longPressAction, slotConfig.longPressParam, inputConnection)
                        true
                    }
                }
            }
        }

        // Empty/transparent slot (NONE action)
        return View(context).apply {
            background = null
            isClickable = false
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(slotWidth, slotHeight)
        }
    }

    /**
     * Builds the underlay with configurable slots.
     */
    private fun buildUnderlay(
        availableWidth: Int,
        slotHeight: Int,
        inputConnection: android.view.inputmethod.InputConnection?
    ): LinearLayout {
        underlaySlotViews.clear()

        val slotCount = barLayoutConfig.slotCount
        val slotWidth = availableWidth / slotCount

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            for (i in 0 until slotCount) {
                val slotConfig = barLayoutConfig.getSlot(i)
                val button = createSlotButton(slotConfig, slotWidth, slotHeight, inputConnection)
                underlaySlotViews.add(button)
                addView(button)
            }
        }
    }

    /**
     * Builds the suggestions overlay.
     */
    private fun buildSuggestionsOverlay(
        suggestions: List<String>,
        availableWidth: Int,
        slotHeight: Int,
        inputConnection: android.view.inputmethod.InputConnection?,
        isStaticContent: Boolean,
        addWordCandidate: String?
    ): LinearLayout {
        val spacingBetweenButtons = dpToPx(3f)
        val displaySuggestions = suggestions.take(3)

        val totalSpacing = spacingBetweenButtons * (displaySuggestions.size - 1).coerceAtLeast(0)
        val suggestionWidth = if (displaySuggestions.isNotEmpty()) {
            max(1, (availableWidth - totalSpacing) / displaySuggestions.size)
        } else {
            0
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(230, 17, 17, 17)) // Semi-transparent so underlay peeks through
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            for ((index, suggestion) in displaySuggestions.withIndex()) {
                val isAddCandidate = !isStaticContent && addWordCandidate == suggestion
                val button = createVariationButton(
                    suggestion,
                    inputConnection,
                    suggestionWidth,
                    suggestionWidth,
                    isStaticContent,
                    isAddCandidate
                )
                val params = LinearLayout.LayoutParams(suggestionWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                if (index > 0) {
                    params.marginStart = spacingBetweenButtons
                }
                addView(button, params)
            }
        }
    }

    /**
     * Updates the language button text with the current language code.
     */
    fun updateLanguageButtonText() {
        languageButtonView?.let { button ->
            updateLanguageButtonText(button)
        }
    }

    private fun applyLanguageLongPressHint(button: TextView, languageCode: String) {
        // Clear any icons so the label stays perfectly centered.
        button.setCompoundDrawables(null, null, null, null)
        button.compoundDrawablePadding = 0
        button.gravity = Gravity.CENTER
        button.textAlignment = View.TEXT_ALIGNMENT_CENTER
        button.setPadding(0, 0, 0, 0)

        val paintCopy = TextPaint(button.paint).apply {
            textSize = button.textSize
        }
        val textWidth = paintCopy.measureText(languageCode).coerceAtLeast(1f)
        // Target 3 dashes -> 3 dash segments + 2 gaps = 5 units.
        val dashLength = max(dpToPx(2f).toFloat(), textWidth / 5f)
        val gapLength = dashLength
        val dashEffect = DashPathEffect(floatArrayOf(dashLength, gapLength), 0f)

        val dottedText = SpannableString(languageCode).apply {
            setSpan(
                object : UnderlineSpan() {
                    override fun updateDrawState(tp: TextPaint) {
                        super.updateDrawState(tp)
                        tp.isUnderlineText = true
                        // Use a dashed underline to hint the long-press action.
                        tp.pathEffect = dashEffect
                    }
                },
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        button.text = dottedText
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
