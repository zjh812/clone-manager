package com.example.clonemanager.system

import com.example.clonemanager.data.CloneProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DumpsysParserTest {

    private val parser = DumpsysParser()

    private val cmdUserListSample = """
        Users:
            UserInfo{0:机主:4c13} running
            UserInfo{900:应用分身:1010} running
            UserInfo{901:应用分身:1010} running
            UserInfo{902:应用分身:1010} running
            UserInfo{903:应用分身:1010} running
            UserInfo{904:分身5:1010} running
            UserInfo{906:分身6:1010} running
    """.trimIndent()

    private val dumpsysSample = """
          Users:
            UserInfo{0:机主:4c13} running
            UserInfo{900:应用分身:1010} running

          User types:
            android.os.usertype.full.SYSTEM:
              mEnabled: true
              mMaxAllowedPerParent: 1
            android.os.usertype.profile.CLONE:
              mEnabled: true
              mMaxAllowedPerParent: 10

          User 0:
            state: RUNNING_UNLOCKED
            type: android.os.usertype.full.SYSTEM
            isClone: false

          User 900:
            state: RUNNING_UNLOCKED
            type: android.os.usertype.profile.CLONE
            isClone: true

          User 902:
            state: RUNNING_LOCKED
            type: android.os.usertype.profile.CLONE
            isClone: true
    """.trimIndent()

    @Test
    fun `解析 mMaxAllowedPerParent 为 10`() {
        assertEquals(10, parser.parseCloneLimit(dumpsysSample))
    }

    @Test
    fun `Framework 修改为 30 后自动读取 30`() {
        val modified = dumpsysSample.replace("mMaxAllowedPerParent: 10", "mMaxAllowedPerParent: 30")
        assertEquals(30, parser.parseCloneLimit(modified))
    }

    @Test
    fun `无法解析时返回 null 不写死`() {
        assertNull(parser.parseCloneLimit("no such section"))
    }

    @Test
    fun `识别 CLONE 用户且包含真实 userId`() {
        val users = parser.parseUsers(cmdUserListSample, dumpsysSample)
        val clones = users.filter { it.isClone }
        assertTrue(clones.all { it.userType == CloneProfile.CLONE_TYPE })
        assertTrue(clones.first { it.userId == 900 }.running)
        assertFalse(clones.first { it.userId == 902 }.unlocked)
        val system = users.first { it.userId == 0 }
        assertFalse(system.isClone)
    }

    @Test
    fun `cmd user list 失败时退化为 dumpsys 解析`() {
        val users = parser.parseUsers(null, dumpsysSample)
        assertTrue(users.isNotEmpty())
    }

    @Test
    fun `不存在的用户列表返回空`() {
        assertTrue(parser.parseUsers("", "").isEmpty())
    }
}
