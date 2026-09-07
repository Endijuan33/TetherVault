package com.tethervault.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tethervault.app.R
import com.tethervault.app.domain.model.AccessLog
import com.tethervault.app.domain.model.AccessLogAction
import com.tethervault.app.domain.model.HotspotSecurity
import com.tethervault.app.presentation.components.EmptyState
import com.tethervault.app.util.TimeFormatter

private val LoginColor = Color(0xFF2E7D32)
private val RevokedColor = Color(0xFFC62828)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accessLogs by viewModel.accessLogs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.saveMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    var ssid by remember(settings) { mutableStateOf(settings?.hotspotSsid.orEmpty()) }
    var security by remember(settings) {
        mutableStateOf(settings?.hotspotSecurity ?: HotspotSecurity.WPA2_PSK)
    }
    var passphrase by remember(settings) { mutableStateOf(settings?.hotspotPassphrase.orEmpty()) }

    val saveEnabled = ssid.isNotBlank() && passphrase.length in 8..63

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            item {
                HotspotConfigCard(
                    ssid = ssid,
                    security = security,
                    passphrase = passphrase,
                    onSsidChange = { ssid = it },
                    onSecurityChange = { security = it },
                    onPassphraseChange = { passphrase = it },
                    saveEnabled = saveEnabled,
                    onSave = { viewModel.saveHotspotConfig(ssid, security, passphrase) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.access_log_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            if (accessLogs.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.access_log_empty_title),
                        subtitle = stringResource(R.string.access_log_empty_subtitle)
                    )
                }
            } else {
                items(accessLogs, key = AccessLog::id) { log ->
                    AccessLogRow(log)
                }
            }
        }
    }
}

@Composable
private fun HotspotConfigCard(
    ssid: String,
    security: HotspotSecurity,
    passphrase: String,
    onSsidChange: (String) -> Unit,
    onSecurityChange: (HotspotSecurity) -> Unit,
    onPassphraseChange: (String) -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_hotspot_section),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ssid,
                onValueChange = onSsidChange,
                label = { Text(stringResource(R.string.settings_ssid_label)) },
                supportingText = { Text(stringResource(R.string.settings_ssid_support)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_security_label),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = security == HotspotSecurity.OPEN,
                    onClick = { onSecurityChange(HotspotSecurity.OPEN) },
                    label = { Text(stringResource(R.string.settings_security_open)) }
                )
                FilterChip(
                    selected = security == HotspotSecurity.WPA2_PSK,
                    onClick = { onSecurityChange(HotspotSecurity.WPA2_PSK) },
                    label = { Text(stringResource(R.string.settings_security_wpa2)) }
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                label = {
                    Text(
                        text = stringResource(
                            if (security == HotspotSecurity.OPEN) {
                                R.string.settings_public_passphrase_label
                            } else {
                                R.string.settings_passphrase_label
                            }
                        )
                    )
                },
                supportingText = {
                    Text(
                        text = stringResource(
                            if (security == HotspotSecurity.OPEN) {
                                R.string.settings_public_passphrase_support
                            } else {
                                R.string.settings_passphrase_support
                            }
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (security == HotspotSecurity.OPEN) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_open_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_save))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_apply_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccessLogRow(log: AccessLog) {
    val actionColor = when (log.action) {
        AccessLogAction.LOGIN -> LoginColor
        AccessLogAction.REVOKED -> RevokedColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = log.action,
                style = MaterialTheme.typography.labelLarge,
                color = actionColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${log.deviceMac} • ${TimeFormatter.format(log.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            log.details?.let { details ->
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
