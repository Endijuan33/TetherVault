package com.tethervault.app.domain.vpn

interface LocalProxyServer {

    val port: Int

    fun start()

    fun stop()
}
