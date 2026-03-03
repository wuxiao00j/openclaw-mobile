package com.openclaw.mobile.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日志管理器
 */
@Singleton
class LogManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val MAX_LOG_LINES = 1000
        private const val LOG_FILE_NAME = "openclaw.log"
    }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    init {
        // 设置自定义 Timber 树
        Timber.plant(FileLoggingTree())
    }

    /**
     * 添加日志条目
     */
    fun addLog(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(entry)
        
        // 限制日志数量
        if (currentLogs.size > MAX_LOG_LINES) {
            currentLogs.removeAt(0)
        }
        
        _logs.value = currentLogs
    }

    /**
     * 清空日志
     */
    fun clearLogs() {
        _logs.value = emptyList()
        // 清空日志文件
        try {
            File(context.filesDir, LOG_FILE_NAME).writeText("")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear log file")
        }
    }

    /**
     * 导出日志到文件
     */
    fun exportLogs(): File? {
        return try {
            val exportFile = File(context.cacheDir, "openclaw_logs_${System.currentTimeMillis()}.txt")
            val content = _logs.value.joinToString("\n") { it.toString() }
            exportFile.writeText(content)
            exportFile
        } catch (e: Exception) {
            Timber.e(e, "Failed to export logs")
            null
        }
    }

    /**
     * 获取日志文件路径
     */
    fun getLogFile(): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    /**
     * 日志条目数据类
     */
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) {
        override fun toString(): String {
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            return "[$timeStr] ${level.name}/$tag: $message"
        }
    }

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    /**
     * 自定义 Timber 树，用于捕获日志
     */
    inner class FileLoggingTree : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, tag, message, t)
            
            val level = when (priority) {
                android.util.Log.VERBOSE -> LogLevel.VERBOSE
                android.util.Log.DEBUG -> LogLevel.DEBUG
                android.util.Log.INFO -> LogLevel.INFO
                android.util.Log.WARN -> LogLevel.WARN
                android.util.Log.ERROR -> LogLevel.ERROR
                else -> LogLevel.DEBUG
            }
            
            addLog(level, tag ?: "OpenClaw", message)
            
            // 同时写入文件
            try {
                val logFile = File(context.filesDir, LOG_FILE_NAME)
                val entry = LogEntry(System.currentTimeMillis(), level, tag ?: "OpenClaw", message)
                logFile.appendText("$entry\n")
            } catch (e: Exception) {
                // 忽略文件写入错误
            }
        }
    }
}
