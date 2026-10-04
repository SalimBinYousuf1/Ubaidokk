package com.ubaid.hostt.model

enum class ConnectionStatus {
    DISCONNECTED,
    WAITING_FOR_CONTROLLER,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class LogCategory {
    SYSTEM,
    WEBRTC,
    TOUCH,
    NOTIFICATION,
    FILE
}

data class ActivityLogItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val description: String,
    val category: LogCategory = LogCategory.SYSTEM
)

data class HostServiceState(
    val isServiceRunning: Boolean = false,
    val isScreenCaptureActive: Boolean = false,
    val isAccessibilityActive: Boolean = false,
    val isNotificationListenerActive: Boolean = false,
    val isStorageGranted: Boolean = false,
    val isBatteryOptExempt: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val controllerId: String? = null,
    val screenResolution: String = "1080x1920",
    val lastError: String? = null
)

data class DeviceStatus(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val powerSource: String = "Battery",
    val networkType: String = "Unknown",
    val ipAddress: String = "127.0.0.1",
    val isScreenOn: Boolean = true,
    val uptimeSeconds: Long = 0L
)

data class FileItemDto(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

sealed class ControlCommand {
    data class Tap(val x: Float, val y: Float) : ControlCommand()
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Long = 300L) : ControlCommand()
    data class Key(val action: String) : ControlCommand()
    data class Text(val text: String) : ControlCommand()
}
