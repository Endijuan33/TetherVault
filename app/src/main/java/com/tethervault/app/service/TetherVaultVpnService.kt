package com.tethervault.app.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.tethervault.app.domain.model.VpnState
import com.tethervault.app.domain.vpn.SocketProtector
import com.tethervault.app.domain.vpn.VpnManager
import com.tethervault.app.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

// SocketProtector.protect(socketFd) is fulfilled by the inherited
// VpnService.protect(int) implementation, so upstream sockets created by the
// proxy can bypass the TUN interface.
@AndroidEntryPoint
class TetherVaultVpnService : VpnService(), SocketProtector {

    @Inject lateinit var vpnManager: VpnManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_SERVICE -> serviceScope.launch {
                vpnManager.updateState(VpnState.Starting)
                val tun = establishTunnel()
                if (tun == null) {
                    vpnManager.updateState(
                        VpnState.Error("Failed to establish VPN tunnel (permission revoked?)")
                    )
                    stopSelf()
                } else {
                    vpnManager.startTunnel(tun, this@TetherVaultVpnService)
                }
            }
            Constants.ACTION_STOP_SERVICE -> serviceScope.launch {
                vpnManager.stopTunnel()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        vpnManager.stopTunnel()
        super.onDestroy()
    }

    private fun establishTunnel(): ParcelFileDescriptor? =
        Builder()
            .addAddress(Constants.VPN_ADDRESS, Constants.VPN_ADDRESS_PREFIX_LENGTH)
            .addRoute(Constants.VPN_ROUTE, Constants.VPN_ROUTE_PREFIX_LENGTH)
            .addDnsServer(Constants.VPN_DNS_SERVER)
            .setMtu(Constants.VPN_MTU)
            .setSession(Constants.VPN_SESSION_NAME)
            .establish()
}
