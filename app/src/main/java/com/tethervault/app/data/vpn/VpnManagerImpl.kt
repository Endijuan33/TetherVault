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
            val started = tun2SocksAdapter.start(
                fd = tun.fd,
                mtu = Constants.VPN_MTU,
                ipv4 = Constants.VPN_ADDRESS,
                ipv6 = "",
                socks5Addr = Constants.PROXY_HOST,
                socks5Port = Constants.PROXY_PORT
            )
            if (started) {
                Log.i(TAG, "tun2socks started on fd ${tun.fd}")
            } else {
                Log.w(
                    TAG,
                    "tun2socks did not start; TUN traffic is not routed. " +
                        "Run ./setup-native.sh to install the native library."
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

    private companion object {
        const val TAG = "TetherVaultVpn"
    }
}
