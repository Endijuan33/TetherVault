package com.tethervault.app.domain.repository

import com.tethervault.app.domain.model.AccessLog
import kotlinx.coroutines.flow.Flow

interface AccessLogRepository {

    fun observeRecent(limit: Int): Flow<List<AccessLog>>

    suspend fun log(deviceMac: String, action: String, details: String? = null)

    suspend fun deleteAll()
}
