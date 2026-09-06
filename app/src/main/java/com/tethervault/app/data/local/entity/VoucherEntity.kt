package com.tethervault.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vouchers",
    indices = [Index(value = ["code"], unique = true)]
)
data class VoucherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "is_used") val isUsed: Boolean = false,
    @ColumnInfo(name = "used_by_mac") val usedByMac: String? = null,
    @ColumnInfo(name = "used_by_ip") val usedByIp: String? = null,
    @ColumnInfo(name = "max_devices") val maxDevices: Int = 1
)
