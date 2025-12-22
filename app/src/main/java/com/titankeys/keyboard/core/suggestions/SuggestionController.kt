package com.titankeys.keyboard.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CancellationException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.titankeys.keyboard.inputmethod.NotificationHelper
import java.io.File
import org.json.JSONObject

class SuggestionController(
    context: Context,
    private val assets: AssetManager,
    private val settingsProvider: () -> SuggestionSettings,
    private val isEnabled: () -> Boolean = { true },
    debugLogging: Boolean = false,
    private val onSuggestionsUpdated: (List<SuggestionResult>) -> Unit,
    private var currentLocale: Locale = Locale.ENGLISH,
    private val keyboardLayoutProvider: () -> String = { "qwerty" }
) {

    private val appContext = context.applicationContext
    private val debugLogging: Boolean = debugLogging
    private val userDictionaryStore = UserDictionaryStore()
    private var dictionaryRepository = DictionaryRepository(appContext, assets, userDictionaryStore, baseLocale = currentLocale, debugLogging = debugLogging)
    private var suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging).apply {
        setKeyboardLayout(keyboardLayoutProvider())
    }
    
    // Next-word prediction components
    private val contextTracker = ContextTracker(maxContextLength = 3, locale = currentLocale, debugLogging = debugLogging)
    private val userLearningStore = UserLearningStore(locale = currentLocale, debugLogging = debugLogging)
    private var ngramLanguageModel = NgramLanguageModel(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging)
    private var nextWordPredictor = NextWordPredictor(ngramLanguageModel, userLearningStore, dictionaryRepository, locale = currentLocale, debugLogging = debugLogging)
    private val adaptiveMode = AdaptiveSuggestionMode(settingsProvider())
    
    // Load user learning data
    init {
        userLearningStore.load(appContext)
    }
    
    private var tracker = CurrentWordTracker(
        onWordChanged = { word ->
            val settings = settingsProvider()
            if (settings.suggestionsEnabled) {
                if (debugLogging) {
                    Log.d("TitanKeysIME", "trackerWordChanged='$word' len=${word.length}")
                }
                // Determine mode and get appropriate suggestions
                val mode = adaptiveMode.determineMode(
                    hasCurrentWord = word.isNotBlank(),
                    contextLength = contextTracker.size(),
                    nextWordPredictionEnabled = settings.nextWordPredictionEnabled
                )
                
                val suggestions = when (mode) {
                    SuggestionMode.CURRENT_WORD -> {
                        // Current word suggestions
                        suggestionEngine.suggest(word, settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking)
                    }
                    SuggestionMode.NEXT_WORD -> {
                        // Next word predictions
                        val context = contextTracker.getContext()
                        val predictions = nextWordPredictor.predict(context, settings.maxSuggestions)
                        // Convert predictions to SuggestionResult format
                        predictions.mapIndexed { index, pred ->
                            SuggestionResult(
                                candidate = pred,
                                distance = 0,
                                score = (predictions.size - index).toDouble(),
                                source = SuggestionSource.MAIN
                            )
                        }
                    }
                    SuggestionMode.HYBRID -> {
                        // Show both: current word suggestions if typing, otherwise next-word predictions
                        if (word.isNotBlank()) {
                            suggestionEngine.suggest(word, settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking)
                        } else {
                            val context = contextTracker.getContext()
                            val predictions = nextWordPredictor.predict(context, settings.maxSuggestions)
                            predictions.mapIndexed { index, pred ->
                                SuggestionResult(
                                    candidate = pred,
                                    distance = 0,
                                    score = (predictions.size - index).toDouble(),
                                    source = SuggestionSource.MAIN
                                )
                            }
                        }
                    }
                }
                
                latestSuggestions.set(suggestions)
                latestMode.set(mode)
                suggestionsListener?.invoke(suggestions)
            }
        },
        onWordReset = {
            latestSuggestions.set(emptyList())
            latestMode.set(SuggestionMode.CURRENT_WORD)
            suggestionsListener?.invoke(emptyList())
        }
    )
    private var autoReplaceController = AutoReplaceController(dictionaryRepository, suggestionEngine, settingsProvider)
    
    /**
     * Updates the locale and reloads the dictionary for the new language.
     */
    fun updateLocale(newLocale: Locale) {
        if (newLocale == currentLocale) return
        
        // Cancel previous load job if still running to prevent conflicts
        currentLoadJob?.cancel()
        currentLoadJob = null
        
        currentLocale = newLocale
        dictionaryRepository = DictionaryRepository(appContext, assets, userDictionaryStore, baseLocale = currentLocale, debugLogging = debugLogging)
        suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging).apply {
            setKeyboardLayout(keyboardLayoutProvider())
        }
        autoReplaceController = AutoReplaceController(dictionaryRepository, suggestionEngine, settingsProvider)
        
        // Recreate next-word prediction components
        contextTracker.reset()
        ngramLanguageModel = NgramLanguageModel(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging)
        nextWordPredictor = NextWordPredictor(ngramLanguageModel, userLearningStore, dictionaryRepository, locale = currentLocale, debugLogging = debugLogging)
        
        // Recreate tracker to use new engine (tracker captures suggestionEngine in closure)
        tracker = CurrentWordTracker(
            onWordChanged = { word ->
                val settings = settingsProvider()
                if (settings.suggestionsEnabled) {
                    val next = suggestionEngine.suggest(word, settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking)
                    latestSuggestions.set(next)
                    suggestionsListener?.invoke(next)
                }
            },
            onWordReset = {
                latestSuggestions.set(emptyList())
                suggestionsListener?.invoke(emptyList())
            }
        )
        
        // Reload dictionary in background
        currentLoadJob = loadScope.launch {
            dictionaryRepository.loadIfNeeded()
            // Load n-gram data after dictionary is ready
            if (dictionaryRepository.isReady) {
                ngramLanguageModel.loadNgrams(
                    dictionaryRepository.getBigrams(),
                    dictionaryRepository.getTrigrams()
                )
            }
        }
        
        // Reset tracker and clear suggestions
        tracker.reset()
        contextTracker.reset()
        suggestionsListener?.invoke(emptyList())
    }

    /**
     * Updates the keyboard layout for proximity-based ranking.
     */
    fun updateKeyboardLayout(layout: String) {
        suggestionEngine.setKeyboardLayout(layout)
    }

    private val latestSuggestions: AtomicReference<List<SuggestionResult>> = AtomicReference(emptyList())
    private val latestMode: AtomicReference<SuggestionMode> = AtomicReference(SuggestionMode.CURRENT_WORD)
    // Dedicated IO scope so dictionary preload never blocks the main thread.
    private val loadScope = CoroutineScope(Dispatchers.IO)
    private var currentLoadJob: Job? = null
    private val cursorHandler = Handler(Looper.getMainLooper())
    private var cursorRunnable: Runnable? = null
    private val cursorDebounceMs = 120L
    private var pendingAddUserWord: String? = null
    
    // #region agent log
    private fun debugLog(hypothesisId: String, location: String, message: String, data: Map<String, Any?> = emptyMap()) {
        try {
            val logFile = File(".cursor/debug.log")
            val logEntry = JSONObject().apply {
                put("sessionId", "debug-session")
                put("runId", "run1")
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                put("data", JSONObject(data))
            }
            logFile.appendText(logEntry.toString() + "\n")
        } catch (e: Exception) {
            // Ignore log errors
        }
    }
    // #endregion

    var suggestionsListener: ((List<SuggestionResult>) -> Unit)? = onSuggestionsUpdated

    fun onCharacterCommitted(text: CharSequence, inputConnection: InputConnection?) {
        if (!isEnabled()) return
        // #region agent log
        val trackerWordBefore = tracker.currentWord
        debugLog("A", "SuggestionController.onCharacterCommitted:entry", "onCharacterCommitted called", mapOf(
            "text" to text.toString(),
            "trackerWordBefore" to trackerWordBefore,
            "trackerWordLengthBefore" to trackerWordBefore.length
        ))
        // #endregion
        if (debugLogging) {
            val caller = Throwable().stackTrace.getOrNull(1)?.let { "${it.className}#${it.methodName}:${it.lineNumber}" }
            Log.d("TitanKeysIME", "SuggestionController.onCharacterCommitted('$text') caller=$caller")
        }
        ensureDictionaryLoaded()

        // Normalize curly/variant apostrophes to straight for tracking and suggestions.
        val normalizedText = text
            .toString()
            .replace("'", "'")
            .replace("'", "'")
            .replace("ʼ", "'")
        
        // Clear last replacement if user types new characters
        autoReplaceController.clearLastReplacement()
        
        // Clear rejected words when user types a new letter (allows re-correction)
        if (normalizedText.isNotEmpty() && normalizedText.any { it.isLetterOrDigit() }) {
            autoReplaceController.clearRejectedWords()
            pendingAddUserWord = null
        }
        
        tracker.onCharacterCommitted(normalizedText)
        // #region agent log
        val trackerWordAfter = tracker.currentWord
        debugLog("A", "SuggestionController.onCharacterCommitted:exit", "tracker updated after onCharacterCommitted", mapOf(
            "trackerWordAfter" to trackerWordAfter,
            "trackerWordLengthAfter" to trackerWordAfter.length,
            "normalizedText" to normalizedText
        ))
        // #endregion
    }

    fun refreshFromInputConnection(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        tracker.onBackspace()
    }

    fun onBoundaryKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?
    ): AutoReplaceController.ReplaceResult {
        if (debugLogging) {
            Log.d(
                "TitanKeysIME",
                "SuggestionController.onBoundaryKey keyCode=$keyCode char=${event?.unicodeChar}"
            )
        }
        ensureDictionaryLoaded()

        // CRITICAL FIX: Sync tracker with actual text before processing boundary
        // The cursor debounce can cause tracker to be out of sync with the actual text field
        if (inputConnection != null && dictionaryRepository.isReady) {
            val word = extractWordAtCursor(inputConnection)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
                Log.d("TitanKeysIME", "SYNC: Synced tracker to actual word='$word' before boundary")
            }
        }

        val result = autoReplaceController.handleBoundary(keyCode, event, tracker, inputConnection)
        
        // Update context and learning after word is committed
        val settings = settingsProvider()
        val completedWord = tracker.currentWord
        if (completedWord.isNotBlank() && settings.userLearningEnabled) {
            // Add word to context
            contextTracker.addWord(completedWord)
            
            // Record sequence for learning (get last few words including the one just completed)
            val contextWords = contextTracker.getContext()
            if (contextWords.size >= 2) {
                userLearningStore.recordSequence(contextWords)
                // Persist periodically (every 10 sequences or so)
                if (contextWords.size % 10 == 0) {
                    userLearningStore.persist(appContext)
                }
            }
        }
        
        // Check if this is a sentence boundary
        val boundaryChar = event?.unicodeChar?.toChar()
        if (boundaryChar != null && (boundaryChar == '.' || boundaryChar == '!' || boundaryChar == '?' || boundaryChar == '\n')) {
            contextTracker.onSentenceBoundary()
        }
        
        if (result.replaced) {
            NotificationHelper.triggerHapticFeedback(appContext)
        } else {
            pendingAddUserWord = null
        }
        
        // Show next-word predictions if enabled and we have context
        if (settings.nextWordPredictionEnabled && !completedWord.isBlank()) {
            val context = contextTracker.getContext()
            if (context.isNotEmpty()) {
                val predictions = nextWordPredictor.predict(context, settings.maxSuggestions)
                val predictionResults = predictions.mapIndexed { index, pred ->
                    SuggestionResult(
                        candidate = pred,
                        distance = 0,
                        score = (predictions.size - index).toDouble(),
                        source = SuggestionSource.MAIN
                    )
                }
                latestSuggestions.set(predictionResults)
                latestMode.set(SuggestionMode.NEXT_WORD)
                suggestionsListener?.invoke(predictionResults)
                return result
            }
        }
        
        suggestionsListener?.invoke(emptyList())
        return result
    }

    /**
     * Reads the word at cursor immediately without debounce.
     * Use this when entering a text field to show suggestions right away.
     * If dictionary is not ready yet, does nothing - normal typing/cursor flow will handle it.
     */
    fun readInitialContext(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (inputConnection == null || !dictionaryRepository.isReady) return
        
        val word = extractWordAtCursor(inputConnection)
        if (!word.isNullOrBlank()) {
            tracker.setWord(word)
        }
    }

    fun onCursorMoved(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        // #region agent log
        val trackerWordBefore = tracker.currentWord
        debugLog("A", "SuggestionController.onCursorMoved:entry", "onCursorMoved called", mapOf(
            "trackerWordBefore" to trackerWordBefore,
            "trackerWordLengthBefore" to trackerWordBefore.length
        ))
        // #endregion
        ensureDictionaryLoaded()
        cursorRunnable?.let { cursorHandler.removeCallbacks(it) }
        if (inputConnection == null) {
            tracker.reset()
            suggestionsListener?.invoke(emptyList())
            return
        }
        cursorRunnable = Runnable {
            // #region agent log
            val trackerWordBeforeExtract = tracker.currentWord
            debugLog("B", "SuggestionController.onCursorMoved:runnable", "extractWordAtCursor about to be called", mapOf(
                "trackerWordBeforeExtract" to trackerWordBeforeExtract,
                "trackerWordLengthBeforeExtract" to trackerWordBeforeExtract.length
            ))
            // #endregion
            if (!dictionaryRepository.isReady) {
                tracker.reset()
                suggestionsListener?.invoke(emptyList())
                return@Runnable
            }
            val word = extractWordAtCursor(inputConnection)
            // #region agent log
            debugLog("B", "SuggestionController.onCursorMoved:afterExtract", "extractWordAtCursor returned", mapOf(
                "extractedWord" to (word ?: "null"),
                "extractedWordLength" to (word?.length ?: 0),
                "trackerWordBeforeSet" to trackerWordBeforeExtract,
                "trackerWordLengthBeforeSet" to trackerWordBeforeExtract.length
            ))
            // #endregion
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
                // #region agent log
                val trackerWordAfter = tracker.currentWord
                debugLog("B", "SuggestionController.onCursorMoved:afterSet", "tracker.setWord called", mapOf(
                    "trackerWordAfter" to trackerWordAfter,
                    "trackerWordLengthAfter" to trackerWordAfter.length,
                    "extractedWord" to word
                ))
                // #endregion
            } else {
                tracker.reset()
                suggestionsListener?.invoke(emptyList())
            }
        }
        cursorHandler.postDelayed(cursorRunnable!!, cursorDebounceMs)
    }

    fun onContextReset() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        contextTracker.reset()
        pendingAddUserWord = null
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        if (!isEnabled()) return
        tracker.onContextChanged()
    }

    fun addUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.addUserEntryQuick(word)
    }

    fun removeUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.removeUserEntry(word)
        refreshUserDictionary()
    }

    fun markUsed(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.markUsed(word)
    }

    fun currentSuggestions(): List<SuggestionResult> = latestSuggestions.get()

    fun currentSuggestionMode(): SuggestionMode = latestMode.get()

    fun userDictionarySnapshot(): List<UserDictionaryStore.UserEntry> = userDictionaryStore.getSnapshot()

    /**
     * Forces a refresh of user dictionary entries.
     * Should be called when words are added/removed from settings.
     */
    fun refreshUserDictionary() {
        if (!isEnabled()) return
        loadScope.launch {
            try {
                dictionaryRepository.refreshUserEntries()
            } catch (_: CancellationException) {
                // Cancelled due to rapid switches; safe to ignore.
            } catch (e: Exception) {
                Log.e("TitanKeysIME", "Failed to refresh user dictionary", e)
            }
        }
    }

    fun handleBackspaceUndo(keyCode: Int, inputConnection: InputConnection?): Boolean {
        if (!isEnabled()) return false
        val undone = autoReplaceController.handleBackspaceUndo(keyCode, inputConnection)
        if (undone) {
            pendingAddUserWord = autoReplaceController.consumeLastUndoOriginalWord()
        }
        return undone
    }

    fun pendingAddWord(): String? = pendingAddUserWord
    fun clearPendingAddWord() {
        pendingAddUserWord = null
    }

    /**
     * Clears the pending add-word candidate if the cursor is no longer on that word.
     * Keeps the candidate only while the cursor remains on the originating token.
     */
    fun clearPendingAddWordIfCursorOutside(inputConnection: InputConnection?) {
        val pending = pendingAddUserWord ?: return
        val currentWord = extractWordAtCursor(inputConnection)
        if (currentWord == null || !currentWord.equals(pending, ignoreCase = true)) {
            pendingAddUserWord = null
        }
    }

    private fun extractWordAtCursor(inputConnection: InputConnection?): String? {
        if (inputConnection == null) return null
        return try {
            val before = inputConnection.getTextBeforeCursor(12, 0)?.toString() ?: ""
            val after = inputConnection.getTextAfterCursor(12, 0)?.toString() ?: ""
            // #region agent log
            debugLog("B", "SuggestionController.extractWordAtCursor:before", "getTextBeforeCursor/getTextAfterCursor called", mapOf(
                "before" to before,
                "beforeLength" to before.length,
                "after" to after,
                "afterLength" to after.length
            ))
            // #endregion
            val boundary = " \t\n\r" + com.titankeys.keyboard.core.Punctuation.BOUNDARY
            var start = before.length
            while (start > 0 && !boundary.contains(before[start - 1])) {
                start--
            }
            var end = 0
            while (end < after.length && !boundary.contains(after[end])) {
                end++
            }
            val word = before.substring(start) + after.substring(0, end)
            // #region agent log
            debugLog("B", "SuggestionController.extractWordAtCursor:after", "word extracted", mapOf(
                "extractedWord" to (if (word.isBlank()) "null" else word),
                "extractedWordLength" to word.length,
                "beforeSubstring" to before.substring(start),
                "afterSubstring" to after.substring(0, end)
            ))
            // #endregion
            if (word.isBlank()) null else word
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Preloads the dictionary in background.
     * Should be called during initialization to have dictionary ready when user focuses a field.
     */
    fun preloadDictionary() {
        if (!dictionaryRepository.isReady && !dictionaryRepository.isLoadStarted) {
            loadScope.launch {
                dictionaryRepository.loadIfNeeded()
                // Load n-gram data after dictionary is ready
                if (dictionaryRepository.isReady) {
                    ngramLanguageModel.loadNgrams(
                        dictionaryRepository.getBigrams(),
                        dictionaryRepository.getTrigrams()
                    )
                }
            }
        }
    }

    private fun ensureDictionaryLoaded() {
        if (!dictionaryRepository.isReady) {
            dictionaryRepository.ensureLoadScheduled {
                loadScope.launch {
                    dictionaryRepository.loadIfNeeded()
                    // Load n-gram data after dictionary is ready
                    if (dictionaryRepository.isReady) {
                        ngramLanguageModel.loadNgrams(
                            dictionaryRepository.getBigrams(),
                            dictionaryRepository.getTrigrams()
                        )
                    }
                }
            }
        } else {
            // Dictionary is ready, ensure n-grams are loaded
            val repoBigrams = dictionaryRepository.getBigrams()
            if (repoBigrams.isNotEmpty()) {
                ngramLanguageModel.loadNgrams(
                    repoBigrams,
                    dictionaryRepository.getTrigrams()
                )
            }
        }
    }
}
