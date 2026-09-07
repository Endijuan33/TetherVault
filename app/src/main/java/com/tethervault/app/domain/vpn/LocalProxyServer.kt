package com.tethervault.app.domain.vpn

interface LocalProxyServer {

    /** SOCKS5 port tunneled traffic is forwarded to. */
    val port: Int

    /** Plain-HTTP port the captive portal login page is served on. */
    val portalPort: Int

    fun start()

    fun stop()
}
