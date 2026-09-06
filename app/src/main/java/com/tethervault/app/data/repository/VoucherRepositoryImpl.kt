package com.tethervault.app.data.repository

import com.tethervault.app.data.local.dao.VoucherDao
import com.tethervault.app.data.local.entity.VoucherEntity
import com.tethervault.app.domain.model.Voucher
import com.tethervault.app.domain.repository.VoucherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val voucherDao: VoucherDao
) : VoucherRepository {

    override fun observeAll(): Flow<List<Voucher>> =
        voucherDao.observeAll().map { entities -> entities.map(VoucherEntity::toDomain) }

    override fun observeAvailable(now: Long): Flow<List<Voucher>> =
        voucherDao.observeAvailable(now).map { entities -> entities.map(VoucherEntity::toDomain) }

    override suspend fun findByCode(code: String): Voucher? =
        voucherDao.findByCode(code)?.toDomain()

    override suspend fun upsert(voucher: Voucher) {
        voucherDao.upsert(voucher.toEntity())
    }

    override suspend fun markUsed(id: Long, usedByMac: String, usedByIp: String) =
        voucherDao.markUsed(id, usedByMac, usedByIp)

    override suspend fun deleteById(id: Long) =
        voucherDao.deleteById(id)

    override suspend fun deleteAll() =
        voucherDao.deleteAll()
}

private fun VoucherEntity.toDomain() = Voucher(
    id = id,
    code = code,
    createdAt = createdAt,
    expiresAt = expiresAt,
    isUsed = isUsed,
    usedByMac = usedByMac,
    usedByIp = usedByIp,
    maxDevices = maxDevices
)

private fun Voucher.toEntity() = VoucherEntity(
    id = id,
    code = code,
    createdAt = createdAt,
    expiresAt = expiresAt,
    isUsed = isUsed,
    usedByMac = usedByMac,
    usedByIp = usedByIp,
    maxDevices = maxDevices
)
