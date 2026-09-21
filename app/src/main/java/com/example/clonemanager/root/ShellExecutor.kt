package com.example.clonemanager.root

interface ShellExecutor {
    suspend fun execute(command: String, timeoutMs: Long = 15_000L): ShellResult
}
