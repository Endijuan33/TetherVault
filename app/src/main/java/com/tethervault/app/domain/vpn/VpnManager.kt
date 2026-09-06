package com.tethervault.app.domain.vpn

import android.os.ParcelFileDescriptor
import com.tethervault.app.domain.model.VpnState
import kotlinx.coroutines.flow.StateFlow

interface VpnManager {

    val vpnState: StateFlow<VpnState>

    fun updateState(state: VpnState)

    fun startTunnel(tun: ParcelFileDescriptor, socketProtector: SocketProtector)

    fun stopTunnel()
}
