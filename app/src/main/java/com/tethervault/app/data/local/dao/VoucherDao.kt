package com.tethervault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tethervault.app.data.local.entity.VoucherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoucherDao {

    @Query("SELECT * FROM vouchers ORDER BY created_at DESC")
    fun observeAll(): Flow<List<VoucherEntity>>

    @Query("SELECT * FROM vouchers WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): VoucherEntity?

    @Query("SELECT * FROM vouchers WHERE is_used = 0 AND expires_at > :now ORDER BY created_at DESC")
    fun observeAvailable(now: Long): Flow<List<VoucherEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(voucher: VoucherEntity): Long

    @Update
    suspend fun update(voucher: VoucherEntity)

    @Query("UPDATE vouchers SET is_used = 1, used_by_mac = :usedByMac, used_by_ip = :usedByIp WHERE id = :id")
    suspend fun markUsed(id: Long, usedByMac: String, usedByIp: String)

    @Query("DELETE FROM vouchers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM vouchers")
    suspend fun deleteAll()
}
