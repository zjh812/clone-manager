package com.example.clonemanager.system

class UserCommandDetector {
    fun detectStartUser(cmdUserHelp: String, amHelp: String): CommandCapability {
        val found = amHelp.contains("start-user") || cmdUserHelp.contains("start-user")
        return CommandCapability("am start-user", found, "am start-user -w <USER_ID>", null, "am help")
    }
    fun detectStopUser(cmdUserHelp: String, amHelp: String): CommandCapability {
        val found = amHelp.contains("stop-user") || cmdUserHelp.contains("stop-user")
        return CommandCapability("am stop-user", found, "am stop-user -w -f <USER_ID>", null, "am help")
    }
    fun detectUserStateQuery(cmdUserHelp: String, amHelp: String): CommandCapability {
        val found = amHelp.contains("get-started-user-state")
        return CommandCapability("am get-started-user-state", found, "am get-started-user-state <USER_ID>", null, "am help")
    }
    fun detectIsUserStopped(amHelp: String): CommandCapability = CommandCapability("am is-user-stopped", amHelp.contains("is-user-stopped"), "am is-user-stopped <USER_ID>", null, "am help")
    fun detectSwitchUser(amHelp: String): CommandCapability = CommandCapability("am switch-user", amHelp.contains("switch-user"), "am switch-user <USER_ID>", null, "am help")
    fun detectUnlockUser(amHelp: String): CommandCapability = CommandCapability("am unlock-user", amHelp.contains("unlock-user"), "am unlock-user <USER_ID>", null, "am help")
}
