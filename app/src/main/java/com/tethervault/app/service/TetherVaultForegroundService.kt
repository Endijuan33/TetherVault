package com.tethervault.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.tethervault.app.R
import com.tethervault.app.domain.hotspot.HotspotManager
import com.tethervault.app.domain.model.HotspotState
import com.tethervault.app.presentation.main.MainActivity
import com.tethervault.app.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TetherVaultForegroundService : Service() {

    @Inject lateinit var hotspotManager: HotspotManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var isInForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            hotspotManager.hotspotState.collect { state ->
                if (isInForeground) {
                    updateNotification(state)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_SERVICE -> {
                startInForeground()
                serviceScope.launch { hotspotManager.startHotspot() }
            }
            Constants.ACTION_STOP_SERVICE -> {
                // The stop action arrives via getForegroundService PendingIntent,
                // so startForeground must be called again before stopping.
                startInForeground()
                serviceScope.launch {
                    hotspotManager.stopHotspot()
                    stopSelf()
                }
            }
            else -> startInForeground()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Best-effort teardown if the system kills the service while active;
        // runs on an orphan scope so it survives the service scope cancellation.
        val state = hotspotManager.hotspotState.value
        if (state is HotspotState.Starting || state is HotspotState.Running) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                runCatching { hotspotManager.stopHotspot() }
            }
        }
        serviceScope.cancel()
        isInForeground = false
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification(hotspotManager.hotspotState.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(Constants.SERVICE_NOTIFICATION_ID, notification)
        }
        isInForeground = true
    }

    private fun updateNotification(state: HotspotState) {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(this).notify(
                Constants.SERVICE_NOTIFICATION_ID,
                buildNotification(state)
            )
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: HotspotState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, TetherVaultForegroundService::class.java)
                .setAction(Constants.ACTION_STOP_SERVICE),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(notificationText(state))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(this, R.drawable.ic_stop),
                    getString(R.string.notification_action_stop),
                    stopIntent
                ).build()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notificationText(state: HotspotState): String = when (state) {
        HotspotState.Starting -> getString(R.string.service_notification_starting)
        is HotspotState.Running -> getString(R.string.service_notification_ssid, state.ssid)
        is HotspotState.Error -> getString(R.string.service_notification_error, state.message)
        HotspotState.Idle -> getString(R.string.service_notification_text)
    }
}
