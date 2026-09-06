package com.tethervault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tethervault.app.data.local.entity.AccessLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessLogDao {

    @Query("SELECT * FROM access_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AccessLogEntity>>

    @Insert
    suspend fun insert(log: AccessLogEntity): Long

    @Query("DELETE FROM access_logs")
    suspend fun deleteAll()
}
