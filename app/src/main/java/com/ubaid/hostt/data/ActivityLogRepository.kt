package com.ubaid.hostt.data

import com.ubaid.hostt.model.ActivityLogItem
import com.ubaid.hostt.model.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

object ActivityLogRepository {
    private const val MAX_LOGS = 60
    private val logList = CopyOnWriteArrayList<ActivityLogItem>()
    private val _logsFlow = MutableStateFlow<List<ActivityLogItem>>(emptyList())
    val logsFlow: StateFlow<List<ActivityLogItem>> = _logsFlow.asStateFlow()

    init {
        log(
            title = "Ubaid Host Initialized",
            description = "Subsystems ready. WebRTC signaling listener standby.",
            category = LogCategory.SYSTEM
        )
    }

    fun log(title: String, description: String, category: LogCategory = LogCategory.SYSTEM) {
        val item = ActivityLogItem(
            title = title,
            description = description,
            category = category
        )
        logList.add(0, item)
        while (logList.size > MAX_LOGS) {
            logList.removeAt(logList.size - 1)
        }
        _logsFlow.value = logList.toList()
    }

    fun clear() {
        logList.clear()
        _logsFlow.value = emptyList()
    }
}
