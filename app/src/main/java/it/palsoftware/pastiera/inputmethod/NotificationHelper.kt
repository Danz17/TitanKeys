package it.palsoftware.pastiera.inputmethod

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Helper for managing app notifications.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "pastiera_nav_mode_channel"
    private const val CHANNEL_NAME = "Pastiera Nav Mode"
    private const val NOTIFICATION_ID = 1
    
    /**
     * Checks whether notification permission is granted.
     * On Android 13+ (API 33+) POST_NOTIFICATIONS is required.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires POST_NOTIFICATIONS permission
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 and below do not require explicit notification permissions
            true
        }
    }
    
    /**
     * Creates the notification channel (required on Android 8.0+).
     * Uses IMPORTANCE_LOW for a silent notification without sound or vibration.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Silent notification (no sound/vibration)
            ).apply {
                description = "Notifications for Pastiera nav mode"
                setShowBadge(false)
                enableLights(false) // Disable LED light for silent notification
                enableVibration(false) // No vibration
                setSound(null, null) // No sound
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Creates a bitmap icon with the letter "N" for the nav mode notification.
     * @param size Icon size in pixels
     * @param backgroundColor Background color (default transparent)
     * @param textColor Text color (default white)
     */
    private fun createNavModeIcon(
        size: Int,
        backgroundColor: Int = Color.TRANSPARENT,
        textColor: Int = Color.WHITE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background
        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor)
        } else {
            canvas.drawColor(Color.TRANSPARENT)
        }
        
        // Draw the "N" letter
        val paint = Paint().apply {
            color = textColor
            textSize = size * 0.7f // 70% of size to keep margins
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // Compute vertical position to center the text
        val textY = (canvas.height / 2) - ((paint.descent() + paint.ascent()) / 2)
        
        // Draw "N"
        canvas.drawText("N", canvas.width / 2f, textY, paint)
        
        return bitmap
    }
    
    /**
     * Shows a notification when nav mode is activated.
     * Checks permissions before showing it.
     */
    fun showNavModeActivatedNotification(context: Context) {
        // Check permission first
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "Notification permission not granted")
            return
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create the channel if needed (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context)
        }
        
        // Create custom "N" icon for the small icon (status bar)
        // Standard size for small icon: 24dp converted to pixels
        val smallIconSize = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val smallIconBitmap = createNavModeIcon(smallIconSize, Color.TRANSPARENT, Color.WHITE)
        val smallIcon = IconCompat.createWithBitmap(smallIconBitmap)
        
        // Create large icon for expanded notification
        val largeIconSize = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
        val largeIconBitmap = createNavModeIcon(largeIconSize, Color.TRANSPARENT, Color.WHITE)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Nav Mode Activated")
            .setContentText("Nav mode activated")
            .setSmallIcon(smallIcon) // Custom "N" icon for status bar
            .setLargeIcon(largeIconBitmap) // Large "N" icon for expanded notification
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority for silent notification
            .setAutoCancel(true) // Automatically dismissed when tapped
            .setOngoing(true) // Persistent, stays visible
            .setCategory(NotificationCompat.CATEGORY_STATUS) // Status category for status bar
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible on lock screen
            .setSilent(true) // Silent notification (no sound)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Cancels the nav mode notification.
     */
    fun cancelNavModeNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

