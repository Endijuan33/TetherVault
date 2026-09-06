package com.tethervault.app.di

import android.content.Context
import androidx.room.Room
import com.tethervault.app.data.local.TetherVaultDatabase
import com.tethervault.app.data.local.dao.AccessLogDao
import com.tethervault.app.data.local.dao.ConnectedDeviceDao
import com.tethervault.app.data.local.dao.VoucherDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TetherVaultDatabase =
        Room.databaseBuilder(
            context,
            TetherVaultDatabase::class.java,
            TetherVaultDatabase.DATABASE_NAME
        ).build()

    @Provides
    fun provideVoucherDao(database: TetherVaultDatabase): VoucherDao =
        database.voucherDao()

    @Provides
    fun provideConnectedDeviceDao(database: TetherVaultDatabase): ConnectedDeviceDao =
        database.connectedDeviceDao()

    @Provides
    fun provideAccessLogDao(database: TetherVaultDatabase): AccessLogDao =
        database.accessLogDao()
}
