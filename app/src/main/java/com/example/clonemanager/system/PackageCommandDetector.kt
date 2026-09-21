package com.example.clonemanager.system

class PackageCommandDetector {
    fun detectInstallExisting(pmHelp: String) = CommandCapability("install-existing", pmHelp.contains("install-existing") && pmHelp.contains("--user"), "pm install-existing --user <USER_ID> <PACKAGE>", null, "pm help")
    fun detectUninstall(pmHelp: String) = CommandCapability("uninstall", pmHelp.contains("uninstall") && pmHelp.contains("--user"), "pm uninstall --user <USER_ID> <PACKAGE>", null, "pm help")
    fun detectClear(pmHelp: String) = CommandCapability("clear", pmHelp.contains("clear") && pmHelp.contains("--user"), "pm clear --user <USER_ID> <PACKAGE>", null, "pm help")
    fun detectListPackages(pmHelp: String) = CommandCapability("list packages", pmHelp.contains("list") && pmHelp.contains("packages"), "pm list packages --user <USER_ID>", null, "pm help")
    fun detectCreateUser(pmHelp: String) = CommandCapability("pm create-user", pmHelp.contains("create-user") && pmHelp.contains("--user-type") && pmHelp.contains("--profileOf"), "pm create-user --profileOf 0 --user-type <TYPE> <name>", null, "pm help")
    fun detectRemoveUser(pmHelp: String) = CommandCapability("pm remove-user", pmHelp.contains("remove-user"), "pm remove-user <USER_ID>", null, "pm help")
    fun detectSetUserName(pmHelp: String) = CommandCapability("pm rename-user", pmHelp.contains("rename-user"), "pm rename-user <USER_ID> <NAME>", null, "pm help")
}
