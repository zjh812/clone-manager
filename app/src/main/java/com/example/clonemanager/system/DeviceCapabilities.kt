package com.example.clonemanager.system

data class DeviceCapabilities(
    val rootAvailable: Boolean,
    val rootOutput: String,
    val cloneTypeSupported: Boolean,
    val cloneLimit: Int?,
    val capabilities: List<CommandCapability>,
    val helpFailures: Map<String, String>
) {
    fun find(name: String): CommandCapability? = capabilities.firstOrNull { it.name == name }
}
