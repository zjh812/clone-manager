package com.example.clonemanager.system

class ActivityCommandDetector {
    fun detectStart(amHelp: String) = CommandCapability("am start --user",
        amHelp.contains("start") && amHelp.contains("--user"),
        if (amHelp.contains("start") && amHelp.contains("--user")) "am start --user <USER_ID> <INTENT>" else null,
        if (amHelp.contains("start") && amHelp.contains("--user")) "已确认" else "未识别", "am help")
}
