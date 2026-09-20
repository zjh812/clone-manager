package com.example.clonemanager.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.example.clonemanager.data.AppInfo
import com.example.clonemanager.root.ShellExecutor
import com.example.clonemanager.root.ShellResult

class AppRepository(
    private val context: Context,
    private val shell: ShellExecutor
) {

    private val pm: PackageManager get() = context.packageManager

    private fun parsePackageLines(output: String): Set<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").substringBefore('\t') }
            .filter { it.isNotBlank() }
            .toSet()

    suspend fun listPackageNames(userId: Int): Set<String> {
        val res = shell.execute("pm list packages --user $userId", 15_000)
        if (!res.success) return emptySet()
        return parsePackageLines(res.stdout)
    }

    private fun enrich(packageName: String, installed: Boolean): AppInfo {
        return try {
            val info = pm.getPackageInfo(packageName, 0)
            val app: ApplicationInfo = info.applicationInfo
                ?: throw IllegalArgumentException("no applicationInfo")
            AppInfo(
                packageName = packageName,
                label = pm.getApplicationLabel(app).toString(),
                versionName = info.versionName,
                versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
                enabled = app.enabled,
                installed = installed
            )
        } catch (e: Exception) {
            AppInfo(packageName, packageName, null, null, true, installed)
        }
    }

    suspend fun listInstalledApps(userId: Int): List<AppInfo> {
        val pkgs = listPackageNames(userId)
        return pkgs.map { enrich(it, true) }.sortedBy { it.label.lowercase() }
    }

    suspend fun listAddableApps(cloneUserId: Int): List<AppInfo> {
        val clonePkgs = listPackageNames(cloneUserId)
        val user0Pkgs = listPackageNames(0)
        val addable = user0Pkgs - clonePkgs
        return addable
            .filter { !isProtectedApp(it) }
            .map { enrich(it, false) }
            .sortedBy { it.label.lowercase() }
    }

    private fun isProtectedApp(pkg: String): Boolean = when {
        pkg == context.packageName -> true
        pkg == "com.android.systemui" -> true
        pkg == "com.android.settings" -> true
        pkg == "android" -> true
        else -> {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolvers: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            resolvers.any { it.activityInfo.packageName == pkg }
        }
    }

    suspend fun installExisting(userId: Int, packageName: String): OpResult {
        val cmd = "pm install-existing --user $userId $packageName"
        val res = shell.execute(cmd, 20_000)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail("安装失败（exit=${res.exitCode}）", detail = buildDetail(cmd, res))
        }
        val now = listPackageNames(userId)
        return if (packageName in now) {
            OpResult.ok("已安装「${enrich(packageName, true).label}」到分身 $userId")
        } else {
            OpResult.fail("命令已执行，但分身 $userId 中仍未发现 $packageName", detail = buildDetail(cmd, res))
        }
    }

    suspend fun uninstallPackage(userId: Int, packageName: String): OpResult {
        if (isProtectedApp(packageName)) return OpResult.fail("受保护应用，禁止卸载")
        val label = enrich(packageName, true).label
        val cmd = "pm uninstall --user $userId $packageName"
        val res = shell.execute(cmd, 20_000)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail("卸载失败（exit=${res.exitCode}）", detail = buildDetail(cmd, res))
        }
        val now = listPackageNames(userId)
        return if (packageName !in now) {
            OpResult.ok("已从分身 $userId 卸载「$label」")
        } else {
            OpResult.fail("命令已执行，但分身 $userId 中仍存在 $packageName", detail = buildDetail(cmd, res))
        }
    }

    suspend fun clearData(userId: Int, packageName: String): OpResult {
        if (isProtectedApp(packageName)) return OpResult.fail("受保护应用，禁止清除数据")
        val label = enrich(packageName, true).label
        val cmd = "pm clear --user $userId $packageName"
        val res = shell.execute(cmd, 20_000)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail("清除数据失败（exit=${res.exitCode}）", detail = buildDetail(cmd, res))
        }
        val stdout = res.stdout.trim()
        return if (stdout.equals("Success", true)) {
            OpResult.ok("已清除「$label」在分身 $userId 中的数据")
        } else {
            OpResult.ok("已执行清除：$stdout")
        }
    }

    suspend fun launchPackage(userId: Int, packageName: String): OpResult {
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return OpResult.fail("未找到 $packageName 的启动 Activity")
        val component = intent.component ?: return OpResult.fail("启动 Intent 无 Component")
        val cmd = "am start --user $userId -n ${component.flattenToShortString()}"
        val res = shell.execute(cmd, 10_000)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail("启动失败（exit=${res.exitCode}）", detail = buildDetail(cmd, res))
        }
        return OpResult.ok("已在分身 $userId 启动「${enrich(packageName, true).label}」")
    }

    private fun buildDetail(command: String, res: ShellResult): String =
        buildString {
            append("命令: ").append(command).append('\n')
            append("[stdout]\n").append(res.stdout).append('\n')
            append("[stderr]\n").append(res.stderr).append('\n')
            append("exitCode: ").append(res.exitCode)
        }
}
