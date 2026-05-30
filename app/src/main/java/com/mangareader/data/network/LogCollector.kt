package com.mangareader.data.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
) {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    val formattedTime: String get() = timeFormat.format(Date(timestamp))
    val fullTimestamp: String get() = dateFormat.format(Date(timestamp))

    val formattedMessage: String
        get() {
            val base = "$fullTimestamp ${level.name.padEnd(5)} [$tag] $message"
            return if (throwable != null) "$base\n${throwable.stackTraceToString()}" else base
        }
}

object LogCollector {
    private const val MAX_ENTRIES = 1000

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    /** Log file — written in real time */
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.cacheDir, "mangareader_logs.txt").also {
            it.writeText("=== MangaReader Log Started ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ===\n")
        }
    }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(level = level, tag = tag, message = message, throwable = throwable)
        _logs.value = (_logs.value + entry).takeLast(MAX_ENTRIES)
        // Append to file
        try {
            logFile?.appendText(entry.formattedMessage + "\n")
        } catch (_: Exception) {}
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    fun clear() {
        _logs.value = emptyList()
        try {
            logFile?.writeText("=== Log Cleared ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ===\n")
        } catch (_: Exception) {}
    }

    fun getLogFile(): File? = logFile

    /**
     * Timber Tree that feeds all Timber calls into LogCollector.
     */
    class CollectingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Skip if already came from LogCollector (avoid infinite loop)
            if (tag == "LogCollector") return

            val level = when (priority) {
                android.util.Log.VERBOSE, android.util.Log.DEBUG -> LogLevel.DEBUG
                android.util.Log.INFO -> LogLevel.INFO
                android.util.Log.WARN -> LogLevel.WARN
                android.util.Log.ERROR -> LogLevel.ERROR
                else -> LogLevel.DEBUG
            }
            log(level, tag ?: "App", message, t)
        }
    }
}
