package com.titankeys.keyboard.core.suggestions

import android.content.Context
import android.util.Log
import com.titankeys.keyboard.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Handles downloading grammar AI models from remote server
 */
class GrammarModelDownloader(private val context: Context) {
  companion object {
    private const val TAG = "GrammarModelDownloader"

    // Base URL for model downloads - GitHub Releases
    private const val BASE_URL = "https://github.com/Danz17/TitanKeys/releases/download/models-v1.0"

    // Model manifest with checksums for verification
    private val MODEL_MANIFEST = mapOf(
      "en" to ModelInfo(
        url = "$BASE_URL/grammar_en_encoder.tflite",
        checksum = "a46ad0f6fc1abbffecfe8f4723347cfbb4461d29144c2a392254e14df9c281fb",
        sizeMB = 35
      ),
      "de" to ModelInfo(
        url = "$BASE_URL/grammar_de.tflite",
        checksum = "",
        sizeMB = 60
      ),
      "es" to ModelInfo(
        url = "$BASE_URL/grammar_es.tflite",
        checksum = "",
        sizeMB = 60
      ),
      "fr" to ModelInfo(
        url = "$BASE_URL/grammar_fr.tflite",
        checksum = "",
        sizeMB = 60
      ),
      "it" to ModelInfo(
        url = "$BASE_URL/grammar_it.tflite",
        checksum = "",
        sizeMB = 60
      ),
      "pt" to ModelInfo(
        url = "$BASE_URL/grammar_pt.tflite",
        checksum = "",
        sizeMB = 60
      ),
      "pl" to ModelInfo(
        url = "$BASE_URL/grammar_pl.tflite",
        checksum = "",
        sizeMB = 60
      ),
      "ru" to ModelInfo(
        url = "$BASE_URL/grammar_ru.tflite",
        checksum = "",
        sizeMB = 60
      ),
      "lt" to ModelInfo(
        url = "$BASE_URL/grammar_lt.tflite",
        checksum = "",
        sizeMB = 60
      )
    )

    private const val BUFFER_SIZE = 8192
    private const val CONNECTION_TIMEOUT_MS = 30000
    private const val READ_TIMEOUT_MS = 60000
  }

  data class ModelInfo(
    val url: String,
    val checksum: String,
    val sizeMB: Int
  )

  private val activeDownloads = mutableMapOf<String, Job>()

  /**
   * Download a grammar model for the specified language
   * @param languageCode The language code (e.g., "en", "de")
   * @param onProgress Progress callback (0.0 to 1.0)
   * @return true if download succeeded, false otherwise
   */
  suspend fun downloadModel(
    languageCode: String,
    onProgress: (Float) -> Unit
  ): Boolean = withContext(Dispatchers.IO) {
    val modelInfo = MODEL_MANIFEST[languageCode]
    if (modelInfo == null) {
      Log.e(TAG, "No model info found for language: $languageCode")
      return@withContext false
    }

    val targetFile = SettingsManager.getGrammarModelFile(context, languageCode)
    val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

    try {
      // Create parent directories if needed
      targetFile.parentFile?.mkdirs()

      // Check if model is available (has a checksum)
      if (modelInfo.checksum.isEmpty()) {
        Log.w(TAG, "Model not yet available for language: $languageCode")
        return@withContext false
      }

      // Open connection
      val url = URL(modelInfo.url)
      val connection = url.openConnection() as HttpURLConnection
      connection.connectTimeout = CONNECTION_TIMEOUT_MS
      connection.readTimeout = READ_TIMEOUT_MS
      connection.requestMethod = "GET"

      val responseCode = connection.responseCode
      if (responseCode != HttpURLConnection.HTTP_OK) {
        Log.e(TAG, "HTTP error $responseCode for $languageCode")
        return@withContext false
      }

      val contentLength = connection.contentLength
      var downloadedBytes = 0L

      // Download to temp file
      BufferedInputStream(connection.inputStream).use { input ->
        FileOutputStream(tempFile).use { output ->
          val buffer = ByteArray(BUFFER_SIZE)
          var bytesRead: Int

          while (input.read(buffer).also { bytesRead = it } != -1) {
            if (!isActive) {
              // Download was cancelled
              tempFile.delete()
              return@withContext false
            }

            output.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead

            if (contentLength > 0) {
              onProgress(downloadedBytes.toFloat() / contentLength)
            }
          }
        }
      }

      connection.disconnect()

      // Verify checksum if provided
      if (modelInfo.checksum.isNotEmpty()) {
        val actualChecksum = calculateSHA256(tempFile)
        if (actualChecksum != modelInfo.checksum) {
          Log.e(TAG, "Checksum mismatch for $languageCode")
          tempFile.delete()
          return@withContext false
        }
      }

      // Move temp file to final location
      if (targetFile.exists()) {
        targetFile.delete()
      }
      if (!tempFile.renameTo(targetFile)) {
        // Fallback: copy and delete
        tempFile.copyTo(targetFile, overwrite = true)
        tempFile.delete()
      }

      Log.i(TAG, "Successfully downloaded grammar model for $languageCode")
      onProgress(1.0f)
      true

    } catch (e: Exception) {
      Log.e(TAG, "Error downloading model for $languageCode", e)
      tempFile.delete()
      false
    }
  }

  /**
   * Create a placeholder model file for testing
   * This simulates a download when models aren't yet hosted
   */
  private suspend fun createPlaceholderModel(
    targetFile: File,
    onProgress: (Float) -> Unit
  ): Boolean = withContext(Dispatchers.IO) {
    try {
      targetFile.parentFile?.mkdirs()

      // Simulate download progress
      for (i in 0..10) {
        if (!isActive) return@withContext false
        kotlinx.coroutines.delay(200)
        onProgress(i / 10f)
      }

      // Create a small placeholder file
      // This indicates the model was "downloaded" but won't work for actual inference
      targetFile.writeText("PLACEHOLDER_MODEL_v1\nThis is a placeholder. Real model required for AI grammar checking.")

      onProgress(1.0f)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Error creating placeholder model", e)
      false
    }
  }

  /**
   * Delete a downloaded model
   */
  fun deleteModel(languageCode: String): Boolean {
    val modelFile = SettingsManager.getGrammarModelFile(context, languageCode)
    return if (modelFile.exists()) {
      modelFile.delete()
    } else {
      true
    }
  }

  /**
   * Cancel all active downloads
   */
  fun cancelAllDownloads() {
    activeDownloads.values.forEach { it.cancel() }
    activeDownloads.clear()
  }

  /**
   * Check if a model file exists and is valid
   */
  fun isModelValid(languageCode: String): Boolean {
    val modelFile = SettingsManager.getGrammarModelFile(context, languageCode)
    if (!modelFile.exists()) return false

    // Check if it's a placeholder or real model
    val content = modelFile.readText().take(20)
    if (content.startsWith("PLACEHOLDER_MODEL")) {
      return false // Placeholder, not a real model
    }

    // For real models, check minimum file size (should be > 1MB)
    return modelFile.length() > 1_000_000
  }

  /**
   * Get the size of a downloaded model in bytes
   */
  fun getModelSize(languageCode: String): Long {
    val modelFile = SettingsManager.getGrammarModelFile(context, languageCode)
    return if (modelFile.exists()) modelFile.length() else 0
  }

  /**
   * Calculate SHA-256 checksum of a file
   */
  private fun calculateSHA256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(BUFFER_SIZE)
      var bytesRead: Int
      while (input.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}
