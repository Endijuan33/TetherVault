package com.tethervault.app.domain.model

sealed interface VpnState {
    data object Idle : VpnState
    data object Starting : VpnState
    data object Running : VpnState
    data class Error(val message: String) : VpnState
}
