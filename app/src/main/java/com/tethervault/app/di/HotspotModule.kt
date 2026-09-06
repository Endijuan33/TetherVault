package com.tethervault.app.di

import com.tethervault.app.data.hotspot.HotspotManagerImpl
import com.tethervault.app.domain.hotspot.HotspotManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HotspotModule {

    @Binds
    @Singleton
    abstract fun bindHotspotManager(impl: HotspotManagerImpl): HotspotManager
}
