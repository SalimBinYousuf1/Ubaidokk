package com.ubaid.hostt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ubaid.hostt.MainActivity
import com.ubaid.hostt.R
import com.ubaid.hostt.data.ActivityLogRepository
import com.ubaid.hostt.data.HostPreferences
import com.ubaid.hostt.model.ConnectionStatus
import com.ubaid.hostt.model.LogCategory
import com.ubaid.hostt.status.DeviceStatusMonitor
import com.ubaid.hostt.webrtc.WebRtcHostManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UbaidHostService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var webRtcHostManager: WebRtcHostManager? = null
    private var deviceStatusMonitor: DeviceStatusMonitor? = null
    private var statusCollectorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isServiceRunning.value = true
        createNotificationChannel()

        val hostId = HostPreferences.getInstance(this).hostId
        webRtcHostManager = WebRtcHostManager(this, hostId)
        deviceStatusMonitor = DeviceStatusMonitor(this)

        startForegroundNotification(ConnectionStatus.DISCONNECTED)

        // Connect notification forwarding
        UbaidNotificationListenerService.notificationEventListener = { json ->
            webRtcHostManager?.sendNotification(json)
        }

        // Start device telemetry
        deviceStatusMonitor?.start { status ->
            webRtcHostManager?.sendDeviceStatus(status)
        }

        // Observe connection state
        statusCollectorJob = scope.launch {
            webRtcHostManager?.connectionStatus?.collect { status ->
                updateNotificationForStatus(status)
            }
        }

        ActivityLogRepository.log(
            title = "Foreground Host Service Started",
            description = "Broadcasting host $hostId with active watchdog.",
            category = LogCategory.SYSTEM
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_STOP -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_START -> {
                    val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
                    }

                    if (projectionData != null) {
                        cachedProjectionData = projectionData
                        webRtcHostManager?.setMediaProjectionIntent(projectionData)
                    } else if (cachedProjectionData != null) {
                        webRtcHostManager?.setMediaProjectionIntent(cachedProjectionData!!)
                    }

                    webRtcHostManager?.startSignaling()
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(status: ConnectionStatus) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationForStatus(status: ConnectionStatus) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: ConnectionStatus): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, UbaidHostService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when (status) {
            ConnectionStatus.CONNECTED -> getString(R.string.notification_text_connected)
            ConnectionStatus.WAITING_FOR_CONTROLLER -> getString(R.string.notification_text_waiting)
            ConnectionStatus.CONNECTING -> "Connecting to Salim controller…"
            ConnectionStatus.ERROR -> "Reconnecting WebRTC stream…"
            ConnectionStatus.DISCONNECTED -> "Host service standby"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ubaid_app_icon_1791139599785)
            .setContentTitle(getString(R.string.notification_title_active))
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Open Dashboard", openPendingIntent)
            .addAction(0, "Stop Host", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        statusCollectorJob?.cancel()
        deviceStatusMonitor?.stop()
        webRtcHostManager?.destroy()
        UbaidNotificationListenerService.notificationEventListener = null

        if (instance == this) {
            instance = null
        }
        _isServiceRunning.value = false

        ActivityLogRepository.log(
            title = "Host Service Stopped",
            description = "Clean shutdown complete.",
            category = LogCategory.SYSTEM
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "ubaid_host_foreground_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.ubaid.hostt.action.START"
        const val ACTION_STOP = "com.ubaid.hostt.action.STOP"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        var instance: UbaidHostService? = null
            private set

        var cachedProjectionData: Intent? = null

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun start(context: Context, projectionData: Intent? = null) {
            val intent = Intent(context, UbaidHostService::class.java).apply {
                action = ACTION_START
                if (projectionData != null) {
                    putExtra(EXTRA_PROJECTION_DATA, projectionData)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, UbaidHostService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
