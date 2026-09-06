package com.tethervault.app.data.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import com.tethervault.app.domain.model.VpnState
import com.tethervault.app.domain.vpn.LocalProxyServer
import com.tethervault.app.domain.vpn.SocketProtector
import com.tethervault.app.domain.vpn.VpnManager
import com.tethervault.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnManagerImpl @Inject constructor(
    private val tun2SocksAdapter: Tun2SocksAdapter,
    private val localProxyServer: LocalProxyServer
) : VpnManager {

    private val _vpnState = MutableStateFlow<VpnState>(VpnState.Idle)
    override val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var tunnelJob: Job? = null
    private var currentTun: ParcelFileDescriptor? = null
    private var socketProtector: SocketProtector? = null

    override fun updateState(state: VpnState) {
        _vpnState.value = state
    }

    override fun startTunnel(tun: ParcelFileDescriptor, socketProtector: SocketProtector) {
        stopTunnel()
        currentTun = tun
        this.socketProtector = socketProtector
        _vpnState.value = VpnState.Running

        localProxyServer.start()

        tunnelJob = managerScope.launch {
            val result = tun2SocksAdapter.start(
                fd = tun.fd,
                vpnIp = ipToInt(Constants.VPN_ADDRESS),
                vpnPrefix = Constants.VPN_ADDRESS_PREFIX_LENGTH,
                proxyIp = Constants.PROXY_HOST,
                proxyPort = Constants.PROXY_PORT
            )
            if (result == 0) {
                Log.i(TAG, "tun2socks started on fd ${tun.fd}")
            } else {
                Log.w(
                    TAG,
                    "tun2socks did not start (result=$result); TUN traffic is not routed. " +
                        "Drop libtun2socks.so into app/src/main/jniLibs/<abi>/."
                )
            }
        }
    }

    override fun stopTunnel() {
        tunnelJob?.cancel()
        tunnelJob = null
        tun2SocksAdapter.stop()
        localProxyServer.stop()
        currentTun?.let { tun ->
            currentTun = null
            runCatching { tun.close() }
        }
        socketProtector = null
        if (_vpnState.value !is VpnState.Error) {
            _vpnState.value = VpnState.Idle
        }
    }

    private fun ipToInt(ip: String): Int =
        try {
            InetAddress.getByName(ip).address.fold(0) { acc, byte ->
                (acc shl 8) or (byte.toInt() and 0xFF)
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Failed to parse IP address '$ip'")
            0
        }

    private companion object {
        const val TAG = "TetherVaultVpn"
    }
}
