package com.tethervault.app.domain.model

sealed interface HotspotState {
    data object Idle : HotspotState
    data object Starting : HotspotState
    data class Running(
        val ssid: String,
        val password: String?,
        // True in Open mode: the password is a shared public value by design
        // (Wi-Fi Direct mandates WPA2 on the radio).
        val isPublicPassword: Boolean = false
    ) : HotspotState
    data class Error(val message: String) : HotspotState
}
