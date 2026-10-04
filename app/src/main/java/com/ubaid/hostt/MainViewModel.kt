package com.ubaid.hostt

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ubaid.hostt.data.ActivityLogRepository
import com.ubaid.hostt.data.HostPreferences
import com.ubaid.hostt.model.ActivityLogItem
import com.ubaid.hostt.model.ConnectionStatus
import com.ubaid.hostt.model.DeviceStatus
import com.ubaid.hostt.service.UbaidAccessibilityService
import com.ubaid.hostt.service.UbaidHostService
import com.ubaid.hostt.service.UbaidNotificationListenerService
import com.ubaid.hostt.status.DeviceStatusMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class MainUiState(
    val hostId: String = "",
    val deviceName: String = "",
    val isSetupCompleted: Boolean = false,
    val isScreenCaptureAuthorized: Boolean = false,
    val isAccessibilityGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isStorageGranted: Boolean = false,
    val isBatteryOptExempt: Boolean = false,
    val isServiceRunning: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val deviceStatus: DeviceStatus = DeviceStatus(),
    val cachedProjectionIntent: Intent? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = HostPreferences.getInstance(application)
    private val statusMonitor = DeviceStatusMonitor(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            hostId = prefs.hostId,
            deviceName = prefs.deviceName,
            isSetupCompleted = prefs.isSetupCompleted
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val activityLogs: StateFlow<List<ActivityLogItem>> = ActivityLogRepository.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshPermissionStates()

        viewModelScope.launch {
            UbaidHostService.isServiceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(
                    isServiceRunning = running,
                    connectionStatus = if (running) ConnectionStatus.WAITING_FOR_CONTROLLER else ConnectionStatus.DISCONNECTED
                )
            }
        }

        viewModelScope.launch {
            statusMonitor.status.collect { status ->
                _uiState.value = _uiState.value.copy(deviceStatus = status)
            }
        }
        statusMonitor.start()
    }

    override fun onCleared() {
        super.onCleared()
        statusMonitor.stop()
    }

    fun refreshPermissionStates() {
        val context = getApplication<Application>()
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val accessibility = UbaidAccessibilityService.isEnabled(context)
        val notifications = UbaidNotificationListenerService.isEnabled(context)
        val storage = Environment.isExternalStorageManager()
        val batteryOpt = pm.isIgnoringBatteryOptimizations(context.packageName)
        val hasProjection = UbaidHostService.cachedProjectionData != null || _uiState.value.cachedProjectionIntent != null

        _uiState.value = _uiState.value.copy(
            isAccessibilityGranted = accessibility,
            isNotificationGranted = notifications,
            isStorageGranted = storage,
            isBatteryOptExempt = batteryOpt,
            isScreenCaptureAuthorized = hasProjection
        )
    }

    fun setScreenCaptureResult(intent: Intent?) {
        if (intent != null) {
            UbaidHostService.cachedProjectionData = intent
            _uiState.value = _uiState.value.copy(
                isScreenCaptureAuthorized = true,
                cachedProjectionIntent = intent
            )
        }
    }

    fun completeSetup() {
        prefs.isSetupCompleted = true
        _uiState.value = _uiState.value.copy(isSetupCompleted = true)
        val context = getApplication<Application>()
        val intent = _uiState.value.cachedProjectionIntent ?: UbaidHostService.cachedProjectionData
        UbaidHostService.start(context, intent)
    }

    fun toggleHostService() {
        val context = getApplication<Application>()
        if (_uiState.value.isServiceRunning) {
            UbaidHostService.stop(context)
        } else {
            val intent = _uiState.value.cachedProjectionIntent ?: UbaidHostService.cachedProjectionData
            UbaidHostService.start(context, intent)
        }
    }

    fun getPairingPayloadJson(): String {
        return JSONObject().apply {
            put("hostId", prefs.hostId)
            put("projectId", "salim-x-ubaid")
            put("app", "ubaid")
            put("version", 1)
            put("deviceName", prefs.deviceName)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }
}
