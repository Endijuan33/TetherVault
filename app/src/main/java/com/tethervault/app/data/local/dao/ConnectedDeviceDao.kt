package com.tethervault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tethervault.app.data.local.entity.ConnectedDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectedDeviceDao {

    @Query("SELECT * FROM connected_devices ORDER BY last_seen DESC")
    fun observeAll(): Flow<List<ConnectedDeviceEntity>>

    @Query("SELECT * FROM connected_devices WHERE mac_address = :macAddress LIMIT 1")
    suspend fun findByMacAddress(macAddress: String): ConnectedDeviceEntity?

    @Query("SELECT * FROM connected_devices WHERE ip_address = :ipAddress LIMIT 1")
    suspend fun findByIpAddress(ipAddress: String): ConnectedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: ConnectedDeviceEntity)

    @Query("DELETE FROM connected_devices WHERE mac_address = :macAddress")
    suspend fun deleteByMacAddress(macAddress: String)

    @Query("DELETE FROM connected_devices")
    suspend fun deleteAll()
}
