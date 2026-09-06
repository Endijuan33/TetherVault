package com.tethervault.app.util

import java.net.Inet4Address
import java.net.NetworkInterface

object P2pAddressResolver {

    // Android assigns 192.168.49.1 to the Wi-Fi Direct group owner interface
    // by convention; used when the live interface address cannot be found.
    const val FALLBACK_GROUP_OWNER_IP = "192.168.49.1"

    /**
     * Returns the IPv4 address of the Wi-Fi Direct group owner interface
     * (named "p2p-wlan..."), or null when the interface is not up yet.
     */
    fun getGroupOwnerAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.filter { it.isUp }
            ?.filter { it.name.startsWith(INTERFACE_PREFIX) }
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private const val INTERFACE_PREFIX = "p2p"
}
