package com.example.clonemanager.repository

import com.example.clonemanager.data.CloneProfile
import com.example.clonemanager.root.ShellExecutor
import com.example.clonemanager.system.ActivityCommandDetector
import com.example.clonemanager.system.DeviceCapabilities
import com.example.clonemanager.system.DumpsysParser
import com.example.clonemanager.system.PackageCommandDetector
import com.example.clonemanager.system.UserCommandDetector
import com.example.clonemanager.util.RootChecker
import com.example.clonemanager.util.RootStatus

class CloneRepository(private val shell: ShellExecutor) {

    private val parser = DumpsysParser()

    suspend fun checkRoot(): RootStatus = RootChecker.check(shell)

    suspend fun getCloneLimit(): Int? {
        val result = shell.execute("dumpsys user")
        if (!result.success) return null
        return parser.parseCloneLimit(result.stdout)
    }

    suspend fun listCloneProfiles(): List<CloneProfile> {
        val cmdList = shell.execute("cmd user list")
        val dumpsys = shell.execute("dumpsys user")
        val users = parser.parseUsers(
            cmdUserListOutput = if (cmdList.success) cmdList.stdout else null,
            dumpsysOutput = if (dumpsys.success) dumpsys.stdout else null
        )
        return users.filter { it.isClone }.map {
            CloneProfile(it.userId, it.name, it.userType ?: "", it.flags,
                it.running, it.unlocked, 0)
        }.sortedBy { it.userId }
    }

    data class HomeSnapshot(
        val root: RootStatus,
        val clones: List<CloneProfile>,
        val allUserIds: List<Int>,
        val cloneLimit: Int?
    )

    suspend fun loadHomeSnapshotWithCapabilities(): Pair<HomeSnapshot, DeviceCapabilities> {
        val idRes = shell.execute("id")
        val listRes = shell.execute("cmd user list")
        val dumpsysRes = shell.execute("dumpsys user")

        val root = RootStatus(
            available = idRes.success && idRes.stdout.contains("uid=0"),
            output = idRes.stdout.trim(),
            stderr = idRes.stderr.trim(),
            exitCode = idRes.exitCode
        )
        val users = parser.parseUsers(
            cmdUserListOutput = if (listRes.success) listRes.stdout else null,
            dumpsysOutput = if (dumpsysRes.success) dumpsysRes.stdout else null
        )
        val clones = users.filter { it.isClone }.map {
            CloneProfile(it.userId, it.name, it.userType ?: "", it.flags,
                it.running, it.unlocked, 0)
        }.sortedBy { it.userId }
        val limit = if (dumpsysRes.success) parser.parseCloneLimit(dumpsysRes.stdout) else null

        val snapshot = HomeSnapshot(root, clones, users.map { it.userId }.sorted(), limit)

        val cmdUserHelpPrimary = shell.execute("cmd user help", 10_000)
        val cmdUserHelp = if (cmdUserHelpPrimary.success && cmdUserHelpPrimary.stdout.isNotBlank())
            cmdUserHelpPrimary.stdout else shell.execute("cmd user", 10_000).stdout
        val cmdPackageHelp = shell.execute("cmd package help", 10_000)
        val pmHelp = shell.execute("pm help", 10_000)
        val amHelp = shell.execute("am help", 10_000)

        val userDetector = UserCommandDetector()
        val pkgDetector = PackageCommandDetector()
        val amDetector = ActivityCommandDetector()

        val capabilities = listOf(
            userDetector.detectStartUser(cmdUserHelp, amHelp.stdout),
            userDetector.detectStopUser(cmdUserHelp, amHelp.stdout),
            userDetector.detectUserStateQuery(cmdUserHelp, amHelp.stdout),
            userDetector.detectIsUserStopped(amHelp.stdout),
            userDetector.detectSwitchUser(amHelp.stdout),
            userDetector.detectUnlockUser(amHelp.stdout),
            pkgDetector.detectListPackages(pmHelp.stdout),
            pkgDetector.detectInstallExisting(pmHelp.stdout),
            pkgDetector.detectUninstall(pmHelp.stdout),
            pkgDetector.detectClear(pmHelp.stdout),
            pkgDetector.detectCreateUser(pmHelp.stdout),
            pkgDetector.detectRemoveUser(pmHelp.stdout),
            pkgDetector.detectSetUserName(pmHelp.stdout),
            amDetector.detectStart(amHelp.stdout)
        )

        val failures = mutableMapOf<String, String>()
        listOf("cmd package help" to cmdPackageHelp, "pm help" to pmHelp, "am help" to amHelp)
            .forEach { (name, r) -> if (!r.success && r.exitCode != 0) failures[name] = "exit=${r.exitCode} ${r.stderr.trim()}" }

        val caps = DeviceCapabilities(
            rootAvailable = root.available,
            rootOutput = root.output,
            cloneTypeSupported = dumpsysRes.stdout.contains(CloneProfile.CLONE_TYPE),
            cloneLimit = limit,
            capabilities = capabilities,
            helpFailures = failures
        )
        return snapshot to caps
    }

    suspend fun startCloneProfile(userId: Int, name: String): OpResult {
        if (userId == CloneProfile.USER_ID_SYSTEM) return OpResult.fail("禁止操作系统用户 0")
        val cmd = "am start-user -w $userId"
        val res = shell.execute(cmd)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail("启动命令执行失败（exit=${res.exitCode}）",
                detail = buildDetail(cmd, res.stdout, res.stderr))
        }
        val refreshed = listCloneProfiles().find { it.userId == userId }
        return if (refreshed?.running == true) OpResult.ok("已启动「$name」（User $userId）")
        else OpResult.fail("命令已执行，但 User $userId 仍未运行",
            detail = buildDetail(cmd, res.stdout, res.stderr))
    }

    suspend fun stopCloneProfile(userId: Int, name: String): OpResult {
        if (userId == CloneProfile.USER_ID_SYSTEM) return OpResult.fail("禁止操作系统用户 0")
        val cmd = "am stop-user -w -f $userId"
        val res = shell.execute(cmd)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail("停止命令执行失败（exit=${res.exitCode}）",
                detail = buildDetail(cmd, res.stdout, res.stderr))
        }
        val refreshed = listCloneProfiles().find { it.userId == userId }
        return if (refreshed?.running == false) OpResult.ok("已停止「$name」（User $userId）")
        else OpResult.fail("命令已执行，但 User $userId 仍在运行",
            detail = buildDetail(cmd, res.stdout, res.stderr))
    }

    suspend fun createCloneProfile(name: String): OpResult {
        val safeName = name.trim().replace("\"", "")
        if (safeName.isEmpty()) return OpResult.fail("分身名称不能为空")
        val beforeIds = listCloneProfiles().map { it.userId }.toSet()
        val cmd = "pm create-user --profileOf 0 --user-type ${CloneProfile.CLONE_TYPE} \"$safeName\""
        val res = shell.execute(cmd, 30_000)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail("创建命令执行失败（exit=${res.exitCode}）",
                detail = buildDetail(cmd, res.stdout, res.stderr))
        }
        val createdIdFromStdout = Regex("""created user id (\d+)""")
            .find(res.stdout)?.groupValues?.get(1)?.toIntOrNull()
        var after = listCloneProfiles()
        var created = after.filter { it.userId !in beforeIds }
        var waited = 0L
        while (created.isEmpty() && waited < 5_000L) {
            Thread.sleep(500); waited += 500
            after = listCloneProfiles()
            created = after.filter { it.userId !in beforeIds }
        }
        return when {
            created.size == 1 -> OpResult.ok("已创建「${created.first().name}」（User ${created.first().userId}）")
            created.isEmpty() && createdIdFromStdout != null ->
                OpResult.ok("创建命令已确认（User $createdIdFromStdout），系统仍在初始化")
            created.isEmpty() -> OpResult.fail("命令已执行，但未在用户列表中发现新的 CLONE",
                detail = buildDetail(cmd, res.stdout, res.stderr))
            else -> OpResult.fail("命令执行后发现多个新用户",
                detail = buildDetail(cmd, res.stdout, res.stderr))
        }
    }

    suspend fun deleteCloneProfile(clone: CloneProfile): OpResult {
        val userId = clone.userId
        if (userId == CloneProfile.USER_ID_SYSTEM) return OpResult.fail("禁止操作系统用户 0")
        shell.execute("am stop-user -w -f $userId", 15_000)
        Thread.sleep(300)
        val cmd = "pm remove-user $userId"
        val res = shell.execute(cmd, 30_000)
        val stdoutOk = res.stdout.contains("removed user", ignoreCase = true)
        val stderrSaysCouldntRemove = res.stderr.contains("couldn't remove user", ignoreCase = true)

        var still: Boolean
        var waited = 0L
        still = listCloneProfiles().any { it.userId == userId }
        while (still && waited < 5_000L) {
            Thread.sleep(500); waited += 500
            still = listCloneProfiles().any { it.userId == userId }
        }
        return when {
            !still -> OpResult.ok("已删除「${clone.name}」（User $userId）")
            stdoutOk -> OpResult.ok("删除命令已确认，系统仍在清理 User $userId")
            stderrSaysCouldntRemove -> OpResult.ok("删除命令返回错误，但系统通常已标记删除，稍后自动消失")
            else -> OpResult.fail("删除失败（exit=${res.exitCode}），User $userId 仍存在",
                detail = buildDetail(cmd, res.stdout, res.stderr))
        }
    }

    suspend fun renameCloneProfile(clone: CloneProfile, newName: String): OpResult {
        val userId = clone.userId
        if (userId == CloneProfile.USER_ID_SYSTEM) return OpResult.fail("禁止重命名系统用户 0")
        val safeName = newName.trim().replace("\"", "")
        if (safeName.isEmpty()) return OpResult.fail("名称不能为空")
        val cmd = "pm rename-user $userId \"$safeName\""
        val res = shell.execute(cmd, 10_000)
        if (!res.success && res.exitCode != 0) {
            return OpResult.fail("重命名失败（exit=${res.exitCode}）",
                detail = buildDetail(cmd, res.stdout, res.stderr))
        }
        return OpResult.ok("已重命名为「$safeName」")
    }

    private fun buildDetail(command: String, stdout: String, stderr: String): String =
        "命令: $command\n[stdout]\n$stdout\n[stderr]\n$stderr".trim()

    suspend fun detectCapabilities(): DeviceCapabilities {
        val root = checkRoot()
        val cmdUserHelpPrimary = shell.execute("cmd user help", 10_000)
        val cmdUserHelp = if (cmdUserHelpPrimary.success && cmdUserHelpPrimary.stdout.isNotBlank())
            cmdUserHelpPrimary.stdout else shell.execute("cmd user", 10_000).stdout
        val cmdPackageHelp = shell.execute("cmd package help", 10_000)
        val pmHelp = shell.execute("pm help", 10_000)
        val amHelp = shell.execute("am help", 10_000)
        val dumpsys = shell.execute("dumpsys user", 15_000)

        val cloneTypeSupported = dumpsys.stdout.contains(CloneProfile.CLONE_TYPE)
        val limit = if (dumpsys.success) parser.parseCloneLimit(dumpsys.stdout) else null

        val userDetector = UserCommandDetector()
        val pkgDetector = PackageCommandDetector()
        val amDetector = ActivityCommandDetector()
        val capabilities = listOf(
            userDetector.detectStartUser(cmdUserHelp, amHelp.stdout),
            userDetector.detectStopUser(cmdUserHelp, amHelp.stdout),
            userDetector.detectUserStateQuery(cmdUserHelp, amHelp.stdout),
            userDetector.detectIsUserStopped(amHelp.stdout),
            userDetector.detectSwitchUser(amHelp.stdout),
            userDetector.detectUnlockUser(amHelp.stdout),
            pkgDetector.detectListPackages(pmHelp.stdout),
            pkgDetector.detectInstallExisting(pmHelp.stdout),
            pkgDetector.detectUninstall(pmHelp.stdout),
            pkgDetector.detectClear(pmHelp.stdout),
            pkgDetector.detectCreateUser(pmHelp.stdout),
            pkgDetector.detectRemoveUser(pmHelp.stdout),
            pkgDetector.detectSetUserName(pmHelp.stdout),
            amDetector.detectStart(amHelp.stdout)
        )
        val failures = mutableMapOf<String, String>()
        listOf("cmd package help" to cmdPackageHelp, "pm help" to pmHelp,
            "am help" to amHelp, "dumpsys user" to dumpsys).forEach { (name, r) ->
            if (!r.success && r.exitCode != 0) failures[name] = "exit=${r.exitCode} ${r.stderr.trim()}"
        }
        return DeviceCapabilities(
            rootAvailable = root.available, rootOutput = root.output,
            cloneTypeSupported = cloneTypeSupported, cloneLimit = limit,
            capabilities = capabilities, helpFailures = failures
        )
    }
}
