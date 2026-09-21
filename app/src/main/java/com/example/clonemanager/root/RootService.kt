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
    override suspend fun execute(command: String, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        val startAt = System.currentTimeMillis()
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(false).start()
        } catch (e: Exception) {
            return@withContext ShellResult(command, "", "无法启动 su 进程：${e.message}", -1, false)
        }
        val out = StreamReader(process.inputStream).apply { start() }
        val err = StreamReader(process.errorStream).apply { start() }
        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            return@withContext ShellResult(command, out.await(), err.await() + "\n[shell] 中断", -3, false)
        }
        if (!finished) {
            process.destroyForcibly()
            return@withContext ShellResult(command, out.await(), err.await() + "\n[shell] 超时 ${timeoutMs}ms", -2, false)
        }
        val exitCode = process.exitValue()
        val result = ShellResult(command, out.await(), err.await(), exitCode, exitCode == 0)
        Logger.logShell(result, System.currentTimeMillis() - startAt)
        result
    }

    private class StreamReader(private val stream: InputStream) {
        private var future: Future<String>? = null
        fun start() { future = Executors.newSingleThreadExecutor().submit(Callable { stream.bufferedReader().use { it.readText().trimEnd('\n') } }) }
        fun await(): String = try { future?.get(30, TimeUnit.SECONDS) ?: "" } catch (_: Exception) { "" }
    }
}
