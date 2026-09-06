package com.tethervault.app.domain.model

sealed interface HotspotState {
    data object Idle : HotspotState
    data object Starting : HotspotState
    data class Running(val ssid: String, val password: String) : HotspotState
    data class Error(val message: String) : HotspotState
}
