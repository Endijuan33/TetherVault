package com.tethervault.app.domain.repository

import com.tethervault.app.domain.model.AppSettings
import com.tethervault.app.domain.model.HotspotSecurity
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun observeSettings(): Flow<AppSettings>

    /**
     * Persists the hotspot configuration. Normalizes the SSID with the
     * mandatory DIRECT- prefix and validates the passphrase length for
     * secured networks. Throws IllegalArgumentException on invalid input.
     */
    suspend fun updateHotspotConfig(ssid: String, security: HotspotSecurity, passphrase: String)
}
