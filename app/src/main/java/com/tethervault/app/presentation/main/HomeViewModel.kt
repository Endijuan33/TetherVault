package com.tethervault.app.presentation.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.core.content.ContextCompat
import com.tethervault.app.domain.hotspot.HotspotManager
import com.tethervault.app.domain.model.HotspotState
import com.tethervault.app.service.TetherVaultForegroundService
import com.tethervault.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    hotspotManager: HotspotManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val hotspotState: StateFlow<HotspotState> = hotspotManager.hotspotState

    fun startHotspot() {
        val intent = Intent(appContext, TetherVaultForegroundService::class.java)
            .setAction(Constants.ACTION_START_SERVICE)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stopHotspot() {
        val intent = Intent(appContext, TetherVaultForegroundService::class.java)
            .setAction(Constants.ACTION_STOP_SERVICE)
        appContext.startService(intent)
    }
}
