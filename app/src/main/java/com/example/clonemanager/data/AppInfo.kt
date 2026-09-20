package com.example.clonemanager.data

/**
 * 一个已安装应用。
 *
 * - PackageManager 负责：名称 / 图标 / 版本 / packageName / 是否系统应用
 * - Root shell 负责：User 级安装状态 / 卸载 / 清除数据
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long?,
    val enabled: Boolean,
    val installed: Boolean,
    val isSystemApp: Boolean = false
)
