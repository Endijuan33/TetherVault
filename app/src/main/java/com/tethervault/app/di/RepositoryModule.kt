package com.tethervault.app.di

import com.tethervault.app.data.repository.AccessLogRepositoryImpl
import com.tethervault.app.data.repository.ConnectedDeviceRepositoryImpl
import com.tethervault.app.data.repository.VoucherRepositoryImpl
import com.tethervault.app.data.settings.SettingsRepositoryImpl
import com.tethervault.app.domain.repository.AccessLogRepository
import com.tethervault.app.domain.repository.ConnectedDeviceRepository
import com.tethervault.app.domain.repository.SettingsRepository
import com.tethervault.app.domain.repository.VoucherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVoucherRepository(impl: VoucherRepositoryImpl): VoucherRepository

    @Binds
    @Singleton
    abstract fun bindConnectedDeviceRepository(impl: ConnectedDeviceRepositoryImpl): ConnectedDeviceRepository

    @Binds
    @Singleton
    abstract fun bindAccessLogRepository(impl: AccessLogRepositoryImpl): AccessLogRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
