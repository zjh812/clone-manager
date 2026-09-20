package com.example.clonemanager.root

import android.util.Log
import com.example.clonemanager.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class RootService : ShellExecutor {

    override suspend fun execute(
        command: String,
        timeoutMs: Long
    ): ShellResult = withContext(Dispatchers.IO) {
        val startAt = System.currentTimeMillis()

        val process = try {
            ProcessBuilder(SU, "-c", command)
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            Logger.e(TAG, "启动 su 失败: ${e.message}")
            return@withContext ShellResult(
                command = command,
                stdout = "",
                stderr = "无法启动 su 进程：${e.message}\n（设备可能未 root，或 su 不在 PATH 中）",
                exitCode = EXIT_LAUNCH_FAILED,
                success = false
            )
        }

        val stdoutReader = StreamReader(process.inputStream)
        val stderrReader = StreamReader(process.errorStream)
        stdoutReader.start()
        stderrReader.start()

        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            return@withContext ShellResult(
                command = command,
                stdout = stdoutReader.await(),
                stderr = stderrReader.await() + "\n[shell] 命令被中断",
                exitCode = EXIT_INTERRUPTED,
                success = false
            )
        }

        if (!finished) {
            process.destroyForcibly()
            val out = stdoutReader.await()
            val err = stderrReader.await()
            return@withContext ShellResult(
                command = command,
                stdout = out,
                stderr = err + "\n[shell] 命令执行超时（${timeoutMs}ms）",
                exitCode = EXIT_TIMEOUT,
                success = false
            )
        }

        val exitCode = process.exitValue()
        val result = ShellResult(
            command = command,
            stdout = stdoutReader.await(),
            stderr = stderrReader.await(),
            exitCode = exitCode,
            success = exitCode == 0
        )

        Logger.logShell(result, System.currentTimeMillis() - startAt)
        if (!result.success && result.exitCode == EXIT_LAUNCH_FAILED) {
            Log.e(TAG, result.stderr)
        }
        result
    }

    private class StreamReader(private val stream: InputStream) {
        private var future: Future<String>? = null

        fun start() {
            future = POOL.submit(Callable {
                stream.bufferedReader().use { it.readText().trimEnd('\n') }
            })
        }

        fun await(): String = try {
            future?.get(READ_TIMEOUT_SEC, TimeUnit.SECONDS) ?: ""
        } catch (e: Exception) {
            ""
        }

        companion object {
            private const val READ_TIMEOUT_SEC = 30L
            private val POOL = Executors.newCachedThreadPool { r ->
                Thread(r, "shell-stream-reader").apply { isDaemon = true }
            }
        }
    }

    companion object {
        private const val TAG = "RootService"
        private const val SU = "su"
        const val EXIT_LAUNCH_FAILED = -1
        const val EXIT_TIMEOUT = -2
        const val EXIT_INTERRUPTED = -3
    }
}
