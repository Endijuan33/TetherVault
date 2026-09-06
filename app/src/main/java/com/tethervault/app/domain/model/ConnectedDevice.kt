package com.tethervault.app.domain.model

data class ConnectedDevice(
    val macAddress: String,
    val ipAddress: String,
    val deviceName: String,
    val isAuthenticated: Boolean = false,
    val lastSeen: Long,
    val totalBytesUp: Long = 0L,
    val totalBytesDown: Long = 0L
)
