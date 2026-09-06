package com.tethervault.app.di

import com.tethervault.app.data.vpn.LocalProxyServerImpl
import com.tethervault.app.data.vpn.VpnManagerImpl
import com.tethervault.app.domain.vpn.LocalProxyServer
import com.tethervault.app.domain.vpn.VpnManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {

    @Binds
    @Singleton
    abstract fun bindVpnManager(impl: VpnManagerImpl): VpnManager

    @Binds
    @Singleton
    abstract fun bindLocalProxyServer(impl: LocalProxyServerImpl): LocalProxyServer
}
