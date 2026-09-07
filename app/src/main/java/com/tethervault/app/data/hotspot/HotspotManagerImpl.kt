package com.tethervault.app.data.hotspot

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.tethervault.app.domain.hotspot.HotspotManager
import com.tethervault.app.domain.model.AppSettings
import com.tethervault.app.domain.model.HotspotSecurity
import com.tethervault.app.domain.model.HotspotState
import com.tethervault.app.domain.repository.SettingsRepository
import com.tethervault.app.domain.vpn.LocalProxyServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class HotspotManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val localProxyServer: LocalProxyServer
) : HotspotManager {

    private val _hotspotState = MutableStateFlow<HotspotState>(HotspotState.Idle)
    override val hotspotState: StateFlow<HotspotState> = _hotspotState.asStateFlow()

    private val stateMutex = Mutex()

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var currentChannel: WifiP2pManager.Channel? = null

    override suspend fun startHotspot() {
        stateMutex.withLock {
            val current = _hotspotState.value
            if (current is HotspotState.Starting || current is HotspotState.Running) return

            val manager = wifiP2pManager ?: run {
                _hotspotState.value =
                    HotspotState.Error("Wi-Fi Direct is not supported on this device")
                return
            }

            val settings = settingsRepository.observeSettings().first()

            _hotspotState.value = HotspotState.Starting
            try {
                withContext(Dispatchers.Main) {
                    val channel = createChannel(manager)
                    currentChannel = channel

                    createGroup(manager, channel, settings)

                    val group = awaitGroupInfo(manager, channel)
                    if (group == null) {
                        manager.removeGroup(channel, null)
                        _hotspotState.value = HotspotState.Error(
                            "Timed out while waiting for group information"
                        )
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                        manager.removeGroup(channel, null)
                        _hotspotState.value = HotspotState.Error(
                            "Reading hotspot credentials requires Android 8.1 or newer"
                        )
                    } else {
                        // Start the captive portal listeners alongside the
                        // hotspot so clients can reach the login page
                        // (http://<group-owner-ip>:8080/portal) even before
                        // the VPN is started.
                        localProxyServer.start()
                        _hotspotState.value = HotspotState.Running(
                            ssid = group.networkName ?: "DIRECT-unknown",
                            password = group.passphrase
                        )
                    }
                }
            } catch (e: Exception) {
                _hotspotState.value = HotspotState.Error(e.message ?: "Failed to start hotspot")
            }
        }
    }

    override suspend fun stopHotspot() {
        stateMutex.withLock {
            val manager = wifiP2pManager ?: return
            val channel = currentChannel ?: run {
                _hotspotState.value = HotspotState.Idle
                return
            }
            withContext(Dispatchers.Main) {
                try {
                    suspendCancellableCoroutine { cont ->
                        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                cont.resume(Unit)
                            }

                            override fun onFailure(reason: Int) {
                                // Best effort: proceed to Idle even if removal failed.
                                cont.resume(Unit)
                            }
                        })
                    }
                } catch (e: Exception) {
                    // Best effort stop.
                }
            }
            currentChannel = null
            _hotspotState.value = HotspotState.Idle
        }
    }

    private fun createChannel(manager: WifiP2pManager): WifiP2pManager.Channel =
        @Suppress("DEPRECATION")
        manager.initialize(context, Looper.getMainLooper(), null)

    private suspend fun createGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        settings: AppSettings
    ) {
        suspendCancellableCoroutine { cont ->
            val listener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    cont.resume(Unit)
                }

                override fun onFailure(reason: Int) {
                    cont.resumeWithException(
                        IllegalStateException("createGroup failed with reason $reason")
                    )
                }
            }
            val config = buildGroupConfig(settings)
            if (config != null) {
                manager.createGroup(channel, config, listener)
            } else {
                manager.createGroup(channel, listener)
            }
        }
    }

    // Wi-Fi Direct group creation with a custom SSID/passphrase requires
    // WifiP2pConfig.Builder (API 29+); older devices fall back to the
    // system-generated group. Wi-Fi Direct groups are always WPA2-PSK per
    // the P2P specification, so OPEN mode substitutes a well-known public
    // passphrase: joining requires no secret and the voucher portal stays
    // the only real gate to the internet.
    private fun buildGroupConfig(settings: AppSettings): WifiP2pConfig? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.i(
                TAG,
                "Custom hotspot settings require Android 10+; using system defaults"
            )
            return null
        }
        val passphrase = when (settings.hotspotSecurity) {
            HotspotSecurity.OPEN -> PUBLIC_PASSPHRASE
            HotspotSecurity.WPA2_PSK -> settings.hotspotPassphrase
        }
        return WifiP2pConfig.Builder()
            .setNetworkName(settings.hotspotSsid)
            .setPassphrase(passphrase)
            .build()
    }

    // Group information may not be available immediately after createGroup succeeds,
    // so poll requestGroupInfo until the group owner reports credentials.
    private suspend fun awaitGroupInfo(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel
    ): WifiP2pGroup? {
        repeat(GROUP_INFO_ATTEMPTS) {
            val group: WifiP2pGroup? = suspendCancellableCoroutine { cont ->
                manager.requestGroupInfo(channel) { candidate ->
                    cont.resume(candidate)
                }
            }
            val hasCredentials = Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 ||
                (!group?.networkName.isNullOrEmpty() && !group?.passphrase.isNullOrEmpty())
            if (group != null && group.isGroupOwner && hasCredentials) return group
            delay(GROUP_INFO_RETRY_DELAY_MS)
        }
        return null
    }

    private companion object {
        const val TAG = "TetherVaultHotspot"
        const val GROUP_INFO_ATTEMPTS = 10
        const val GROUP_INFO_RETRY_DELAY_MS = 500L
        const val PUBLIC_PASSPHRASE = "tethervault"
    }
}
