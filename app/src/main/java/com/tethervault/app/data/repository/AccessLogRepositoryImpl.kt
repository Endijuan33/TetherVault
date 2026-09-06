package com.tethervault.app.data.repository

import com.tethervault.app.data.local.dao.AccessLogDao
import com.tethervault.app.data.local.entity.AccessLogEntity
import com.tethervault.app.domain.model.AccessLog
import com.tethervault.app.domain.repository.AccessLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessLogRepositoryImpl @Inject constructor(
    private val accessLogDao: AccessLogDao
) : AccessLogRepository {

    override fun observeRecent(limit: Int): Flow<List<AccessLog>> =
        accessLogDao.observeRecent(limit).map { entities -> entities.map(AccessLogEntity::toDomain) }

    override suspend fun log(deviceMac: String, action: String, details: String?) {
        accessLogDao.insert(
            AccessLogEntity(
                timestamp = System.currentTimeMillis(),
                deviceMac = deviceMac,
                action = action,
                details = details
            )
        )
    }

    override suspend fun deleteAll() =
        accessLogDao.deleteAll()
}

private fun AccessLogEntity.toDomain() = AccessLog(
    id = id,
    timestamp = timestamp,
    deviceMac = deviceMac,
    action = action,
    details = details
)
