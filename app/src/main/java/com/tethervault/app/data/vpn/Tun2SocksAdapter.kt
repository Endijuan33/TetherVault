package com.tethervault.app.data.vpn

import android.util.Log
import hev.socks5.tunnel.Tun2Socks
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Tun2SocksAdapter @Inject constructor() {

    fun start(
        fd: Int,
        mtu: Int,
        ipv4: String,
        ipv6: String,
        socks5Addr: String,
        socks5Port: Int
    ): Boolean = try {
        Tun2Socks.Start(fd, mtu, ipv4, ipv6, socks5Addr, socks5Port)
        true
    } catch (e: LinkageError) {
        // Covers every native-binding failure mode: missing library
        // (UnsatisfiedLinkError), failed class init on first use
        // (ExceptionInInitializerError), and reuse after a failed init
        // (NoClassDefFoundError). The wrapper's own catch only handles
        // Exception, so these escape it as Errors.
        Log.w(TAG, "tun2socks start skipped: ${e.cause?.message ?: e.message}")
        false
    }

    fun stop() {
        try {
            Tun2Socks.Stop()
        } catch (e: LinkageError) {
            Log.w(TAG, "tun2socks stop skipped: ${e.cause?.message ?: e.message}")
        }
    }

    private companion object {
        const val TAG = "Tun2Socks"
    }
}
