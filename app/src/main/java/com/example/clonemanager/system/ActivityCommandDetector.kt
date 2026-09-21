package com.example.clonemanager.system

class ActivityCommandDetector {
    fun detectStart(amHelp: String): CommandCapability {
        val detected = amHelp.contains("start") && amHelp.contains("--user")
        return CommandCapability(
            name = "am start --user", detected = detected,
            commandLine = if (detected) "am start --user <USER_ID> <INTENT>" else null,
            note = if (detected) "已确认" else "未识别", source = "am help"
        )
    }
}
