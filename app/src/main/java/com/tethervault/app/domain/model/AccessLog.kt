package com.tethervault.app.domain.model

data class AccessLog(
    val id: Long = 0L,
    val timestamp: Long,
    val deviceMac: String,
    val action: String,
    val details: String? = null
)
