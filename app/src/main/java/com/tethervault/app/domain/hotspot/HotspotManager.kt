package com.tethervault.app.domain.hotspot

import com.tethervault.app.domain.model.HotspotState
import kotlinx.coroutines.flow.StateFlow

interface HotspotManager {

    val hotspotState: StateFlow<HotspotState>

    suspend fun startHotspot()

    suspend fun stopHotspot()
}
