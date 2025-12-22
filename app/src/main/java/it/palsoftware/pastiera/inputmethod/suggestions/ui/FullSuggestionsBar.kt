package it.palsoftware.pastiera.inputmethod.suggestions.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.StateListDrawable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.inputmethod.suggestions.SuggestionButtonHandler
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import it.palsoftware.pastiera.inputmethod.SubtypeCycler
import it.palsoftware.pastiera.core.suggestions.SuggestionMode
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import it.palsoftware.pastiera.core.suggestions.DictionaryRepository

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

    private var container: LinearLayout? = null
    private var frameContainer: FrameLayout? = null
    private var languageButton: TextView? = null
    private var lastSlots: List<String?> = emptyList()
    private var lastMode: SuggestionMode = SuggestionMode.CURRENT_WORD
    private var assets: AssetManager? = null
    private var imeServiceClass: Class<*>? = null
    private var showLanguageButton: Boolean = false // Control visibility of language button
    private val suggestionButtons: MutableList<TextView> = mutableListOf()
    private var isTyping: Boolean = false
    private var fadeAnimator: android.view.ViewPropertyAnimator? = null
    private val targetHeightPx: Int by lazy {
        // Compact row sized around three suggestion pills
        dpToPx(36f)
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
     * When typing starts, the suggestions bar fades in.
     * When typing stops, the bar fades out after a delay to reveal the scroll bar underneath.
     */
    fun setTypingState(typing: Boolean) {
        if (isTyping == typing) return
        isTyping = typing

        val frame = frameContainer ?: return

        // Cancel any ongoing animation
        fadeAnimator?.cancel()
        fadeAnimator = null

        if (typing) {
            // Fade in when typing
            frame.visibility = View.VISIBLE
            fadeAnimator = frame.animate()
                .alpha(1f)
                .setDuration(150)
                .setListener(null)
            fadeAnimator?.start()
        } else {
            // Fade out after delay when stopped typing
            fadeAnimator = frame.animate()
                .alpha(0f)
                .setStartDelay(500) // Wait 500ms before fading out
                .setDuration(200)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isTyping) {
                            frame.visibility = View.INVISIBLE
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
            // Create frame container to allow overlaying the language button
            frameContainer = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    targetHeightPx
                )
                visibility = View.GONE
                minimumHeight = targetHeightPx
            }
            
            // Create the suggestions container
            container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    targetHeightPx
                )
                visibility = View.GONE
                minimumHeight = targetHeightPx
            }
            
            // Create language button positioned absolutely on the right
            languageButton = TextView(context).apply {
                text = getCurrentLanguageCode()
                gravity = Gravity.CENTER
                textSize = 12f
                includeFontPadding = false
                minHeight = 0
                maxLines = 1
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dpToPx(8f), dpToPx(2f), dpToPx(8f), dpToPx(2f))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(50, 50, 50))
                    cornerRadius = dpToPx(4f).toFloat()
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    marginEnd = dpToPx(4f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    cycleToNextSubtype()
                }
                setOnLongClickListener {
                    openSettings()
                    true
                }
            }
            
            frameContainer?.addView(container)
            frameContainer?.addView(languageButton)
            // Ensure the outer layout (when attached to parent LinearLayout) keeps the target height
            frameContainer?.layoutParams = (frameContainer?.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeightPx)
        }
        return frameContainer!!
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
            // Update button text after cycling
            languageButton?.text = getCurrentLanguageCode()
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
            languageButton?.visibility = View.GONE
            lastSlots = emptyList()
            return
        }

        frame.visibility = View.VISIBLE
        // Show or hide language button based on showLanguageButton flag
        languageButton?.visibility = if (showLanguageButton) View.VISIBLE else View.GONE
        // Update language button text in case subtype changed externally
        languageButton?.text = getCurrentLanguageCode()

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
        (bar.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.height = targetHeightPx
            bar.layoutParams = lp
        } ?: run {
            bar.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetHeightPx
            )
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
