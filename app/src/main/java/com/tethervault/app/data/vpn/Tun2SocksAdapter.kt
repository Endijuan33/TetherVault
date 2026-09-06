package com.tethervault.app.data.vpn

import android.util.Log
import com.tethervault.app.data.vpn.jni.Tun2SocksJniWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Tun2SocksAdapter @Inject constructor() {

    fun start(fd: Int, vpnIp: Int, vpnPrefix: Int, proxyIp: String, proxyPort: Int): Int =
        try {
            Tun2SocksJniWrapper.start(fd, vpnIp, vpnPrefix, proxyIp, proxyPort)
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "tun2socks start skipped: native library not available (${e.message})")
            TUN2SOCKS_START_FAILED
        }

    fun stop() {
        try {
            Tun2SocksJniWrapper.stop()
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "tun2socks stop skipped: native library not available (${e.message})")
        }
    }

    private companion object {
        const val TAG = "Tun2Socks"
        const val TUN2SOCKS_START_FAILED = -1
    }
}
