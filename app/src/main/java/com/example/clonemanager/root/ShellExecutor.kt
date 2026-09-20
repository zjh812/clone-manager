package com.example.clonemanager.root

/**
 * 所有 root shell 操作的统一抽象。
 */
interface ShellExecutor {
    suspend fun execute(
        command: String,
        timeoutMs: Long = 15_000L
    ): ShellResult
}
