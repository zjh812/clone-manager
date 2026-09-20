package com.example.clonemanager.system

class PackageCommandDetector {
    fun detectInstallExisting(pmHelp: String) = CommandCapability("install-existing",
        pmHelp.contains("install-existing") && pmHelp.contains("--user"),
        if (pmHelp.contains("install-existing")) "pm install-existing --user <USER_ID> <PACKAGE>" else null,
        "pm help", "pm help")
    fun detectUninstall(pmHelp: String) = CommandCapability("uninstall",
        pmHelp.contains("uninstall") && pmHelp.contains("--user"),
        if (pmHelp.contains("uninstall")) "pm uninstall --user <USER_ID> <PACKAGE>" else null,
        "pm help", "pm help")
    fun detectClear(pmHelp: String) = CommandCapability("clear",
        pmHelp.contains("clear") && pmHelp.contains("--user"),
        if (pmHelp.contains("clear")) "pm clear --user <USER_ID> <PACKAGE>" else null,
        "pm help", "pm help")
    fun detectListPackages(pmHelp: String) = CommandCapability("list packages",
        pmHelp.contains("list") && pmHelp.contains("packages"),
        "pm list packages --user <USER_ID>", "pm help", "pm help")
    fun detectCreateUser(pmHelp: String): CommandCapability {
        val detected = pmHelp.contains("create-user") && pmHelp.contains("--user-type") && pmHelp.contains("--profileOf")
        return CommandCapability("pm create-user", detected,
            if (detected) "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE <name>" else null,
            if (detected) "已确认" else "未识别", "pm help")
    }
    fun detectRemoveUser(pmHelp: String) = CommandCapability("pm remove-user",
        pmHelp.contains("remove-user"),
        if (pmHelp.contains("remove-user")) "pm remove-user <USER_ID>" else null,
        if (pmHelp.contains("remove-user")) "已确认" else "未识别", "pm help")
    fun detectSetUserName(pmHelp: String) = CommandCapability("pm rename-user",
        pmHelp.contains("rename-user"),
        if (pmHelp.contains("rename-user")) "pm rename-user <USER_ID> <NEW_NAME>" else null,
        if (pmHelp.contains("rename-user")) "已确认" else "未识别", "pm help")
}
