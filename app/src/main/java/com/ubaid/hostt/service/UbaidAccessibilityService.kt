package com.ubaid.hostt.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ubaid.hostt.data.ActivityLogRepository
import com.ubaid.hostt.model.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UbaidAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        ActivityLogRepository.log(
            title = "Accessibility Connected",
            description = "Touch dispatch and system actions ready.",
            category = LogCategory.SYSTEM
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be used for event-driven feedback if required
    }

    override fun onInterrupt() {
        _isServiceActive.value = false
        ActivityLogRepository.log(
            title = "Accessibility Interrupted",
            description = "Service was interrupted by system.",
            category = LogCategory.SYSTEM
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        _isServiceActive.value = false
    }

    fun dispatchTap(xRatio: Float, yRatio: Float, callback: ((Boolean) -> Unit)? = null) {
        val metrics = getDisplayBounds()
        val targetX = (xRatio.coerceIn(0f, 1f) * metrics.widthPixels)
        val targetY = (yRatio.coerceIn(0f, 1f) * metrics.heightPixels)

        val path = Path().apply {
            moveTo(targetX, targetY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                ActivityLogRepository.log(
                    title = "Remote Tap Dispatched",
                    description = "Position: (${(xRatio * 100).toInt()}%, ${(yRatio * 100).toInt()}%)",
                    category = LogCategory.TOUCH
                )
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                ActivityLogRepository.log(
                    title = "Remote Tap Cancelled",
                    description = "Position: (${(xRatio * 100).toInt()}%, ${(yRatio * 100).toInt()}%)",
                    category = LogCategory.TOUCH
                )
                callback?.invoke(false)
            }
        }, null)
    }

    fun dispatchSwipe(
        x1Ratio: Float,
        y1Ratio: Float,
        x2Ratio: Float,
        y2Ratio: Float,
        durationMs: Long = 300L,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val metrics = getDisplayBounds()
        val startX = (x1Ratio.coerceIn(0f, 1f) * metrics.widthPixels)
        val startY = (y1Ratio.coerceIn(0f, 1f) * metrics.heightPixels)
        val endX = (x2Ratio.coerceIn(0f, 1f) * metrics.widthPixels)
        val endY = (y2Ratio.coerceIn(0f, 1f) * metrics.heightPixels)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val boundedDuration = durationMs.coerceIn(50L, 2500L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, boundedDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                ActivityLogRepository.log(
                    title = "Remote Swipe Dispatched",
                    description = "From (${(x1Ratio * 100).toInt()}%, ${(y1Ratio * 100).toInt()}%) to (${(x2Ratio * 100).toInt()}%, ${(y2Ratio * 100).toInt()}%)",
                    category = LogCategory.TOUCH
                )
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                ActivityLogRepository.log(
                    title = "Remote Swipe Cancelled",
                    description = "Swipe failed or cancelled by system.",
                    category = LogCategory.TOUCH
                )
                callback?.invoke(false)
            }
        }, null)
    }

    fun executeGlobalNavAction(action: String): Boolean {
        val result = when (action.lowercase()) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "power", "lock" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    false
                }
            }
            else -> false
        }
        ActivityLogRepository.log(
            title = "Navigation Action: $action",
            description = if (result) "Action executed successfully" else "Action could not be executed",
            category = LogCategory.TOUCH
        )
        return result
    }

    fun injectTextInput(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            ActivityLogRepository.log(
                title = "Text Injected",
                description = "Length: ${text.length} chars (success: $success)",
                category = LogCategory.TOUCH
            )
            return success
        }
        return false
    }

    private fun getDisplayBounds(): DisplayMetrics {
        return resources.displayMetrics
    }

    companion object {
        var instance: UbaidAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${UbaidAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = enabledServicesSetting.split(":")
            for (componentString in colonSplitter) {
                if (componentString.equals(expectedServiceName, ignoreCase = true) ||
                    componentString.contains(UbaidAccessibilityService::class.java.simpleName)
                ) {
                    return true
                }
            }
            return false
        }
    }
}
