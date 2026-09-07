package com.tethervault.app.util

object Constants {

    const val NOTIFICATION_CHANNEL_ID = "tethervault_service_channel"
    const val SERVICE_NOTIFICATION_ID = 1

    const val ACTION_START_SERVICE = "com.tethervault.app.action.START_SERVICE"
    const val ACTION_STOP_SERVICE = "com.tethervault.app.action.STOP_SERVICE"

    const val VPN_ADDRESS = "10.0.0.2"
    const val VPN_ADDRESS_PREFIX_LENGTH = 24
    const val VPN_ROUTE = "0.0.0.0"
    const val VPN_ROUTE_PREFIX_LENGTH = 0
    const val VPN_DNS_SERVER = "8.8.8.8"
    const val VPN_MTU = 1500
    const val VPN_SESSION_NAME = "TetherVault"

    const val PROXY_HOST = "127.0.0.1"
    const val PROXY_PORT = 1080
    const val PORTAL_HTTP_PORT = 8080
}
