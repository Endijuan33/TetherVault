package com.tethervault.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tethervault.app.data.local.dao.AccessLogDao
import com.tethervault.app.data.local.dao.ConnectedDeviceDao
import com.tethervault.app.data.local.dao.VoucherDao
import com.tethervault.app.data.local.entity.AccessLogEntity
import com.tethervault.app.data.local.entity.ConnectedDeviceEntity
import com.tethervault.app.data.local.entity.VoucherEntity

@Database(
    entities = [
        VoucherEntity::class,
        ConnectedDeviceEntity::class,
        AccessLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TetherVaultDatabase : RoomDatabase() {

    abstract fun voucherDao(): VoucherDao

    abstract fun connectedDeviceDao(): ConnectedDeviceDao

    abstract fun accessLogDao(): AccessLogDao

    companion object {
        const val DATABASE_NAME = "tethervault.db"
    }
}
