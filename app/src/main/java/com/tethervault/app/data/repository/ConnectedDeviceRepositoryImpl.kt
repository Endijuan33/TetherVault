package com.tethervault.app.data.repository

import com.tethervault.app.data.local.dao.ConnectedDeviceDao
import com.tethervault.app.data.local.entity.ConnectedDeviceEntity
import com.tethervault.app.domain.model.ConnectedDevice
import com.tethervault.app.domain.repository.ConnectedDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectedDeviceRepositoryImpl @Inject constructor(
    private val connectedDeviceDao: ConnectedDeviceDao
) : ConnectedDeviceRepository {

    override fun observeAll(): Flow<List<ConnectedDevice>> =
        connectedDeviceDao.observeAll().map { entities -> entities.map(ConnectedDeviceEntity::toDomain) }

    override suspend fun findByMacAddress(macAddress: String): ConnectedDevice? =
        connectedDeviceDao.findByMacAddress(macAddress)?.toDomain()

    override suspend fun findByIpAddress(ipAddress: String): ConnectedDevice? =
        connectedDeviceDao.findByIpAddress(ipAddress)?.toDomain()

    override suspend fun upsert(device: ConnectedDevice) =
        connectedDeviceDao.upsert(device.toEntity())

    override suspend fun deleteByMacAddress(macAddress: String) =
        connectedDeviceDao.deleteByMacAddress(macAddress)

    override suspend fun deleteAll() =
        connectedDeviceDao.deleteAll()
}

private fun ConnectedDeviceEntity.toDomain() = ConnectedDevice(
    macAddress = macAddress,
    ipAddress = ipAddress,
    deviceName = deviceName,
    isAuthenticated = isAuthenticated,
    lastSeen = lastSeen,
    totalBytesUp = totalBytesUp,
    totalBytesDown = totalBytesDown
)

private fun ConnectedDevice.toEntity() = ConnectedDeviceEntity(
    macAddress = macAddress,
    ipAddress = ipAddress,
    deviceName = deviceName,
    isAuthenticated = isAuthenticated,
    lastSeen = lastSeen,
    totalBytesUp = totalBytesUp,
    totalBytesDown = totalBytesDown
)
