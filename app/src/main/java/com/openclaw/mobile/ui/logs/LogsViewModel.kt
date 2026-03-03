package com.openclaw.mobile.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.mobile.utils.LogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logManager: LogManager
) : ViewModel() {

    private val _filterDebug = MutableStateFlow(false)
    val filterDebug: StateFlow<Boolean> = _filterDebug

    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll

    val logs = combine(logManager.logs, _filterDebug) { allLogs, filter ->
        if (filter) {
            allLogs.filter { it.level != LogManager.LogLevel.DEBUG && it.level != LogManager.LogLevel.VERBOSE }
        } else {
            allLogs
        }
    }

    fun setFilterDebug(enabled: Boolean) {
        _filterDebug.value = enabled
    }

    fun setAutoScroll(enabled: Boolean) {
        _autoScroll.value = enabled
    }

    fun clearLogs() {
        logManager.clearLogs()
    }

    fun exportLogs(): File? {
        return logManager.exportLogs()
    }
}
