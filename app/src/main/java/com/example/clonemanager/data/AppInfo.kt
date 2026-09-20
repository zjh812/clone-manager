package com.example.clonemanager.data

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long?,
    val enabled: Boolean,
    val installed: Boolean
)
