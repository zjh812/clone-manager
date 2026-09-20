package com.example.clonemanager.system

import com.example.clonemanager.data.CloneProfile

class DumpsysParser {

    data class ParsedUser(
        val userId: Int,
        val name: String,
        val flags: Int,
        val stateText: String,
        val userType: String?,
        val running: Boolean,
        val unlocked: Boolean
    ) {
        val isClone: Boolean
            get() = userType == CloneProfile.CLONE_TYPE ||
                (flags and CloneProfile.FLAG_CLONE) != 0
    }

    private data class UserBlock(val userType: String?, val state: String?)

    companion object {
        private val USER_INFO_REGEX =
            Regex("UserInfo\\{(\\d+):(.*?):([0-9a-fA-F]+)\\}(.*)")
        private val USER_SECTION_REGEX = Regex("^\\s*User\\s+(\\d+)\\s*:\\s*$")
        private val KEY_VALUE_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9]*)\\s*:\\s*(.*)$")
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
                val indented = next.firstOrNull()?.isWhitespace() == true
                if (!indented) break
                val trimmed = next.trim()
                if (trimmed.startsWith("mMaxAllowedPerParent")) {
                    return trimmed.substringAfter(":").trim().toIntOrNull()
                }
                j++
            }
            i++
        }
        return null
    }

    fun parseUsers(cmdUserListOutput: String?, dumpsysOutput: String?): List<ParsedUser> {
        val merged = LinkedHashMap<Int, ParsedUser>()
        if (!cmdUserListOutput.isNullOrBlank()) {
            for (u in parseUserInfoLines(cmdUserListOutput)) merged[u.userId] = u
        }
        if (!dumpsysOutput.isNullOrBlank()) {
            val dumpUsers = parseUserInfoLines(dumpsysOutput)
            if (merged.isEmpty()) for (u in dumpUsers) merged[u.userId] = u
            else for (u in dumpUsers) merged.putIfAbsent(u.userId, u)
            for ((id, block) in parseUserBlocks(dumpsysOutput)) {
                val prev = merged[id]
                val state = block.state
                merged[id] = ParsedUser(
                    userId = id,
                    name = prev?.name ?: "User $id",
                    flags = prev?.flags ?: 0,
                    stateText = state ?: prev?.stateText.orEmpty(),
                    userType = block.userType ?: prev?.userType,
                    running = if (state != null) isRunningState(state) else prev?.running ?: false,
                    unlocked = if (state != null) isUnlockedState(state) else prev?.unlocked ?: false
                )
            }
        }
        return merged.values.sortedBy { it.userId }
    }

    private fun parseUserInfoLines(output: String): List<ParsedUser> {
        val users = mutableListOf<ParsedUser>()
        for (line in output.lines()) {
            val m = USER_INFO_REGEX.find(line) ?: continue
            val id = m.groupValues[1].toIntOrNull() ?: continue
            val name = m.groupValues[2].ifBlank { "User $id" }
            val flags = m.groupValues[3].toIntOrNull(16) ?: 0
            val rest = m.groupValues[4].trim()
            users.add(ParsedUser(id, name, flags, rest, null, isRunningText(rest), false))
        }
        return users
    }

    private fun parseUserBlocks(dumpsysOutput: String): Map<Int, UserBlock> {
        val blocks = mutableMapOf<Int, UserBlock>()
        val lines = dumpsysOutput.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val infoMatch = USER_INFO_REGEX.find(line)
            val sectionMatch = USER_SECTION_REGEX.find(line)
            val id = when {
                infoMatch != null -> infoMatch.groupValues[1].toIntOrNull()
                sectionMatch != null -> sectionMatch.groupValues[1].toIntOrNull()
                else -> null
            }
            if (id == null) { i++; continue }
            val headerIndent = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
            var type: String? = null
            var state: String? = null
            var j = i + 1
            while (j < lines.size) {
                val next = lines[j]
                if (next.isBlank()) { j++; break }
                val indent = next.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
                if (indent <= headerIndent) break
                val kv = KEY_VALUE_REGEX.find(next.trim())
                if (kv != null) {
                    when (kv.groupValues[1].lowercase()) {
                        "type" -> if (type == null) type = kv.groupValues[2].trim()
                        "state" -> if (state == null) state = kv.groupValues[2].trim()
                        "isclone" -> if (type == null && kv.groupValues[2].trim().equals("true", true))
                            type = CloneProfile.CLONE_TYPE
                    }
                }
                j++
            }
            blocks[id] = UserBlock(type, state)
            i = j
        }
        return blocks
    }

    private fun isRunningText(rest: String) =
        rest.contains("running", true) || rest.contains("start", true)
    private fun isRunningState(state: String) =
        state.contains("RUNNING", true) || state.contains("BOOTING", true) || state.contains("STARTING", true)
    private fun isUnlockedState(state: String) = state.contains("UNLOCKED", true)
}
