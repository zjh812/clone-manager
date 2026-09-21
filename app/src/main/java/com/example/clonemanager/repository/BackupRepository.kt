package com.example.clonemanager.repository

import android.content.Context
import com.example.clonemanager.root.ShellExecutor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分身数据备份 / 恢复。
 *
 * 备份原理（root）：
 *   tar czf <dest> -C /data/user/<userId> .
 *
 * 恢复原理：
 *   1. am stop-user -f <userId>
 *   2. tar xzf <src> -C /data/user/<userId>
 *   3. am start-user -w <userId>
 *
 * 备份文件：/sdcard/Android/data/<pkg>/files/backups/<userId>_<timestamp>.tar.gz
 */
class BackupRepository(
    private val context: Context,
    private val shell: ShellExecutor
) {

    data class BackupInfo(
        val userId: Int,
        val fileName: String,
        val filePath: String,
        val sizeBytes: Long,
        val timestamp: Long
    )

    private val backupDir: File by lazy {
        File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
    }

    suspend fun listBackups(userId: Int): List<BackupInfo> {
        return backupDir.listFiles { f ->
            f.isFile && f.name.endsWith(".tar.gz") && f.name.startsWith("clone_${userId}_")
        }?.map { f ->
            val ts = f.name.removePrefix("clone_${userId}_").removeSuffix(".tar.gz").toLongOrNull() ?: 0L
            BackupInfo(userId, f.name, f.absolutePath, f.length(), ts)
        }?.sortedByDescending { it.timestamp } ?: emptyList()
    }

    suspend fun backupClone(userId: Int, cloneName: String): OpResult {
        if (userId == 0) return OpResult.fail("禁止备份系统用户 0")
        val ts = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(ts))
        val dest = File(backupDir, "clone_${userId}_$stamp.tar.gz").absolutePath

        val check = shell.execute("[ -d /data/user/$userId ] && echo OK || echo NO", 5_000)
        if (!check.stdout.contains("OK")) {
            return OpResult.fail("分身 $userId 的数据目录不存在：/data/user/$userId")
        }

        val cmd = "tar czf \"$dest\" -C /data/user/$userId . 2>&1"
        val res = shell.execute(cmd, 10 * 60 * 1000)
        val f = File(dest)
        if (f.exists() && f.length() > 0) {
            val sizeMb = f.length() / 1024.0 / 1024.0
            return OpResult.ok("已备份「$cloneName」(User $userId)\n文件：$stamp.tar.gz\n大小：%.1f MB".format(sizeMb))
        }
        return OpResult.fail(
            "备份失败（exit=${res.exitCode}）",
            detail = buildString {
                append("命令: ").append(cmd).append('\n')
                append("[stdout]\n").append(res.stdout).append('\n')
                append("[stderr]\n").append(res.stderr)
            }
        )
    }

    suspend fun restoreBackup(userId: Int, backup: BackupInfo): OpResult {
        if (userId == 0) return OpResult.fail("禁止恢复系统用户 0")
        val src = backup.filePath
        if (!File(src).exists()) return OpResult.fail("备份文件不存在：$src")

        shell.execute("am stop-user -w -f $userId", 20_000)
        Thread.sleep(500)

        val cmd = "tar xzf \"$src\" -C /data/user/$userId 2>&1"
        val res = shell.execute(cmd, 10 * 60 * 1000)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail(
                "解包失败（exit=${res.exitCode}）",
                detail = buildString {
                    append("命令: ").append(cmd).append('\n')
                    append("[stdout]\n").append(res.stdout).append('\n')
                    append("[stderr]\n").append(res.stderr)
                }
            )
        }

        shell.execute("am start-user -w $userId", 30_000)
        return OpResult.ok("已恢复备份「${backup.fileName}」到 User $userId\n分身已重新启动")
    }

    suspend fun deleteBackup(backup: BackupInfo): OpResult {
        val f = File(backup.filePath)
        return if (f.delete()) OpResult.ok("已删除 ${backup.fileName}")
        else OpResult.fail("删除失败：${backup.fileName}")
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
