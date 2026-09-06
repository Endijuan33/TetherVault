package com.tethervault.app.data.vpn

import android.content.Context
import android.util.Log
import com.tethervault.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import hev.socks5.tunnel.Tun2Socks
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Tun2SocksAdapter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun start(fd: Int, mtu: Int, ipv4: String, socks5Port: Int): Boolean {
        val yaml = """
            tunnel:
              mtu: $mtu
              ipv4: '$ipv4'
            socks5:
              address: '${Constants.PROXY_HOST}'
              port: $socks5Port
        """.trimIndent()

        val configFile = File(context.filesDir, CONFIG_FILE_NAME)
        configFile.writeText(yaml)

        return try {
            Tun2Socks.TProxyStartService(configFile.absolutePath, fd)
        } catch (e: LinkageError) {
            // Covers every native-binding failure mode: missing library
            // (UnsatisfiedLinkError), failed class init on first use
            // (ExceptionInInitializerError), and reuse after a failed init
            // (NoClassDefFoundError).
            Log.w(TAG, "tun2socks start skipped: ${e.cause?.message ?: e.message}")
            false
        }
    }

    fun stop() {
        try {
            Tun2Socks.TProxyStopService()
        } catch (e: LinkageError) {
            Log.w(TAG, "tun2socks stop skipped: ${e.cause?.message ?: e.message}")
        }
    }

    private companion object {
        const val TAG = "Tun2Socks"
        const val CONFIG_FILE_NAME = "tun2socks_config.yaml"
    }
}
