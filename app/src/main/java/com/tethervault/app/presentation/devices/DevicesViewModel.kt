package com.tethervault.app.presentation.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tethervault.app.domain.model.AccessLogAction
import com.tethervault.app.domain.model.ConnectedDevice
import com.tethervault.app.domain.repository.AccessLogRepository
import com.tethervault.app.domain.repository.ConnectedDeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val connectedDeviceRepository: ConnectedDeviceRepository,
    private val accessLogRepository: AccessLogRepository
) : ViewModel() {

    val devices: StateFlow<List<ConnectedDevice>> = connectedDeviceRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun disconnectDevice(device: ConnectedDevice) {
        viewModelScope.launch {
            connectedDeviceRepository.upsert(device.copy(isAuthenticated = false))
            accessLogRepository.log(
                deviceMac = device.macAddress,
                action = AccessLogAction.REVOKED,
                details = "Access revoked for ${device.ipAddress}"
            )
        }
    }
}
