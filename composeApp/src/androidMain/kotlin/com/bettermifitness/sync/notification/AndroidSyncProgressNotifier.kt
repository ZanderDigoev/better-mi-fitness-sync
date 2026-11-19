package com.bettermifitness.sync.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bettermifitness.sync.sync.SyncProgressNotifier
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
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
        if (res != 0) res else android.R.drawable.ic_popup_sync
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
        const val CHANNEL_ID = "sync_progress_v5"
        const val NOTIFICATION_ID = 2001
        private const val AUTO_DISMISS_DELAY_MS = 8000L
        private const val BRAND_ORANGE_HEX = "#FF6B35"
        private const val PIC_KEY_SYNC = "sync_icon"
        private const val NOTIFICATION_TITLE = "Mi Fit sync"
    }

    private fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER
        val b = Build.BRAND
        return m.equals("Xiaomi", ignoreCase = true) ||
            b.equals("Xiaomi", ignoreCase = true) ||
            b.equals("POCO", ignoreCase = true) ||
            b.equals("Redmi", ignoreCase = true)
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                notificationManager?.deleteNotificationChannel("sync_progress_channel")
                notificationManager?.deleteNotificationChannel("sync_progress_v4")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Синхронизация данных",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Прогресс синхронизации данных Mi Fitness с Health Connect"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
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

    private fun attachHyperIslandExtras(
        builder: NotificationCompat.Builder,
        title: String,
        text: String,
        percent: Int,
        isFinished: Boolean = false,
    ) {
        try {
            if (isXiaomi()) {
                val icon = Icon.createWithResource(context, iconRes)
                val pic = HyperPicture(PIC_KEY_SYNC, icon)
                val island = HyperIslandNotification.Builder(context, "sync_progress", text)
                    .addPicture(pic)
                    .setSmallIslandCircularProgress(
                        PIC_KEY_SYNC,
                        percent,
                        BRAND_ORANGE_HEX,
                        null,
                        false,
                    )
                    .setBigIslandProgressCircle(
                        PIC_KEY_SYNC,
                        title,
                        percent,
                        BRAND_ORANGE_HEX,
                        false,
                    )

                if (isFinished) {
                    island.setCancel(true)
                }

                val bundle = island.buildResourceBundle()
                val jsonParam = island.buildJsonParam()

                builder.addExtras(bundle)
                builder.extras.putString("miui.focus.param", jsonParam)
                Log.d(TAG, "Attached HyperIsland payload for progress: $percent%")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "HyperIsland attachment fallback: ${e.message}")
        }
    }

    override fun onSyncStarted(total: Int) {
        if (!hasPermission()) return
        val title = NOTIFICATION_TITLE
        val text = "Запуск синхронизации (0 из $total)..."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setProgress(total, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        attachHyperIslandExtras(builder, title, text, 0)

        try {
            notificationManager?.notify(NOTIFICATION_ID, builder.build())
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
        val title = NOTIFICATION_TITLE

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        attachHyperIslandExtras(builder, title, text, percent)

        try {
            notificationManager?.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sync progress notification", e)
        }
    }

    override fun onSyncFinished(success: Boolean, message: String?) {
        if (!hasPermission()) return
        val title = NOTIFICATION_TITLE
        val text = if (success) "Синхронизация завершена" else (message ?: "Ошибка синхронизации")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        attachHyperIslandExtras(builder, title, text, 100, isFinished = true)

        try {
            notificationManager?.notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Posted sync finished notification: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post sync finish notification", e)
        }

        if (success) {
            scope.launch {
                delay(AUTO_DISMISS_DELAY_MS)
                try {
                    notificationManager?.cancel(NOTIFICATION_ID)
                } catch (_: Exception) {}
            }
        }
    }
}
