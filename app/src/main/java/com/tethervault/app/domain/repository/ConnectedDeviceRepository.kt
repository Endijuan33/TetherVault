package com.tethervault.app.domain.repository

import com.tethervault.app.domain.model.ConnectedDevice
import kotlinx.coroutines.flow.Flow

interface ConnectedDeviceRepository {

    fun observeAll(): Flow<List<ConnectedDevice>>

    suspend fun findByMacAddress(macAddress: String): ConnectedDevice?

    suspend fun findByIpAddress(ipAddress: String): ConnectedDevice?

    suspend fun upsert(device: ConnectedDevice)

    suspend fun deleteByMacAddress(macAddress: String)

    suspend fun deleteAll()
}
