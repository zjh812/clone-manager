package com.example.clonemanager.data

data class CloneProfile(
    val userId: Int,
    val name: String,
    val userType: String,
    val flags: Int,
    val running: Boolean,
    val unlocked: Boolean,
    val appCount: Int
) {
    val isClone: Boolean
        get() = userType == CLONE_TYPE || (flags and FLAG_CLONE) != 0

    companion object {
        const val CLONE_TYPE = "android.os.usertype.profile.CLONE"
        const val FLAG_CLONE = 0x1000
        const val USER_ID_SYSTEM = 0
    }
}
