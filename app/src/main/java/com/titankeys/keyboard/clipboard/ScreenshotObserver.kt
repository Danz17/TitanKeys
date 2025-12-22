package com.titankeys.keyboard.clipboard

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Observes the MediaStore for new images (screenshots and copied images) and adds them to clipboard history.
 * This is needed because many Android devices don't put screenshots/copied images in the clipboard.
 * Also captures images copied from apps like WhatsApp, Gallery, browsers, etc.
 */
class ScreenshotObserver(
    private val context: Context,
    private val onScreenshotDetected: (String) -> Unit
) {
    companion object {
        private const val TAG = "ScreenshotObserver"
        private const val IMAGE_DEBOUNCE_MS = 2000L // Ignore duplicates within 2 seconds
        // Common screenshot folder patterns
        private val SCREENSHOT_PATTERNS = listOf(
            "screenshot", "screen_shot", "screen-shot",
            "screencap", "screen_cap", "screen-cap",
            "scrnshot", "scrshot", "screenshots"
        )
        // Patterns to exclude (camera photos, downloads from unknown sources)
        private val EXCLUDE_PATTERNS = listOf(
            "dcim/camera", "/camera/", "whatsapp/media/whatsapp images/sent",
            "telegram/telegram images/sent"
        )
    }

    private var contentObserver: ContentObserver? = null
    private var lastProcessedUri: Uri? = null
    private var lastProcessedTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let { handleMediaChange(it) }
            }
        }

        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.registerContentObserver(
                contentUri,
                true,
                contentObserver!!
            )
            Log.d(TAG, "Started observing MediaStore for screenshots")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register content observer", e)
        }
    }

    fun stop() {
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
            Log.d(TAG, "Stopped observing MediaStore")
        }
    }

    private fun handleMediaChange(uri: Uri) {
        // Debounce to avoid processing the same image multiple times
        val now = System.currentTimeMillis()
        if (uri == lastProcessedUri && now - lastProcessedTime < IMAGE_DEBOUNCE_MS) {
            return
        }

        // Query to check if this is a newly added image
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.RELATIVE_PATH
            )

            // Get the most recent image
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
            val selectionArgs = arrayOf(((now - 5000) / 1000).toString()) // Last 5 seconds

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex) ?: ""
                        val data = if (dataIndex >= 0) cursor.getString(dataIndex) else null
                        val relativePath = if (pathIndex >= 0) cursor.getString(pathIndex) else null

                        val pathLower = (data ?: relativePath ?: "").lowercase()
                        val nameLower = name.lowercase()

                        // Check if this should be excluded (camera photos, sent messages)
                        val shouldExclude = EXCLUDE_PATTERNS.any { pattern ->
                            pathLower.contains(pattern) || nameLower.contains(pattern)
                        }

                        if (shouldExclude) {
                            Log.d(TAG, "Excluding image (camera/sent): $name at $pathLower")
                            return
                        }

                        // Check if it's a screenshot (high priority - always capture)
                        val isScreenshot = SCREENSHOT_PATTERNS.any { pattern ->
                            nameLower.contains(pattern) || pathLower.contains(pattern)
                        }

                        // Also capture images from common "received" or "download" locations
                        // These are likely copied/shared images
                        val isReceivedOrDownload = pathLower.contains("download") ||
                            pathLower.contains("received") ||
                            pathLower.contains("whatsapp") ||
                            pathLower.contains("telegram") ||
                            pathLower.contains("pictures") ||
                            pathLower.contains("saved") ||
                            pathLower.contains("clipboard")

                        if (isScreenshot || isReceivedOrDownload) {
                            Log.d(TAG, "Image detected (screenshot=$isScreenshot, received=$isReceivedOrDownload): $name at $data")

                            // Save to clipboard history
                            val savedPath = saveScreenshotToHistory(uri, data)
                            if (savedPath != null) {
                                lastProcessedUri = uri
                                lastProcessedTime = now
                                onScreenshotDetected(savedPath)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for image", e)
        }
    }

    private fun saveScreenshotToHistory(contentUri: Uri, filePath: String?): String? {
        return try {
            // Try to load bitmap from file path first (more reliable)
            val bitmap = if (filePath != null && File(filePath).exists()) {
                BitmapFactory.decodeFile(filePath)
            } else {
                // Fallback to content URI
                context.contentResolver.openInputStream(contentUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }

            if (bitmap == null) {
                Log.w(TAG, "Failed to decode image bitmap")
                return null
            }

            // Save to app's private storage
            val imagesDir = File(context.filesDir, "clipboard_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            val filename = "clip_img_${System.currentTimeMillis()}_${UUID.randomUUID()}.png"
            val imageFile = File(imagesDir, filename)

            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            Log.d(TAG, "Saved image to clipboard history: ${imageFile.absolutePath}")
            imageFile.absolutePath
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - no permission to read image", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image to history", e)
            null
        }
    }
}
