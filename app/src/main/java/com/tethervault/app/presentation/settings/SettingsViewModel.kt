package com.tethervault.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tethervault.app.R
import com.tethervault.app.domain.model.AccessLog
import com.tethervault.app.domain.model.AppSettings
import com.tethervault.app.domain.model.HotspotSecurity
import com.tethervault.app.domain.repository.AccessLogRepository
import com.tethervault.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
    accessLogRepository: AccessLogRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val accessLogs: StateFlow<List<AccessLog>> = accessLogRepository.observeRecent(MAX_LOG_ENTRIES)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _saveMessages = MutableSharedFlow<String>()
    val saveMessages: SharedFlow<String> = _saveMessages.asSharedFlow()

    fun saveHotspotConfig(ssid: String, security: HotspotSecurity, passphrase: String) {
        viewModelScope.launch {
            val message = try {
                settingsRepository.updateHotspotConfig(ssid, security, passphrase)
                appContext.getString(R.string.settings_saved)
            } catch (e: IllegalArgumentException) {
                e.message ?: appContext.getString(R.string.settings_save_failed)
            }
            _saveMessages.emit(message)
        }
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 100
    }
}
