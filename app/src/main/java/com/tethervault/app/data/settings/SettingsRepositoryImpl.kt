package com.tethervault.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tethervault.app.domain.model.AppSettings
import com.tethervault.app.domain.model.HotspotSecurity
import com.tethervault.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tethervault_settings"
)

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        context.settingsDataStore.data.map { prefs ->
            AppSettings(
                hotspotSsid = prefs[KEY_HOTSPOT_SSID] ?: DEFAULT_SSID,
                hotspotSecurity = prefs[KEY_HOTSPOT_SECURITY]
                    ?.let { stored -> runCatching { HotspotSecurity.valueOf(stored) }.getOrNull() }
                    ?: DEFAULT_SECURITY,
                hotspotPassphrase = prefs[KEY_HOTSPOT_PASSPHRASE] ?: DEFAULT_PASSPHRASE
            )
        }

    override suspend fun updateHotspotConfig(
        ssid: String,
        security: HotspotSecurity,
        passphrase: String
    ) {
        val trimmedSsid = ssid.trim()
        require(trimmedSsid.isNotBlank()) { "SSID must not be empty" }
        val normalizedSsid = if (trimmedSsid.startsWith(SSID_PREFIX, ignoreCase = true)) {
            trimmedSsid
        } else {
            "$SSID_PREFIX$trimmedSsid"
        }
        require(normalizedSsid.length <= MAX_SSID_LENGTH) {
            "SSID is too long (max $MAX_SSID_LENGTH characters including the DIRECT- prefix)"
        }
        // Wi-Fi Direct mandates WPA2-PSK, so a passphrase is required in both
        // modes; in OPEN mode it is a shared public value by design.
        require(passphrase.length in MIN_PASSPHRASE_LENGTH..MAX_PASSPHRASE_LENGTH) {
            "Passphrase must be $MIN_PASSPHRASE_LENGTH-$MAX_PASSPHRASE_LENGTH characters"
        }

        context.settingsDataStore.edit { prefs ->
            prefs[KEY_HOTSPOT_SSID] = normalizedSsid
            prefs[KEY_HOTSPOT_SECURITY] = security.name
            prefs[KEY_HOTSPOT_PASSPHRASE] = passphrase
        }
    }

    private companion object {
        val KEY_HOTSPOT_SSID = stringPreferencesKey("hotspot_ssid")
        val KEY_HOTSPOT_SECURITY = stringPreferencesKey("hotspot_security")
        val KEY_HOTSPOT_PASSPHRASE = stringPreferencesKey("hotspot_passphrase")

        const val SSID_PREFIX = "DIRECT-"
        const val MAX_SSID_LENGTH = 32
        const val MIN_PASSPHRASE_LENGTH = 8
        const val MAX_PASSPHRASE_LENGTH = 63
        const val DEFAULT_SSID = "DIRECT-TetherVault"
        val DEFAULT_SECURITY = HotspotSecurity.WPA2_PSK
        const val DEFAULT_PASSPHRASE = "tethervault"
    }
}
