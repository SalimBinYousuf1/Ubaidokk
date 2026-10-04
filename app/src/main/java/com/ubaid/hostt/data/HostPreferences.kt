package com.ubaid.hostt.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

class HostPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ubaid_host_prefs", Context.MODE_PRIVATE)

    var hostId: String
        get() {
            var id = prefs.getString(KEY_HOST_ID, null)
            if (id.isNullOrBlank()) {
                val randomSuffix = UUID.randomUUID().toString().substring(0, 8).uppercase()
                id = "UBAID-$randomSuffix"
                prefs.edit().putString(KEY_HOST_ID, id).apply()
            }
            return id
        }
        set(value) {
            prefs.edit().putString(KEY_HOST_ID, value).apply()
        }

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    var deviceName: String
        get() {
            val defaultName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Host)"
            return prefs.getString(KEY_DEVICE_NAME, defaultName) ?: defaultName
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var lastConnectedController: String?
        get() = prefs.getString(KEY_LAST_CONTROLLER, null)
        set(value) = prefs.edit().putString(KEY_LAST_CONTROLLER, value).apply()

    companion object {
        private const val KEY_HOST_ID = "key_host_id"
        private const val KEY_SETUP_COMPLETED = "key_setup_completed"
        private const val KEY_DEVICE_NAME = "key_device_name"
        private const val KEY_LAST_CONTROLLER = "key_last_controller"

        @Volatile
        private var INSTANCE: HostPreferences? = null

        fun getInstance(context: Context): HostPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HostPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
