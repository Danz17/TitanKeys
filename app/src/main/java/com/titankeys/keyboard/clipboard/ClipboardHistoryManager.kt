package com.titankeys.keyboard.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import com.titankeys.keyboard.SettingsManager
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages clipboard history tracking and provides popup display.
 * Listens to system clipboard changes and stores them in a database.
 * Supports both text and image (screenshot) clipboard content.
 */
class ClipboardHistoryManager(
    private val context: Context
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var clipboardDao: ClipboardDao? = null
    private var isEnabled: Boolean = true
    private var screenshotObserver: ScreenshotObserver? = null

    fun onCreate() {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        clipboardDao = ClipboardDao.getInstance(context)

        // Check if history is enabled
        isEnabled = getClipboardHistoryEnabled()

        if (isEnabled) {
            fetchPrimaryClip()

            // Start observing MediaStore for screenshots (they don't always go through clipboard)
            screenshotObserver = ScreenshotObserver(context) { savedPath ->
                val retentionMinutes = getClipboardRetentionTime()
                clipboardDao?.addImageClip(System.currentTimeMillis(), false, savedPath, retentionMinutes)
                Log.d(TAG, "Screenshot added to clipboard history from MediaStore observer")
            }
            screenshotObserver?.start()
        }
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
        screenshotObserver?.stop()
        screenshotObserver = null
    }

    override fun onPrimaryClipChanged() {
        if (!isEnabled) return
        fetchPrimaryClip()
    }

    private fun fetchPrimaryClip() {
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0) return

        val description = clipData.description
        val timeStamp = System.currentTimeMillis()
        val retentionMinutes = getClipboardRetentionTime()

        // Log MIME types for debugging
        val mimeTypes = (0 until (description?.mimeTypeCount ?: 0)).map {
            description?.getMimeType(it)
        }
        Log.d(TAG, "Clipboard changed. MIME types: $mimeTypes")

        val clipItem = clipData.getItemAt(0) ?: return

        // Try to get URI first (for images/screenshots)
        val uri = clipItem.uri
        if (uri != null) {
            Log.d(TAG, "Clipboard has URI: $uri")

            // Multi-method image detection for robust handling of all image sources
            // 1. Check ClipDescription MIME type (fastest check)
            val isImageMime = description?.hasMimeType("image/*") == true

            // 2. Query ContentResolver for actual content type
            val contentType = try {
                context.contentResolver.getType(uri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get content type for URI", e)
                null
            }
            val isImageContent = contentType?.startsWith("image/") == true
            Log.d(TAG, "Content type from resolver: $contentType, isImageMime=$isImageMime, isImageContent=$isImageContent")

            // 3. Fallback: Try to detect if URI points to a decodable bitmap
            // This catches images from apps that don't properly set MIME types
            val canDecodeAsBitmap = if (!isImageMime && !isImageContent) {
                tryDetectBitmapFromUri(uri)
            } else false

            val isImage = isImageMime || isImageContent || canDecodeAsBitmap

            if (isImage) {
                Log.d(TAG, "Attempting to save image from URI (detected via: mime=$isImageMime, content=$isImageContent, decode=$canDecodeAsBitmap)")
                if (saveImageFromUri(uri, timeStamp, retentionMinutes)) {
                    return // Successfully saved image
                }
            }
        }

        // Check for text content
        if (description?.hasMimeType("text/*") == true) {
            val content = clipItem.coerceToText(context)
            if (!TextUtils.isEmpty(content)) {
                Log.d(TAG, "Saving text clip: ${content.toString().take(50)}...")
                clipboardDao?.addClip(timeStamp, false, content.toString(), retentionMinutes)
                return
            }
        }

        // Fallback: try to coerce to text anyway
        val content = clipItem.coerceToText(context)
        if (!TextUtils.isEmpty(content)) {
            Log.d(TAG, "Saving coerced text clip: ${content.toString().take(50)}...")
            clipboardDao?.addClip(timeStamp, false, content.toString(), retentionMinutes)
        }
    }

    /**
     * Saves an image from a URI to app's private storage.
     * Returns true if successful, false otherwise.
     */
    private fun saveImageFromUri(uri: Uri, timeStamp: Long, retentionMinutes: Long): Boolean {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }

            if (bitmap == null) {
                Log.w(TAG, "Failed to decode bitmap from URI: $uri")
                return false
            }

            // Create clipboard images directory
            val imagesDir = File(context.filesDir, "clipboard_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            // Save with unique filename
            val filename = "clip_${timeStamp}_${UUID.randomUUID()}.png"
            val imageFile = File(imagesDir, filename)

            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            clipboardDao?.addImageClip(timeStamp, false, imageFile.absolutePath, retentionMinutes)
            Log.d(TAG, "Saved clipboard image: ${imageFile.absolutePath}")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - no permission to read image URI: $uri", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save clipboard image from URI: $uri", e)
            false
        }
    }

    /**
     * Tries to detect if a URI points to a decodable bitmap by reading just the header.
     * This is a lightweight check that doesn't load the full image into memory.
     * Used as fallback when MIME type detection fails.
     */
    private fun tryDetectBitmapFromUri(uri: Uri): Boolean {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true // Only decode bounds, not actual bitmap
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val isValidBitmap = options.outWidth > 0 && options.outHeight > 0
            if (isValidBitmap) {
                Log.d(TAG, "Detected bitmap from URI: ${options.outWidth}x${options.outHeight}")
            }
            isValidBitmap
        } catch (e: SecurityException) {
            Log.d(TAG, "No permission to check bitmap from URI: $uri")
            false
        } catch (e: Exception) {
            Log.d(TAG, "Failed to detect bitmap from URI: $uri - ${e.message}")
            false
        }
    }

    fun toggleClipPinned(id: Long) {
        clipboardDao?.togglePinned(id)
    }

    fun clearHistory() {
        clipboardDao?.clearNonPinned()
        try {
            clipboardManager.clearPrimaryClip()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear system clipboard", e)
        }
    }

    fun canRemove(index: Int) = clipboardDao?.isPinned(index) == false

    fun removeEntry(index: Int, force: Boolean = false) {
        val entry = getHistoryEntry(index) ?: return

        // For UX: allow deleting pinned entries when explicitly requested (force=true)
        if (entry.isPinned && !force) return

        if (entry.isPinned && force) {
            // Unpin first so DAO allows removal, then delete using the updated position
            toggleClipPinned(entry.id)
            val updatedIndex = (0 until getHistorySize()).firstOrNull { idx ->
                getHistoryEntry(idx)?.id == entry.id
            }
            updatedIndex?.let { clipboardDao?.deleteClipAt(it) }
        } else {
            clipboardDao?.deleteClipAt(index)
        }
    }

    fun sortHistoryEntries() {
        clipboardDao?.sort()
    }

    fun prepareClipboardHistory() {
        // Clear old clips before showing history
        val retentionMinutes = getClipboardRetentionTime()
        clipboardDao?.clearOldClips(true, retentionMinutes)
    }

    fun getHistorySize() = clipboardDao?.count() ?: 0

    fun getHistoryEntry(position: Int) = clipboardDao?.getAt(position)

    fun getHistoryEntryContent(id: Long) = clipboardDao?.get(id)

    fun setHistoryChangeListener(listener: ClipboardDao.Listener?) {
        clipboardDao?.listener = listener
    }

    /**
     * Gets the current clipboard content as text.
     * Returns null if clipboard is empty or doesn't contain text.
     */
    fun getCurrentClipboardText(): String? {
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") == false) {
            return null
        }

        val clipItem = clipData.getItemAt(0) ?: return null
        val content = clipItem.coerceToText(context)
        return if (TextUtils.isEmpty(content)) null else content.toString()
    }

    /**
     * Checks if clipboard has text content available.
     */
    fun hasClipboardContent(): Boolean {
        return getCurrentClipboardText() != null
    }

    /**
     * Paste the given text into the input connection.
     */
    fun pasteText(text: String, inputConnection: android.view.inputmethod.InputConnection?) {
        inputConnection?.commitText(text, 1)
    }

    /**
     * Paste an image by setting it to the system clipboard.
     * The target app will need to handle the paste from clipboard.
     */
    fun pasteImage(imagePath: String) {
        try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.w(TAG, "Image file not found: $imagePath")
                return
            }

            // Create a content URI for the image using FileProvider
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            // Set the image to system clipboard
            val clipData = android.content.ClipData.newUri(
                context.contentResolver,
                "Clipboard Image",
                uri
            )
            clipboardManager.setPrimaryClip(clipData)
            Log.d(TAG, "Set image to clipboard: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to paste image", e)
        }
    }

    /**
     * Quick paste: Pastes the current clipboard content directly.
     * Returns true if paste was successful, false if clipboard is empty.
     */
    fun quickPaste(inputConnection: android.view.inputmethod.InputConnection?): Boolean {
        val text = getCurrentClipboardText() ?: return false
        pasteText(text, inputConnection)
        return true
    }

    /**
     * Shows the clipboard history popup above the keyboard.
     * Returns the popup view that was created.
     */
    fun showClipboardHistoryPopup(
        inputConnection: android.view.inputmethod.InputConnection?,
        onDismiss: () -> Unit
    ): ClipboardHistoryPopupView? {
        if (!isEnabled) return null

        prepareClipboardHistory()

        return ClipboardHistoryPopupView(context).apply {
            setOnItemClickListener { entry ->
                if (entry.isImage && entry.imagePath != null) {
                    pasteImage(entry.imagePath)
                } else {
                    pasteText(entry.text ?: "", inputConnection)
                }
                dismiss()
                onDismiss()
            }
            setOnPinClickListener { entry ->
                toggleClipPinned(entry.id)
            }
            setOnDeleteClickListener { entry ->
                val index = getHistoryEntry(0)?.let {
                    (0 until getHistorySize()).find { idx ->
                        getHistoryEntry(idx)?.id == entry.id
                    }
                }
                index?.let { removeEntry(it, force = true) }
            }
            setOnClearAllClickListener {
                clearHistory()
            }
            show()
        }
    }

    private fun getClipboardHistoryEnabled(): Boolean {
        return SettingsManager.getClipboardHistoryEnabled(context)
    }

    private fun getClipboardRetentionTime(): Long {
        return SettingsManager.getClipboardRetentionTime(context)
    }

    companion object {
        private const val TAG = "ClipboardHistoryManager"
    }
}
