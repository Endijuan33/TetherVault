package com.tethervault.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tethervault.app.domain.model.AccessLog
import com.tethervault.app.domain.repository.AccessLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    accessLogRepository: AccessLogRepository
) : ViewModel() {

    val accessLogs: StateFlow<List<AccessLog>> = accessLogRepository.observeRecent(MAX_LOG_ENTRIES)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private companion object {
        const val MAX_LOG_ENTRIES = 100
    }
}
