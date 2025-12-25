package com.titankeys.keyboard.inputmethod

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.util.Log
import android.util.TypedValue
import com.titankeys.keyboard.R
import com.titankeys.keyboard.MainActivity
import com.titankeys.keyboard.SymCustomizationActivity
import com.titankeys.keyboard.SettingsManager
import kotlin.math.max
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.InputDevice
import kotlin.math.abs
import com.titankeys.keyboard.inputmethod.ui.ClipboardHistoryView
import com.titankeys.keyboard.inputmethod.ui.LedStatusView
import com.titankeys.keyboard.inputmethod.ui.VariationBarView
import com.titankeys.keyboard.core.suggestions.SuggestionMode
import android.content.res.AssetManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Manages the status bar shown by the IME, handling view creation
 * and updating text/style based on modifier states.
 */
class StatusBarController(
    private val context: Context,
    private val mode: Mode = Mode.FULL,
    private val clipboardHistoryManager: com.titankeys.keyboard.clipboard.ClipboardHistoryManager? = null,
    private val assets: AssetManager? = null,
    private val imeServiceClass: Class<*>? = null
) {
    enum class Mode {
        FULL,
        CANDIDATES_ONLY
    }

    // Listener for variation selection
    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
        set(value) {
            field = value
            variationBarView?.onVariationSelectedListener = value
        }
    
    // Listener for cursor movement (to update variations)
    var onCursorMovedListener: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onCursorMovedListener = value
        }
    
    // Listener for speech recognition request
    var onSpeechRecognitionRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onSpeechRecognitionRequested = value
        }

    var onAddUserWord: ((String) -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onAddUserWord = value
        }

    var onLanguageSwitchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onLanguageSwitchRequested = value
        }

    var onClipboardRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onClipboardRequested = value
        }
    var onQuickPasteRequested: ((android.view.inputmethod.InputConnection?) -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onQuickPasteRequested = value
        }

    // New callbacks for bar layout customization
    var onEmojiRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onEmojiRequested = value
        }

    var onSettingsRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onSettingsRequested = value
        }

    var onAppLaunchRequested: ((String) -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onAppLaunchRequested = value
        }

    // Callback for speech recognition state changes (active/inactive)
    var onSpeechRecognitionStateChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            // Note: VariationBarView doesn't need this directly, but we can add it if needed
        }
    
    fun invalidateStaticVariations() {
        variationBarView?.invalidateStaticVariations()
    }

    /**
     * Updates the bar layout configuration.
     * Call this when settings change or on initialization.
     */
    fun updateBarLayoutConfig(config: com.titankeys.keyboard.inputmethod.ui.BarLayoutConfig) {
        variationBarView?.updateBarLayoutConfig(config)
    }

    /**
     * Reloads the bar layout configuration from settings.
     */
    fun reloadBarLayoutConfig() {
        val config = SettingsManager.getBarLayoutConfig(context)
        updateBarLayoutConfig(config)
    }
    
    /**
     * Sets the microphone button active state.
     */
    fun setMicrophoneButtonActive(isActive: Boolean) {
        variationBarView?.setMicrophoneButtonActive(isActive)
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     * @param rmsdB The RMS audio level in decibels (typically -10 to 0)
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        variationBarView?.updateMicrophoneAudioLevel(rmsdB)
    }
    
    /**
     * Shows or hides the speech recognition hint message.
     * When showing, replaces the swipe hint with speech recognition message.
     */
    fun showSpeechRecognitionHint(show: Boolean) {
        variationBarView?.showSpeechRecognitionHint(show)
    }

    /**
     * Updates only the clipboard badge count without re-rendering variations.
     */
    fun updateClipboardCount(count: Int) {
        variationBarView?.updateClipboardCount(count)
    }

    /**
     * Briefly highlights a suggestion slot using the original suggestion index
     * ordering (0=center, 1=right, 2=left). Used for trackpad/swipe commits.
     */
    fun flashSuggestionSlot(suggestionIndex: Int) {
        // TODO: Implement in VariationBarView if needed
    }

    companion object {
        private const val TAG = "StatusBarController"
        private val DEFAULT_BACKGROUND = Color.parseColor("#000000")
    }

    data class StatusSnapshot(
        val capsLockEnabled: Boolean,
        val shiftPhysicallyPressed: Boolean,
        val shiftOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlPhysicallyPressed: Boolean,
        val ctrlOneShot: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altLatchActive: Boolean,
        val altPhysicallyPressed: Boolean,
        val altOneShot: Boolean,
        val symPage: Int, // 0=disabled, 1=page1 emoji, 2=page2 characters
        val clipboardOverlay: Boolean = false, // show clipboard as dedicated view
        val clipboardCount: Int = 0, // number of clipboard entries
        val variations: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val suggestionMode: SuggestionMode = SuggestionMode.CURRENT_WORD,
        val addWordCandidate: String? = null,
        val lastInsertedChar: Char? = null,
        // Granular smart features flags
        val shouldDisableSuggestions: Boolean = false,
        val shouldDisableAutoCorrect: Boolean = false,
        val shouldDisableAutoCapitalize: Boolean = false,
        val shouldDisableDoubleSpaceToPeriod: Boolean = false,
        val shouldDisableVariations: Boolean = false,
        val isEmailField: Boolean = false
    ) {
        val navModeActive: Boolean
            get() = ctrlLatchActive && ctrlLatchFromNavMode
    }

    private var statusBarLayout: LinearLayout? = null
    private var modifiersContainer: LinearLayout? = null
    private var emojiMapTextView: TextView? = null
    private var emojiKeyboardContainer: LinearLayout? = null
    private var emojiKeyboardHorizontalPaddingPx: Int = 0
    private var emojiKeyboardBottomPaddingPx: Int = 0
    private var clipboardHistoryView: ClipboardHistoryView? = null
    private var emojiKeyButtons: MutableList<View> = mutableListOf()
    private var lastSymPageRendered: Int = 0
    private var lastSymMappingsRendered: Map<Int, String>? = null
    private var lastInputConnectionUsed: android.view.inputmethod.InputConnection? = null
    private var wasSymActive: Boolean = false

    // Trackpad debug
    private var trackpadDebugLaunched = false
    private var symShown: Boolean = false
    private var lastSymHeight: Int = 0
    private val defaultSymHeightPx: Int
        get() = dpToPx(600f) // fallback when nothing measured yet
    private val ledStatusView = LedStatusView(context)
    private val variationBarView: VariationBarView? = if (mode == Mode.FULL) VariationBarView(context, assets, imeServiceClass) else null
    private var variationsWrapper: View? = null
    private var forceMinimalUi: Boolean = false
    private var baseBottomPadding: Int = 0

    fun setForceMinimalUi(force: Boolean) {
        if (mode != Mode.FULL) {
            return
        }
        if (forceMinimalUi == force) {
            return
        }
        forceMinimalUi = force
        if (force) {
            variationBarView?.hideImmediate()
        }
    }

    fun isMinimalUiActive(): Boolean = forceMinimalUi

    fun getLayout(): LinearLayout? = statusBarLayout

    fun getOrCreateLayout(emojiMapText: String = ""): LinearLayout {
        if (statusBarLayout == null) {
            statusBarLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
            }
            statusBarLayout?.let { layout ->
                baseBottomPadding = layout.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
                    // Preserve space for the system IME switcher / nav bar while keeping zero extra gap otherwise
                    val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                    val bottomInset = maxOf(navInsets.bottom, imeInsets.bottom, cutout.bottom)
                    view.updatePadding(bottom = baseBottomPadding + bottomInset)
                    insets
                }
            }

            // Container for modifier indicators (horizontal, left-aligned).
            // Add left padding to avoid the IME collapse button.
            val leftPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                64f, 
                context.resources.displayMetrics
            ).toInt()
            val horizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                16f, 
                context.resources.displayMetrics
            ).toInt()
            val verticalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                8f, 
                context.resources.displayMetrics
            ).toInt()
            
            modifiersContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(leftPadding, verticalPadding, horizontalPadding, verticalPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }

            // Container for emoji grid (when SYM is active) - placed at the bottom
            val emojiKeyboardHorizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                context.resources.displayMetrics
            ).toInt()
            val emojiKeyboardBottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f, // Bottom padding to avoid IME controls
                context.resources.displayMetrics
            ).toInt()
            emojiKeyboardHorizontalPaddingPx = emojiKeyboardHorizontalPadding
            emojiKeyboardBottomPaddingPx = emojiKeyboardBottomPadding
            
            emojiKeyboardContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                // No top padding, only horizontal and bottom
                setPadding(emojiKeyboardHorizontalPadding, 0, emojiKeyboardHorizontalPadding, emojiKeyboardBottomPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }
            
            // Keep the TextView for backward compatibility (hidden)
            emojiMapTextView = TextView(context).apply {
                visibility = View.GONE
            }

            variationsWrapper = variationBarView?.ensureView()
            val ledStrip = ledStatusView.ensureView()

            statusBarLayout?.apply {
                addView(modifiersContainer)
                addView(emojiKeyboardContainer) // Emoji grid
                variationsWrapper?.let { addView(it) } // Variation bar with Voice/Clipboard/Suggestions
                addView(ledStrip) // LED strip
            }
            statusBarLayout?.let { ViewCompat.requestApplyInsets(it) }
        } else if (emojiMapText.isNotEmpty()) {
            emojiMapTextView?.text = emojiMapText
        }
        return statusBarLayout!!
    }

    private fun launchTrackpadDebug() {
        if (!trackpadDebugLaunched) {
            trackpadDebugLaunched = true
            val intent = Intent(context, com.titankeys.keyboard.TrackpadDebugActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Ensures the layout is created before updating.
     * This is important for candidates view which may not have been created yet.
     */
    private fun ensureLayoutCreated(emojiMapText: String = ""): LinearLayout? {
        return statusBarLayout ?: getOrCreateLayout(emojiMapText)
    }
    
    /**
     * Recursively finds a clickable view at the given coordinates in the view hierarchy.
     * Coordinates are relative to the parent view.
     */
    private fun findClickableViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is ViewGroup) {
            // Single view: check if it's clickable and contains the point
            if (x >= 0 && x < parent.width &&
                y >= 0 && y < parent.height &&
                parent.isClickable) {
                return parent
            }
            return null
        }
        
        // For ViewGroup, check children first (they are on top)
        // Iterate in reverse to check topmost views first
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val childLeft = child.left.toFloat()
                val childTop = child.top.toFloat()
                val childRight = child.right.toFloat()
                val childBottom = child.bottom.toFloat()
                
                if (x >= childLeft && x < childRight &&
                    y >= childTop && y < childBottom) {
                    // Point is inside this child, recurse with relative coordinates
                    val childX = x - childLeft
                    val childY = y - childTop
                    val found = findClickableViewAt(child, childX, childY)
                    if (found != null) {
                        return found
                    }
                    
                    // If child itself is clickable, return it
                    if (child.isClickable) {
                        return child
                    }
                }
            }
        }
        
        // If no child was found and parent is clickable, return parent
        if (parent.isClickable) {
            return parent
        }
        
        return null
    }
    
    /**
     * Creates a modifier indicator (deprecated, kept for compatibility).
     */
    private fun createModifierIndicator(text: String, isActive: Boolean): TextView {
        val dp8 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            8f, 
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            6f, 
            context.resources.displayMetrics
        ).toInt()
        
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(if (isActive) Color.WHITE else Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(dp6, dp8, dp6, dp8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp8 // Right margin between indicators
            }
        }
    }
    
    /**
     * Updates the clipboard history view inline in the keyboard container.
     */
    private fun updateClipboardView(inputConnection: android.view.inputmethod.InputConnection? = null) {
        val manager = clipboardHistoryManager ?: return
        val container = emojiKeyboardContainer ?: return
        // Clipboard page should be edge-to-edge; remove the SYM container side padding.
        container.setPadding(0, 0, 0, emojiKeyboardBottomPaddingPx)

        // Reuse the same view to avoid flicker caused by removeAllViews()/recreate on each status update.
        val view = clipboardHistoryView ?: ClipboardHistoryView(context, manager).also { clipboardHistoryView = it }
        if (view.parent !== container) {
            container.removeAllViews()
            emojiKeyButtons.clear()
            container.addView(view)
        }
        view.setInputConnection(inputConnection)

        // Always refresh when clipboard page is shown to catch new entries
        // that might have same count (due to retention cleanup removing old + adding new).
        // The view uses DiffUtil internally for efficient updates.
        manager.prepareClipboardHistory()
        view.refresh()
        lastSymPageRendered = 3
    }

    /**
     * Updates the emoji/character grid with SYM mappings.
     * @param symMappings The mappings to display
     * @param page The active page (1=emoji, 2=characters)
     * @param inputConnection The input connection to insert characters when buttons are clicked
     */
    private fun updateEmojiKeyboard(symMappings: Map<Int, String>, page: Int, inputConnection: android.view.inputmethod.InputConnection? = null) {
        val container = emojiKeyboardContainer ?: return
        // Restore default padding for emoji/symbols pages.
        container.setPadding(emojiKeyboardHorizontalPaddingPx, 0, emojiKeyboardHorizontalPaddingPx, emojiKeyboardBottomPaddingPx)
        val inputConnectionChanged = lastInputConnectionUsed != inputConnection
        val inputConnectionBecameAvailable = lastInputConnectionUsed == null && inputConnection != null
        if (lastSymPageRendered == page && lastSymMappingsRendered == symMappings && !inputConnectionChanged && !inputConnectionBecameAvailable) {
            return
        }
        
        // Remove all existing keys
        container.removeAllViews()
        emojiKeyButtons.clear()
        
        // Keyboard row definitions
        val keyboardRows = listOf(
            listOf(android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_E, 
                   android.view.KeyEvent.KEYCODE_R, android.view.KeyEvent.KEYCODE_T, android.view.KeyEvent.KEYCODE_Y, 
                   android.view.KeyEvent.KEYCODE_U, android.view.KeyEvent.KEYCODE_I, android.view.KeyEvent.KEYCODE_O, 
                   android.view.KeyEvent.KEYCODE_P),
            listOf(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D, 
                   android.view.KeyEvent.KEYCODE_F, android.view.KeyEvent.KEYCODE_G, android.view.KeyEvent.KEYCODE_H, 
                   android.view.KeyEvent.KEYCODE_J, android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_L),
            listOf(android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.KEYCODE_X, android.view.KeyEvent.KEYCODE_C, 
                   android.view.KeyEvent.KEYCODE_V, android.view.KeyEvent.KEYCODE_B, android.view.KeyEvent.KEYCODE_N, 
                   android.view.KeyEvent.KEYCODE_M)
        )
        
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Calculate fixed key width based on first row (10 keys)
        val maxKeysInRow = 10 // First row has 10 keys
        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f * 2, // left + right padding
            context.resources.displayMetrics
        ).toInt()
        val availableWidth = screenWidth - horizontalPadding
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        val fixedKeyWidth = (availableWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56f,
            context.resources.displayMetrics
        ).toInt()
        
        // Create each keyboard row
        for ((rowIndex, row) in keyboardRows.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL // Center shorter rows
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    // Add margin only between rows, not after the last one
                    if (rowIndex < keyboardRows.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            // For the third row, add transparent placeholder on the left
            if (rowIndex == 2) {
                val leftPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginEnd = keySpacing
                })
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = keyLabels[keyCode] ?: ""
                val content = symMappings[keyCode] ?: ""
                
                val keyButton = createEmojiKeyButton(label, content, keyHeight, page)
                emojiKeyButtons.add(keyButton)
                
                // Add click listener to make button touchable
                if (content.isNotEmpty() && inputConnection != null) {
                    keyButton.isClickable = true
                    keyButton.isFocusable = true
                    
                    // Use only OnTouchListener for feedback + click (more efficient)
                    val originalBackground = keyButton.background as? GradientDrawable
                    if (originalBackground != null) {
                        val normalColor = Color.argb(40, 255, 255, 255)
                        val pressedColor = Color.argb(80, 255, 255, 255)
                        
                        keyButton.setOnTouchListener { view, motionEvent ->
                            when (motionEvent.action) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    originalBackground.setColor(pressedColor)
                                    view.postInvalidate()
                                    true // Consume for immediate feedback
                                }
                                android.view.MotionEvent.ACTION_UP -> {
                                    originalBackground.setColor(normalColor)
                                    view.postInvalidate()
                                    // Execute commitText directly here (faster)
                                    inputConnection.commitText(content, 1)
                                    true
                                }
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    originalBackground.setColor(normalColor)
                                    view.postInvalidate()
                                    true
                                }
                                else -> false
                            }
                        }
                    } else {
                        // Fallback: only click listener if no background
                        keyButton.setOnClickListener {
                            inputConnection.commitText(content, 1)
                        }
                    }
                }
                
                // Use fixed width instead of weight
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    // Add margin only if not the last key in the row
                    if (index < row.size - 1) {
                        marginEnd = keySpacing
                    }
                })
            }
            
            // For the third row, add placeholder with pencil icon on the right
            if (rowIndex == 2) {
                val rightPlaceholder = createPlaceholderWithPencilButton(keyHeight)
                rowLayout.addView(rightPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginStart = keySpacing
                })
            }
            
            container.addView(rowLayout)
        }

        // Cache what was rendered to avoid rebuilding on each status refresh
        lastSymPageRendered = page
        lastSymMappingsRendered = HashMap(symMappings)
        lastInputConnectionUsed = inputConnection
    }
    
    /**
     * Creates a transparent placeholder to align rows.
     */
    private fun createPlaceholderButton(height: Int): View {
        return FrameLayout(context).apply {
            background = null // Transparent
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            isClickable = false
            isFocusable = false
        }
    }
    
    /**
     * Creates a placeholder with pencil icon to open SYM customization screen.
     */
    private fun createPlaceholderWithPencilButton(height: Int): View {
        val placeholder = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        // Transparent background
        placeholder.background = null

        // Larger icon size
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            20f, // Increased for better visibility
            context.resources.displayMetrics
        ).toInt()
        
        val button = ImageView(context).apply {
            background = null
            setImageResource(R.drawable.ic_edit_24)
            setColorFilter(Color.WHITE) // White
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = iconSize
            maxHeight = iconSize
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize
            ).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true
            isFocusable = true
        }
        
        button.setOnClickListener {
            // Save current SYM page state temporarily (will be confirmed only if user presses back)
            val prefs = context.getSharedPreferences("titankeys_prefs", Context.MODE_PRIVATE)
            val currentSymPage = prefs.getInt("current_sym_page", 0)
            if (currentSymPage > 0) {
                // Save as pending - will be converted to restore only if user presses back
                SettingsManager.setPendingRestoreSymPage(context, currentSymPage)
            }
            
            // Open SymCustomizationActivity directly
            val intent = Intent(context, SymCustomizationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening SYM customization screen", e)
            }
        }
        
        placeholder.addView(button)
        return placeholder
    }
    
    /**
     * Creates an emoji/character grid key.
     * @param label The key letter
     * @param content The emoji or character to display
     * @param height The key height
     * @param page The active page (1=emoji, 2=characters)
     */
    private fun createEmojiKeyButton(label: String, content: String, height: Int, page: Int): View {
        val keyLayout = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0) // No padding to allow emoji to fill entire space
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        // Key background with slightly rounded corners
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f, // Slightly rounded corners
            context.resources.displayMetrics
        )
        val drawable = GradientDrawable().apply {
            setColor(Color.argb(40, 255, 255, 255)) // Semi-transparent white
            setCornerRadius(cornerRadius)
            // No border
        }
        keyLayout.background = drawable
        
        // Emoji/character should fill entire key, centered
        // Calculate textSize based on available height (converting from pixels to sp)
        val heightInDp = height / context.resources.displayMetrics.density
        val contentTextSize = if (page == 2) {
            // For unicode characters, use a smaller size
            (heightInDp * 0.5f)
        } else {
            // For emoji, use normal size
            (heightInDp * 0.75f)
        }
        
        val contentText = TextView(context).apply {
            text = content
            textSize = contentTextSize // textSize is in sp
            gravity = Gravity.CENTER
            // For page 2 (characters), make white and bold
            if (page == 2) {
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            // Width and height to fill all available space
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        
        // Label (letter) - positioned bottom right, in front of emoji
        val labelPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f, // Pochissimo margine
            context.resources.displayMetrics
        ).toInt()
        
        val labelText = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE) // 100% opaque white
            gravity = Gravity.END or Gravity.BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = labelPadding
                bottomMargin = labelPadding
            }
        }
        
        // Add content first (behind) then text (in front)
        keyLayout.addView(contentText)
        keyLayout.addView(labelText)
        
        return keyLayout
    }
    
    /**
     * Creates a customizable emoji grid (for the customization screen).
     * Returns a View that can be embedded in Compose via AndroidView.
     *
     * @param symMappings The emoji mappings to display
     * @param onKeyClick Callback called when a key is clicked (keyCode, emoji)
     */
    fun createCustomizableEmojiKeyboard(
        symMappings: Map<Int, String>,
        onKeyClick: (Int, String) -> Unit,
        page: Int = 1 // Default to page 1 (emoji)
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics
            ).toInt()
            setPadding(0, 0, 0, bottomPadding) // No horizontal padding, only bottom
            // Add black background to improve character visibility with light theme
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Keyboard row definitions (same structure as real keyboard)
        val keyboardRows = listOf(
            listOf(android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_E, 
                   android.view.KeyEvent.KEYCODE_R, android.view.KeyEvent.KEYCODE_T, android.view.KeyEvent.KEYCODE_Y, 
                   android.view.KeyEvent.KEYCODE_U, android.view.KeyEvent.KEYCODE_I, android.view.KeyEvent.KEYCODE_O, 
                   android.view.KeyEvent.KEYCODE_P),
            listOf(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D, 
                   android.view.KeyEvent.KEYCODE_F, android.view.KeyEvent.KEYCODE_G, android.view.KeyEvent.KEYCODE_H, 
                   android.view.KeyEvent.KEYCODE_J, android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_L),
            listOf(android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.KEYCODE_X, android.view.KeyEvent.KEYCODE_C, 
                   android.view.KeyEvent.KEYCODE_V, android.view.KeyEvent.KEYCODE_B, android.view.KeyEvent.KEYCODE_N, 
                   android.view.KeyEvent.KEYCODE_M)
        )
        
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Calculate fixed key width based on first row (10 keys)
        // Use ViewTreeObserver to get actual container width after layout
        val maxKeysInRow = 10 // First row has 10 keys

        // Initialize with temporary width, will be updated after layout
        var fixedKeyWidth = 0
        
        container.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val containerWidth = container.width
                if (containerWidth > 0) {
                    val totalSpacing = keySpacing * (maxKeysInRow - 1)
                    fixedKeyWidth = (containerWidth - totalSpacing) / maxKeysInRow
                    
                    // Update all keys with correct width
                    for (i in 0 until container.childCount) {
                        val rowLayout = container.getChildAt(i) as? LinearLayout
                        rowLayout?.let { row ->
                            for (j in 0 until row.childCount) {
                                val keyButton = row.getChildAt(j)
                                val layoutParams = keyButton.layoutParams as? LinearLayout.LayoutParams
                                layoutParams?.let {
                                    it.width = fixedKeyWidth
                                    keyButton.layoutParams = it
                                }
                            }
                        }
                    }
                    
                    // Remove listener after first layout
                    container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
        
        // Initial value based on screen width (will be updated by listener)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        fixedKeyWidth = (screenWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56f,
            context.resources.displayMetrics
        ).toInt()
        
        // Create each keyboard row (same structure as real keyboard)
        for ((rowIndex, row) in keyboardRows.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL // Center shorter rows
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex < keyboardRows.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            // For the third row, add transparent placeholder on the left
            if (rowIndex == 2) {
                val leftPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginEnd = keySpacing
                })
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = keyLabels[keyCode] ?: ""
                val emoji = symMappings[keyCode] ?: ""
                
                // Use the same createEmojiKeyButton function as real keyboard
                val keyButton = createEmojiKeyButton(label, emoji, keyHeight, page)
                
                // Add click listener
                keyButton.setOnClickListener {
                    onKeyClick(keyCode, emoji)
                }
                
                // Use fixed width instead of weight (same layout as real keyboard)
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    if (index < row.size - 1) {
                        marginEnd = keySpacing
                    }
                })
            }
            
            // For the third row in customization screen, add transparent placeholder on the right
            // to maintain alignment (no pencil and no click listener)
            if (rowIndex == 2) {
                val rightPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(rightPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginStart = keySpacing
                })
            }
            
            container.addView(rowLayout)
        }
        
        return container
    }
    
    /**
     * Animates emoji grid appearance with slide up only (no fade).
     * @param backgroundView The background view to set opaque immediately
     */
    private fun animateEmojiKeyboardIn(view: View, backgroundView: View? = null) {
        val height = view.height
        if (height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }
        val measuredHeight = view.measuredHeight

        view.alpha = 1f
        view.translationY = measuredHeight.toFloat()
        view.visibility = View.VISIBLE

        // Set background to opaque immediately without animation
        backgroundView?.let { bgView ->
            if (bgView.background !is ColorDrawable) {
                bgView.background = ColorDrawable(DEFAULT_BACKGROUND)
            }
            (bgView.background as? ColorDrawable)?.alpha = 255
        }

        val animator = ValueAnimator.ofFloat(measuredHeight.toFloat(), 0f).apply {
            duration = 125
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                view.translationY = value
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.translationY = 0f
                    view.alpha = 1f
                }
            })
        }
        animator.start()
    }
    
    /**
     * Animates emoji grid disappearance (slide down + fade out).
     * @param backgroundView The background view (not animated, stays opaque)
     * @param onAnimationEnd Callback called when animation is complete
     */
    private fun animateEmojiKeyboardOut(view: View, backgroundView: View? = null, onAnimationEnd: (() -> Unit)? = null) {
        val height = view.height
        if (height == 0) {
            view.visibility = View.GONE
            onAnimationEnd?.invoke()
            return
        }

        // Background remains opaque, no animation

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
                view.translationY = height * (1f - progress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.translationY = 0f
                    view.alpha = 1f
                    onAnimationEnd?.invoke()
                }
            })
        }
        animator.start()
    }

    
    

    fun update(snapshot: StatusSnapshot, emojiMapText: String = "", inputConnection: android.view.inputmethod.InputConnection? = null, symMappings: Map<Int, String>? = null) {
        variationBarView?.onVariationSelectedListener = onVariationSelectedListener
        variationBarView?.onCursorMovedListener = onCursorMovedListener
        variationBarView?.updateInputConnection(inputConnection)
        variationBarView?.setSymModeActive(snapshot.symPage > 0 || snapshot.clipboardOverlay)
        variationBarView?.updateLanguageButtonText()
        
        val layout = ensureLayoutCreated(emojiMapText) ?: return
        val modifiersContainerView = modifiersContainer ?: return
        val emojiView = emojiMapTextView ?: return
        val emojiKeyboardView = emojiKeyboardContainer ?: return
        emojiView.visibility = View.GONE
        
        if (snapshot.navModeActive) {
            layout.visibility = View.GONE
            return
        }
        layout.visibility = View.VISIBLE
        
        if (layout.background !is ColorDrawable) {
            layout.background = ColorDrawable(DEFAULT_BACKGROUND)
        } else if (snapshot.symPage == 0) {
            (layout.background as ColorDrawable).alpha = 255
        }
        
        modifiersContainerView.visibility = View.GONE
        ledStatusView.update(snapshot)
        val variationsBar = if (!forceMinimalUi) variationBarView else null
        val variationsWrapperView = if (!forceMinimalUi) variationsWrapper else null
        if (snapshot.clipboardOverlay) {
            // Show clipboard as dedicated overlay (not part of SYM pages)
            updateClipboardView(inputConnection)
            variationsBar?.resetVariationsState()

            // Pin background and hide variations while showing clipboard grid
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(DEFAULT_BACKGROUND)
            }
            (layout.background as? ColorDrawable)?.alpha = 255
            variationsWrapperView?.apply {
                visibility = View.INVISIBLE
                isEnabled = false
                isClickable = false
            }
            variationsBar?.hideImmediate()

            val measured = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout, forceReMeasure = true)
            val animationHeight = if (measured > 0) measured else defaultSymHeightPx
            emojiKeyboardView.setBackgroundColor(DEFAULT_BACKGROUND)
            emojiKeyboardView.visibility = View.VISIBLE
            // Use weight so the clipboard grid scrolls and leaves room for LED strip
            emojiKeyboardView.layoutParams = (emojiKeyboardView.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = 0
                weight = 1f
            }
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = animationHeight.toFloat()
                animateEmojiKeyboardIn(emojiKeyboardView, layout)
                symShown = true
                wasSymActive = true
            } else {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = 0f
                wasSymActive = true
            }
            return
        }

        if (snapshot.symPage > 0) {
            // Handle page 3 (clipboard) vs pages 1-2 (emoji/symbols)
            if (snapshot.symPage == 3) {
                // Show clipboard history inline (similar to emoji grid)
                updateClipboardView(inputConnection)
            } else if (symMappings != null) {
                updateEmojiKeyboard(symMappings, snapshot.symPage, inputConnection)
            }
            variationsBar?.resetVariationsState()

            // Pin background to opaque IME color and hide variations so SYM animates on a solid canvas.
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(DEFAULT_BACKGROUND)
            }
            (layout.background as? ColorDrawable)?.alpha = 255
            variationsWrapperView?.apply {
                visibility = View.INVISIBLE // keep space to avoid shrink/flash
                isEnabled = false
                isClickable = false
            }
            variationsBar?.hideImmediate()

            val measured = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout, forceReMeasure = true)
            val symHeight = if (measured > 0) measured else defaultSymHeightPx
            lastSymHeight = symHeight
            emojiKeyboardView.setBackgroundColor(DEFAULT_BACKGROUND)
            emojiKeyboardView.visibility = View.VISIBLE
            emojiKeyboardView.layoutParams = (emojiKeyboardView.layoutParams ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                symHeight
            )).apply { height = symHeight }
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f // keep black visible immediately
                emojiKeyboardView.translationY = symHeight.toFloat()
                animateEmojiKeyboardIn(emojiKeyboardView, layout)
                symShown = true
                wasSymActive = true
            } else {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = 0f
                wasSymActive = true
            }
            return
        }
        
        if (emojiKeyboardView.visibility == View.VISIBLE) {
            animateEmojiKeyboardOut(emojiKeyboardView, layout) {
                variationsWrapperView?.apply {
                    visibility = View.VISIBLE
                    isEnabled = true
                    isClickable = true
                }
                variationsBar?.showVariations(snapshot, inputConnection)
            }
            symShown = false
            wasSymActive = false
        } else {
            emojiKeyboardView.visibility = View.GONE
            variationsWrapperView?.apply {
                visibility = View.VISIBLE
                isEnabled = true
                isClickable = true
            }
            variationsBar?.showVariations(snapshot, inputConnection)
            symShown = false
            wasSymActive = false
        }
    }

    private fun ensureEmojiKeyboardMeasuredHeight(view: View, parent: View, forceReMeasure: Boolean = false): Int {
        if (view.height > 0 && !forceReMeasure) {
            return view.height
        }
        val width = if (parent.width > 0) parent.width else context.resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
