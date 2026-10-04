package com.ubaid.hostt.service

import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.ubaid.hostt.data.ActivityLogRepository
import com.ubaid.hostt.model.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class UbaidNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isListenerActive.value = true
        ActivityLogRepository.log(
            title = "Notification Listener Connected",
            description = "Monitoring incoming notifications for remote forwarding.",
            category = LogCategory.SYSTEM
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        _isListenerActive.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Skip our own ongoing host service notification
        if (pkg == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg
        }

        val json = JSONObject().apply {
            put("type", "notification_posted")
            put("id", sbn.id)
            put("package", pkg)
            put("appName", appName)
            put("title", title)
            put("text", text)
            put("timestamp", sbn.postTime)
        }

        ActivityLogRepository.log(
            title = "Notification: $appName",
            description = if (title.isNotBlank()) "$title: $text" else text.take(60),
            category = LogCategory.NOTIFICATION
        )

        notificationEventListener?.invoke(json)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val json = JSONObject().apply {
            put("type", "notification_removed")
            put("id", sbn.id)
            put("package", pkg)
            put("timestamp", System.currentTimeMillis())
        }

        notificationEventListener?.invoke(json)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        _isListenerActive.value = false
    }

    companion object {
        var instance: UbaidNotificationListenerService? = null
            private set

        private val _isListenerActive = MutableStateFlow(false)
        val isListenerActive: StateFlow<Boolean> = _isListenerActive.asStateFlow()

        var notificationEventListener: ((JSONObject) -> Unit)? = null

        fun isEnabled(context: Context): Boolean {
            val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
            return packageNames.contains(context.packageName)
        }
    }
}
