package com.example.clonemanager.repository

data class OpResult(
    val success: Boolean,
    val message: String,
    val detail: String? = null
) {
    companion object {
        fun ok(message: String) = OpResult(true, message)
        fun fail(message: String, detail: String? = null) = OpResult(false, message, detail)
    }
}
