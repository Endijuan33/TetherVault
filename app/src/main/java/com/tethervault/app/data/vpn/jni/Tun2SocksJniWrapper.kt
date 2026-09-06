package com.tethervault.app.data.vpn.jni

import android.util.Log

// The native library (libtun2socks.so) is expected in app/src/main/jniLibs/<abi>/.
// Until it is added, calls to the external functions throw UnsatisfiedLinkError,
// which Tun2SocksAdapter handles gracefully.
object Tun2SocksJniWrapper {

    private const val TAG = "Tun2Socks"

    init {
        try {
            System.loadLibrary("tun2socks")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library not loaded yet")
        }
    }

    external fun start(fd: Int, vpnIp: Int, vpnPrefix: Int, proxyIp: String, proxyPort: Int): Int

    external fun stop()
}
