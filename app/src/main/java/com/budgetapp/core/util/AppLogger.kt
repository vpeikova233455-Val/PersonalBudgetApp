package com.budgetapp.core.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_BUFFER_SIZE = 500
    private val logBuffer = ArrayDeque<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String, t: Throwable? = null) = log("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log("E", tag, msg, t)

    private fun log(level: String, tag: String, msg: String, t: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val entry = buildString {
            append("[$timestamp] $level/$tag: $msg")
            t?.let { append("\n${it.stackTraceToString()}") }
        }
        synchronized(logBuffer) {
            if (logBuffer.size >= MAX_BUFFER_SIZE) logBuffer.removeFirst()
            logBuffer.addLast(entry)
        }
        when (level) {
            "D" -> Log.d(tag, msg)
            "I" -> Log.i(tag, msg)
            "W" -> if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
            "E" -> if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        }
    }

    fun getLogs(): String = synchronized(logBuffer) { logBuffer.joinToString("\n") }
    fun clearLogs() = synchronized(logBuffer) { logBuffer.clear() }
}
