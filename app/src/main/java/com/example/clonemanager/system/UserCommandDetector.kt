package com.example.clonemanager.system

class UserCommandDetector {
    fun detectStartUser(cmdUserHelp: String, amHelp: String): CommandCapability {
        val inAm = amHelp.contains("start-user")
        val inCmd = cmdUserHelp.contains("start-user")
        val detected = inAm || inCmd
        val source = if (inAm) "am help" else if (inCmd) "cmd user" else "am help / cmd user"
        return CommandCapability("start-user", detected,
            if (detected) (if (inAm) "am start-user" else "cmd user start-user") + " -w <USER_ID>" else null,
            if (detected) "已确认 start-user（$source）" else "未识别", source)
    }
    fun detectStopUser(cmdUserHelp: String, amHelp: String): CommandCapability {
        val inAm = amHelp.contains("stop-user")
        val inCmd = cmdUserHelp.contains("stop-user")
        val detected = inAm || inCmd
        val source = if (inAm) "am help" else if (inCmd) "cmd user" else "am help / cmd user"
        return CommandCapability("stop-user", detected,
            if (detected) (if (inAm) "am stop-user" else "cmd user stop-user") + " -w -f <USER_ID>" else null,
            if (detected) "已确认 stop-user（$source，CLONE 必须 -f）" else "未识别", source)
    }
    fun detectUserStateQuery(cmdUserHelp: String, amHelp: String): CommandCapability {
        val inAm = amHelp.contains("get-started-user-state")
        val inCmd = cmdUserHelp.contains("get-started-user-state")
        val detected = inAm || inCmd
        val source = if (inAm) "am help" else if (inCmd) "cmd user" else null
        return CommandCapability("get-started-user-state", detected,
            if (detected) (if (inAm) "am get-started-user-state" else "cmd user get-started-user-state") + " <USER_ID>" else null,
            if (detected) "已确认" else "未识别", source)
    }
    fun detectIsUserStopped(amHelp: String) = CommandCapability("is-user-stopped",
        amHelp.contains("is-user-stopped"),
        if (amHelp.contains("is-user-stopped")) "am is-user-stopped <USER_ID>" else null,
        if (amHelp.contains("is-user-stopped")) "已确认" else "未识别",
        if (amHelp.contains("is-user-stopped")) "am help" else null)
    fun detectSwitchUser(amHelp: String) = CommandCapability("switch-user",
        amHelp.contains("switch-user"),
        if (amHelp.contains("switch-user")) "am switch-user <USER_ID>" else null,
        if (amHelp.contains("switch-user")) "已确认" else "未识别",
        if (amHelp.contains("switch-user")) "am help" else null)
    fun detectUnlockUser(amHelp: String) = CommandCapability("unlock-user",
        amHelp.contains("unlock-user"),
        if (amHelp.contains("unlock-user")) "am unlock-user <USER_ID>" else null,
        if (amHelp.contains("unlock-user")) "已确认" else "未识别",
        if (amHelp.contains("unlock-user")) "am help" else null)
}
