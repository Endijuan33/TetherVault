package com.tethervault.app.domain.model

data class Voucher(
    val id: Long = 0L,
    val code: String,
    val createdAt: Long,
    val expiresAt: Long,
    val isUsed: Boolean = false,
    val usedByMac: String? = null,
    val usedByIp: String? = null,
    val maxDevices: Int = 1
)
