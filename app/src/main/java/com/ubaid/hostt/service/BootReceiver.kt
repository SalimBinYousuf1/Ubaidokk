package com.ubaid.hostt.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ubaid.hostt.data.HostPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = HostPreferences.getInstance(context)
            if (prefs.isSetupCompleted) {
                // Schedule watchdog worker to keep the service healthy
                WatchdogWorker.schedule(context)
            }
        }
    }
}
