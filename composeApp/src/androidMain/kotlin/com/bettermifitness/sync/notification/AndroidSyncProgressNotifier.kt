package com.bettermifitness.sync.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bettermifitness.sync.sync.SyncProgressNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AndroidSyncProgressNotifier(
    private val context: Context,
) : SyncProgressNotifier {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val iconRes: Int by lazy {
        val res = context.resources.getIdentifier(
            "ic_sync_notification",
            "drawable",
            context.packageName,
        )
        if (res != 0) res else context.applicationInfo.icon
    }

    private val contentIntent: PendingIntent? by lazy {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else null
    }

    companion object {
        private const val TAG = "SyncNotification"
        const val CHANNEL_ID = "sync_progress_v4"
        const val NOTIFICATION_ID = 2001
        private const val FOCUS_PARAM_KEY = "miui.focus.param"
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                notificationManager?.deleteNotificationChannel("sync_progress_channel")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Синхронизация данных",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Прогресс синхронизации данных Mi Fitness с Health Connect"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun hasPermission(): Boolean {
        val manager = notificationManager ?: return false
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled in system app settings")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS runtime permission not granted")
            }
            return granted
        }
        return true
    }

    private fun buildHyperOsFocusParam(ticker: String, percent: Int): String {
        return """
        {
          "param_v2": {
            "protocol": 1,
            "business": "fitness",
            "enableFloat": true,
            "updatable": true,
            "ticker": "$ticker",
            "param_island": {
              "islandProperty": 1,
              "smallIslandArea": {
                "type": 1
              },
              "bigIslandArea": {
                "type": 1
              }
            }
          }
        }
        """.trimIndent()
    }

    override fun onSyncStarted(total: Int) {
        if (!hasPermission()) return
        val title = "Better Mi Fitness Sync"
        val text = "Запуск синхронизации (0 из $total)..."
        val focusParam = buildHyperOsFocusParam(text, 0)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setProgress(total, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                extras.putString(FOCUS_PARAM_KEY, focusParam)
            }
            .build()

        try {
            notificationManager?.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Posted sync start notification")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post sync start notification", e)
        }
    }

    override fun onProgress(done: Int, total: Int, currentMetricName: String) {
        if (!hasPermission()) return
        val percent = if (total > 0) (done * 100) / total else 0
        val text = if (currentMetricName.isNotBlank()) {
            "$currentMetricName ($done из $total, $percent%)"
        } else {
            "Синхронизация ($done из $total, $percent%)"
        }
        val focusParam = buildHyperOsFocusParam(text, percent)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("Better Mi Fitness Sync")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                extras.putString(FOCUS_PARAM_KEY, focusParam)
            }
            .build()

        try {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sync progress notification", e)
        }
    }

    override fun onSyncFinished(success: Boolean, message: String?) {
        if (!hasPermission()) return
        val title = "Better Mi Fitness Sync"
        val text = if (success) "Синхронизация завершена" else (message ?: "Ошибка синхронизации")
        val focusParam = buildHyperOsFocusParam(text, 100)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                extras.putString(FOCUS_PARAM_KEY, focusParam)
            }
            .build()

        try {
            notificationManager?.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Posted sync finished notification: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post sync finish notification", e)
        }

        // Auto dismiss after 2.5 seconds so notification bar stays clean
        scope.launch {
            delay(2500)
            try {
                notificationManager?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }
}
