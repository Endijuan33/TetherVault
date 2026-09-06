package com.tethervault.app.data.hotspot

import android.content.Context
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import com.tethervault.app.domain.hotspot.HotspotManager
import com.tethervault.app.domain.model.HotspotState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    @ApplicationContext private val context: Context
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

            _hotspotState.value = HotspotState.Starting
            try {
                withContext(Dispatchers.Main) {
                    val channel = createChannel(manager)
                    currentChannel = channel

                    createGroup(manager, channel)

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
                        _hotspotState.value = HotspotState.Running(
                            ssid = group.networkName ?: "DIRECT-unknown",
                            password = group.passphrase.orEmpty()
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
        channel: WifiP2pManager.Channel
    ) {
        suspendCancellableCoroutine { cont ->
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    cont.resume(Unit)
                }

                override fun onFailure(reason: Int) {
                    cont.resumeWithException(
                        IllegalStateException("createGroup failed with reason $reason")
                    )
                }
            })
        }
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
        const val GROUP_INFO_ATTEMPTS = 10
        const val GROUP_INFO_RETRY_DELAY_MS = 500L
    }
}
