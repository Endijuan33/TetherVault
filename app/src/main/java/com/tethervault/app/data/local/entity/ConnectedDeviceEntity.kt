package com.tethervault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connected_devices")
data class ConnectedDeviceEntity(
    @PrimaryKey @ColumnInfo(name = "mac_address") val macAddress: String,
    @ColumnInfo(name = "ip_address") val ipAddress: String,
    @ColumnInfo(name = "device_name") val deviceName: String,
    @ColumnInfo(name = "is_authenticated") val isAuthenticated: Boolean = false,
    @ColumnInfo(name = "last_seen") val lastSeen: Long,
    @ColumnInfo(name = "total_bytes_up") val totalBytesUp: Long = 0L,
    @ColumnInfo(name = "total_bytes_down") val totalBytesDown: Long = 0L
)
