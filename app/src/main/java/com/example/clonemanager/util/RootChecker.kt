package com.example.clonemanager.util

import com.example.clonemanager.root.ShellExecutor

data class RootStatus(
    val available: Boolean,
    val output: String,
    val stderr: String,
    val exitCode: Int
)

object RootChecker {
    const val ROOT_MARKER = "uid=0(root)"

    suspend fun check(executor: ShellExecutor, timeoutMs: Long = 10_000L): RootStatus {
        val result = executor.execute("id", timeoutMs)
        val out = result.stdout.trim()
        val available = result.success && out.contains(ROOT_MARKER, ignoreCase = true)
        return RootStatus(available, out, result.stderr.trim(), result.exitCode)
    }
}
