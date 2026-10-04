package com.ubaid.hostt.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ubaid.hostt.MainActivity
import com.ubaid.hostt.R
import com.ubaid.hostt.data.ActivityLogRepository
import com.ubaid.hostt.data.HostPreferences
import com.ubaid.hostt.model.LogCategory
import java.util.concurrent.TimeUnit

class WatchdogWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = HostPreferences.getInstance(context)
        if (!prefs.isSetupCompleted) {
            return Result.success()
        }

        if (UbaidHostService.instance == null) {
            if (UbaidHostService.cachedProjectionData != null) {
                // Resume service automatically
                UbaidHostService.start(context, UbaidHostService.cachedProjectionData)
                ActivityLogRepository.log(
                    title = "Watchdog Auto-Restart",
                    description = "Restored host service from cached capture session.",
                    category = LogCategory.SYSTEM
                )
            } else {
                // Screen capture permission token was discarded by the OS on process kill.
                // Surface honest notification to user for one-tap resume.
                showTapToResumeNotification()
                ActivityLogRepository.log(
                    title = "Watchdog Prompt",
                    description = "Screen capture token expired. Requested user tap to resume.",
                    category = LogCategory.SYSTEM
                )
            }
        }

        return Result.success()
    }

    private fun showTapToResumeNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PROMPT_SCREEN_CAPTURE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UbaidHostService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ubaid_app_icon_1791139599785)
            .setContentTitle(context.getString(R.string.notification_tap_to_resume_title))
            .setContentText(context.getString(R.string.notification_tap_to_resume_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(TAP_TO_RESUME_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORK_NAME = "ubaid_host_watchdog"
        private const val TAP_TO_RESUME_NOTIFICATION_ID = 2002

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
