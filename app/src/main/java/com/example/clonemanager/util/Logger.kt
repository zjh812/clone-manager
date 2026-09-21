package com.example.clonemanager.util

import com.example.clonemanager.root.ShellResult

object Logger {
    const val TAG = "CloneManager"
    const val MAX_ENTRIES = 300
    data class LogEntry(val command: String, val stdout: String, val stderr: String, val exitCode: Int, val elapsedMs: Long, val timestamp: Long) {
        fun render(): String = buildString {
            append('[').append(java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(timestamp))).append("] $ ").append(command).append('\n')
            append("[stdout]\n").append(stdout.trimEnd()).append('\n')
            append("[stderr]\n").append(stderr.trimEnd()).append('\n')
            append("[exitCode] ").append(exitCode).append("  [耗时] ").append(elapsedMs).append("ms")
        }
    }
    private val buffer = ArrayDeque<LogEntry>()
    @Synchronized fun logShell(result: ShellResult, elapsedMs: Long) {
        buffer.addLast(LogEntry(result.command, result.stdout, result.stderr, result.exitCode, elapsedMs, System.currentTimeMillis()))
        while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
    }
    @Synchronized fun entries(): List<LogEntry> = buffer.toList()
    @Synchronized fun clear() { buffer.clear() }
    @Synchronized fun dump(): String = buffer.joinToString("\n\n") { it.render() }
}
