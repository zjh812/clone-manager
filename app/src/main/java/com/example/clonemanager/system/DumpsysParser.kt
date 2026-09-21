package com.example.clonemanager.system

import com.example.clonemanager.data.CloneProfile

class DumpsysParser {
    data class ParsedUser(
        val userId: Int, val name: String, val flags: Int, val stateText: String,
        val userType: String?, val running: Boolean, val unlocked: Boolean
    ) {
        val isClone: Boolean get() = userType == CloneProfile.CLONE_TYPE || (flags and CloneProfile.FLAG_CLONE) != 0
    }

    fun parseCloneLimit(dumpsysOutput: String): Int? {
        val lines = dumpsysOutput.lines()
        var i = 0
        while (i < lines.size) {
            if (lines[i].trim() != "${CloneProfile.CLONE_TYPE}:") { i++; continue }
            var j = i + 1
            while (j < lines.size) {
                val next = lines[j]
                if (next.isBlank()) { j++; continue }
                if (next.firstOrNull()?.isWhitespace() != true) break
                val t = next.trim()
                if (t.startsWith("mMaxAllowedPerParent")) return t.substringAfter(":").trim().toIntOrNull()
                j++
            }
            i++
        }
        return null
    }

    fun parseUsers(cmdUserListOutput: String?, dumpsysOutput: String?): List<ParsedUser> {
        val merged = LinkedHashMap<Int, ParsedUser>()
        if (!cmdUserListOutput.isNullOrBlank()) for (u in parseUserInfoLines(cmdUserListOutput)) merged[u.userId] = u
        if (!dumpsysOutput.isNullOrBlank()) {
            for (u in parseUserInfoLines(dumpsysOutput)) merged.putIfAbsent(u.userId, u)
            for ((id, block) in parseUserBlocks(dumpsysOutput)) {
                val prev = merged[id]
                merged[id] = ParsedUser(
                    id, prev?.name ?: "User $id", prev?.flags ?: 0, block.state ?: prev?.stateText.orEmpty(),
                    block.userType ?: prev?.userType,
                    block.state?.let { isRunning(it) } ?: prev?.running ?: false,
                    block.state?.let { it.contains("UNLOCKED", true) } ?: prev?.unlocked ?: false
                )
            }
        }
        return merged.values.sortedBy { it.userId }
    }

    private fun parseUserInfoLines(output: String): List<ParsedUser> {
        val users = mutableListOf<ParsedUser>()
        val re = Regex("UserInfo\\{(\\d+):(.*?):([0-9a-fA-F]+)\\}(.*)")
        for (line in output.lines()) {
            val m = re.find(line) ?: continue
            val id = m.groupValues[1].toIntOrNull() ?: continue
            users.add(ParsedUser(id, m.groupValues[2].ifBlank { "User $id" }, m.groupValues[3].toIntOrNull(16) ?: 0,
                m.groupValues[4].trim(), null, m.groupValues[4].contains("running", true), false))
        }
        return users
    }

    private data class Block(val userType: String?, val state: String?)

    private fun parseUserBlocks(dumpsys: String): Map<Int, Block> {
        val blocks = mutableMapOf<Int, Block>()
        val lines = dumpsys.lines()
        val infoRe = Regex("UserInfo\\{(\\d+):")
        val secRe = Regex("^\\s*User\\s+(\\d+)\\s*:\\s*$")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val id = (infoRe.find(line)?.groupValues?.get(1) ?: secRe.find(line)?.groupValues?.get(1))?.toIntOrNull()
            if (id == null) { i++; continue }
            val hIndent = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
            var type: String? = null; var state: String? = null
            var j = i + 1
            while (j < lines.size) {
                val n = lines[j]
                if (n.isBlank()) { j++; break }
                val ind = n.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
                if (ind <= hIndent) break
                val t = n.trim()
                if (t.startsWith("Type:")) type = t.substringAfter(":").trim()
                if (t.startsWith("State:")) state = t.substringAfter(":").trim()
                j++
            }
            blocks[id] = Block(type, state)
            i = j
        }
        return blocks
    }

    private fun isRunning(s: String): Boolean = s.contains("RUNNING", true) || s.contains("BOOTING", true)
}
