package com.tethervault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "access_logs")
data class AccessLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "device_mac") val deviceMac: String,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "details") val details: String? = null
)
