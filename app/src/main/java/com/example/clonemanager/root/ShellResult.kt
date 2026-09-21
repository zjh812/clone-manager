package com.example.clonemanager.root

data class ShellResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val success: Boolean
) {
    fun fullOutput(): String = buildString {
        append("$ command: ").append(command).append('\n')
        append("[stdout]\n").append(stdout.trimEnd()).append('\n')
        append("[stderr]\n").append(stderr.trimEnd()).append('\n')
        append("[exitCode] ").append(exitCode)
    }
}
