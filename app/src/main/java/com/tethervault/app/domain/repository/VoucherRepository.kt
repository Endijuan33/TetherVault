package com.tethervault.app.domain.repository

import com.tethervault.app.domain.model.Voucher
import kotlinx.coroutines.flow.Flow

interface VoucherRepository {

    fun observeAll(): Flow<List<Voucher>>

    fun observeAvailable(now: Long): Flow<List<Voucher>>

    suspend fun findByCode(code: String): Voucher?

    suspend fun upsert(voucher: Voucher)

    suspend fun markUsed(id: Long, usedByMac: String, usedByIp: String)

    suspend fun deleteById(id: Long)

    suspend fun deleteAll()
}
