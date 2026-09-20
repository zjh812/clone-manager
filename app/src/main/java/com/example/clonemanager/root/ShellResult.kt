package com.example.clonemanager.root

/**
 * 一次 shell 命令执行的完整结果。
 *
 * exitCode 约定：
 * - >= 0       进程真实退出码（0 = 成功）
 * - -1         无法启动 su 进程（su 不存在 / 无 root 权限）
 * - -2         执行超时，进程已被强制销毁
 * - -3         执行被中断
 */
data class ShellResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val success: Boolean
) {
    /** 合并后的完整输出，便于展示 / 日志。 */
    fun fullOutput(): String = buildString {
        append("$ command: ").append(command).append('\n')
        append("[stdout]\n").append(stdout.trimEnd()).append('\n')
        append("[stderr]\n").append(stderr.trimEnd()).append('\n')
        append("[exitCode] ").append(exitCode)
    }
}
