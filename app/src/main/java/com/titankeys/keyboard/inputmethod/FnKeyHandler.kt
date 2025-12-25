package com.titankeys.keyboard.inputmethod

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.titankeys.keyboard.SettingsManager

/**
 * Handler for Fn and SYM programmable key functionality.
 *
 * Reads Unihertz system settings for key configuration:
 * - fn_programmable_key_enable: 0/1 (disabled/enabled)
 * - fn_programmable_key_function: Function mode (0=disabled, 1=app, 2=action, etc.)
 * - sym_programmable_key_enable: 0/1 (disabled/enabled)
 * - sym_programmable_key_function: Function mode
 *
 * Function modes:
 * - 0: Disabled (use default key behavior)
 * - 1: Launch app (uses Fn+key shortcuts)
 * - 2: System action (flashlight, screenshot, etc.)
 * - 3: Quick settings panel
 * - 4: Notifications panel
 * - 5: Recent apps
 * - 6: Voice assistant
 */
class FnKeyHandler(private val context: Context) {
  companion object {
    private const val TAG = "FnKeyHandler"

    // System settings keys (Unihertz Titan specific)
    private const val FN_PROGRAMMABLE_KEY_ENABLE = "fn_programmable_key_enable"
    private const val FN_PROGRAMMABLE_KEY_FUNCTION = "fn_programmable_key_function"
    private const val SYM_PROGRAMMABLE_KEY_ENABLE = "sym_programmable_key_enable"
    private const val SYM_PROGRAMMABLE_KEY_FUNCTION = "sym_programmable_key_function"

    // Function modes
    const val FUNCTION_DISABLED = 0
    const val FUNCTION_APP_LAUNCH = 1
    const val FUNCTION_SYSTEM_ACTION = 2
    const val FUNCTION_QUICK_SETTINGS = 3
    const val FUNCTION_NOTIFICATIONS = 4
    const val FUNCTION_RECENT_APPS = 5
    const val FUNCTION_VOICE_ASSISTANT = 6
    const val FUNCTION_FLASHLIGHT = 7
    const val FUNCTION_SCREENSHOT = 8

    // Key codes
    const val KEYCODE_FN = 119 // KeyEvent.KEYCODE_FUNCTION on some devices
    const val KEYCODE_SYM = 63

    // State for Fn key modifier
    private var fnPressed = false
    private var fnPressTime = 0L
    private const val FN_COMBO_TIMEOUT_MS = 2000L
  }

  // Cached settings
  private var fnEnabled: Boolean = false
  private var fnFunction: Int = FUNCTION_DISABLED
  private var symEnabled: Boolean = false
  private var symFunction: Int = FUNCTION_DISABLED
  private var settingsLoaded = false

  // Callback for showing toasts
  var showToast: ((String) -> Unit)? = null

  // Callback for launching shortcuts
  var launcherShortcutController: LauncherShortcutController? = null

  /**
   * Load programmable key settings from system settings.
   * Call this on startup and when settings may have changed.
   */
  fun loadSettings() {
    try {
      val resolver = context.contentResolver

      // Read Fn key settings
      fnEnabled = Settings.System.getInt(resolver, FN_PROGRAMMABLE_KEY_ENABLE, 0) == 1
      fnFunction = Settings.System.getInt(resolver, FN_PROGRAMMABLE_KEY_FUNCTION, FUNCTION_DISABLED)

      // Read SYM key settings
      symEnabled = Settings.System.getInt(resolver, SYM_PROGRAMMABLE_KEY_ENABLE, 0) == 1
      symFunction = Settings.System.getInt(resolver, SYM_PROGRAMMABLE_KEY_FUNCTION, FUNCTION_DISABLED)

      settingsLoaded = true
      Log.d(TAG, "Settings loaded: Fn(enabled=$fnEnabled, function=$fnFunction), SYM(enabled=$symEnabled, function=$symFunction)")
    } catch (e: Exception) {
      Log.w(TAG, "Could not read programmable key settings (may not be available on this device)", e)
      // Default to disabled if settings not available
      fnEnabled = false
      fnFunction = FUNCTION_DISABLED
      symEnabled = false
      symFunction = FUNCTION_DISABLED
      settingsLoaded = true
    }
  }

  /**
   * Check if Fn key is currently pressed (for combo detection).
   */
  fun isFnPressed(): Boolean = fnPressed

  /**
   * Handle Fn key down event.
   * @return true if event was consumed, false to pass through
   */
  fun onFnKeyDown(): Boolean {
    if (!settingsLoaded) loadSettings()

    if (!fnEnabled) {
      Log.d(TAG, "Fn key disabled in settings")
      return false
    }

    fnPressed = true
    fnPressTime = System.currentTimeMillis()
    Log.d(TAG, "Fn key pressed, waiting for combo key")

    // Don't consume yet - wait for combo or release
    return true
  }

  /**
   * Handle Fn key up event.
   * If no combo was executed, perform single-press action.
   * @return true if event was consumed
   */
  fun onFnKeyUp(): Boolean {
    if (!fnPressed) return false

    fnPressed = false
    val pressDuration = System.currentTimeMillis() - fnPressTime

    // If held briefly without combo, do nothing (Fn is a modifier)
    if (pressDuration < 500) {
      Log.d(TAG, "Fn released quickly, no action")
      return true
    }

    // Long press could trigger a specific action if configured
    Log.d(TAG, "Fn long press, duration=$pressDuration ms")
    return true
  }

  /**
   * Handle a key press while Fn is held.
   * @param keyCode The key pressed with Fn
   * @return true if a Fn+key combo was executed
   */
  fun handleFnCombo(keyCode: Int): Boolean {
    if (!fnPressed) return false

    // Check timeout
    if (System.currentTimeMillis() - fnPressTime > FN_COMBO_TIMEOUT_MS) {
      Log.d(TAG, "Fn combo timeout")
      fnPressed = false
      return false
    }

    Log.d(TAG, "Fn combo: Fn + keyCode=$keyCode")

    // Based on function mode, handle differently
    when (fnFunction) {
      FUNCTION_APP_LAUNCH -> {
        // Use launcher shortcuts (Fn+key = SYM+key behavior)
        val controller = launcherShortcutController
        if (controller != null) {
          return controller.handleLauncherShortcut(keyCode)
        }
        return false
      }
      FUNCTION_SYSTEM_ACTION -> {
        return handleSystemAction(keyCode)
      }
      else -> {
        Log.d(TAG, "Fn function mode $fnFunction not implemented for combos")
        return false
      }
    }
  }

  /**
   * Handle SYM key with programmable function.
   * This is separate from the existing power shortcuts system.
   * @return true if handled by programmable function, false to use default behavior
   */
  fun handleSymProgrammable(): Boolean {
    if (!settingsLoaded) loadSettings()

    if (!symEnabled || symFunction == FUNCTION_DISABLED) {
      return false // Let default SYM behavior handle it
    }

    Log.d(TAG, "SYM programmable function: $symFunction")
    return executeFunction(symFunction)
  }

  /**
   * Execute a programmable function.
   */
  private fun executeFunction(function: Int): Boolean {
    return when (function) {
      FUNCTION_QUICK_SETTINGS -> {
        expandQuickSettings()
        true
      }
      FUNCTION_NOTIFICATIONS -> {
        expandNotifications()
        true
      }
      FUNCTION_RECENT_APPS -> {
        // Cannot directly trigger recent apps from IME
        showToast?.invoke("Recent apps not available from keyboard")
        false
      }
      FUNCTION_VOICE_ASSISTANT -> {
        launchVoiceAssistant()
        true
      }
      FUNCTION_FLASHLIGHT -> {
        toggleFlashlight()
        true
      }
      FUNCTION_SCREENSHOT -> {
        // Cannot directly trigger screenshot from IME
        showToast?.invoke("Screenshot not available from keyboard")
        false
      }
      else -> {
        Log.d(TAG, "Unknown function: $function")
        false
      }
    }
  }

  /**
   * Handle Fn+key for system actions (flashlight, etc.)
   */
  private fun handleSystemAction(keyCode: Int): Boolean {
    // Map common Fn+key combinations
    return when (keyCode) {
      KeyEvent.KEYCODE_F -> { // Fn+F = Flashlight
        toggleFlashlight()
        true
      }
      KeyEvent.KEYCODE_V -> { // Fn+V = Voice assistant
        launchVoiceAssistant()
        true
      }
      KeyEvent.KEYCODE_Q -> { // Fn+Q = Quick settings
        expandQuickSettings()
        true
      }
      KeyEvent.KEYCODE_N -> { // Fn+N = Notifications
        expandNotifications()
        true
      }
      else -> false
    }
  }

  /**
   * Toggle the device flashlight.
   */
  private fun toggleFlashlight(): Boolean {
    return try {
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
      val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false

      // Toggle flashlight (this is a simplified version - real implementation
      // would need to track current state)
      // Note: This requires FLASHLIGHT permission
      cameraManager.setTorchMode(cameraId, true)
      showToast?.invoke("Flashlight ON")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to toggle flashlight", e)
      showToast?.invoke("Flashlight not available")
      false
    }
  }

  /**
   * Launch the device voice assistant.
   */
  private fun launchVoiceAssistant(): Boolean {
    return try {
      val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch voice assistant", e)
      showToast?.invoke("Voice assistant not available")
      false
    }
  }

  /**
   * Expand the quick settings panel.
   */
  @Suppress("DEPRECATION")
  private fun expandQuickSettings(): Boolean {
    return try {
      val statusBarService = context.getSystemService("statusbar")
      val statusBarManager = Class.forName("android.app.StatusBarManager")
      val expandMethod = statusBarManager.getMethod("expandSettingsPanel")
      expandMethod.invoke(statusBarService)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to expand quick settings", e)
      false
    }
  }

  /**
   * Expand the notifications panel.
   */
  @Suppress("DEPRECATION")
  private fun expandNotifications(): Boolean {
    return try {
      val statusBarService = context.getSystemService("statusbar")
      val statusBarManager = Class.forName("android.app.StatusBarManager")
      val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
      expandMethod.invoke(statusBarService)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to expand notifications", e)
      false
    }
  }

  /**
   * Get human-readable name for a function mode.
   */
  fun getFunctionName(function: Int): String {
    return when (function) {
      FUNCTION_DISABLED -> "Disabled"
      FUNCTION_APP_LAUNCH -> "App Launch"
      FUNCTION_SYSTEM_ACTION -> "System Action"
      FUNCTION_QUICK_SETTINGS -> "Quick Settings"
      FUNCTION_NOTIFICATIONS -> "Notifications"
      FUNCTION_RECENT_APPS -> "Recent Apps"
      FUNCTION_VOICE_ASSISTANT -> "Voice Assistant"
      FUNCTION_FLASHLIGHT -> "Flashlight"
      FUNCTION_SCREENSHOT -> "Screenshot"
      else -> "Unknown ($function)"
    }
  }

  /**
   * Get current Fn key function for display.
   */
  fun getFnFunctionDescription(): String {
    if (!settingsLoaded) loadSettings()
    return if (fnEnabled) getFunctionName(fnFunction) else "Disabled"
  }

  /**
   * Get current SYM key function for display.
   */
  fun getSymFunctionDescription(): String {
    if (!settingsLoaded) loadSettings()
    return if (symEnabled) getFunctionName(symFunction) else "Default (Power Shortcuts)"
  }
}
