package com.example.clonemanager.util

import com.example.clonemanager.root.ShellExecutor

data class RootStatus(val available: Boolean, val output: String, val stderr: String, val exitCode: Int)

object RootChecker {
    suspend fun check(shell: ShellExecutor): RootStatus {
        val r = shell.execute("id")
        return RootStatus(r.success && r.stdout.contains("uid=0"), r.stdout.trim(), r.stderr.trim(), r.exitCode)
    }
}
