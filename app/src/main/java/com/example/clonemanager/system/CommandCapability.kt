package com.example.clonemanager.system

data class CommandCapability(
    val name: String,
    val detected: Boolean,
    val commandLine: String? = null,
    val note: String? = null,
    val source: String? = null
) {
    val displayText: String get() = if (detected) "已识别" else "未识别"
}
