package com.tethervault.app.presentation.devices

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
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tethervault.app.R
import com.tethervault.app.domain.model.ConnectedDevice
import com.tethervault.app.presentation.components.EmptyState
import com.tethervault.app.util.ByteFormatter

private val ConnectedColor = Color(0xFF2E7D32)
private val BlockedColor = Color(0xFFC62828)

@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    var devicePendingRevoke by remember { mutableStateOf<ConnectedDevice?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.devices_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        if (devices.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Devices,
                title = stringResource(R.string.devices_empty_title),
                subtitle = stringResource(R.string.devices_empty_subtitle)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = ConnectedDevice::macAddress) { device ->
                    DeviceRow(
                        device = device,
                        onRevoke = { devicePendingRevoke = device }
                    )
                }
            }
        }
    }

    devicePendingRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { devicePendingRevoke = null },
            title = { Text(stringResource(R.string.device_revoke_confirm_title)) },
            text = { Text(stringResource(R.string.device_revoke_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnectDevice(device)
                        devicePendingRevoke = null
                    }
                ) {
                    Text(stringResource(R.string.device_action_revoke))
                }
            },
            dismissButton = {
                TextButton(onClick = { devicePendingRevoke = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DeviceRow(device: ConnectedDevice, onRevoke: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${device.macAddress} • ${device.ipAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "↑ ${ByteFormatter.format(device.totalBytesUp)}  •  ↓ ${ByteFormatter.format(device.totalBytesDown)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(
                        if (device.isAuthenticated) R.string.device_status_connected
                        else R.string.device_status_blocked
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (device.isAuthenticated) ConnectedColor else BlockedColor
                )
                if (device.isAuthenticated) {
                    TextButton(onClick = onRevoke) {
                        Text(text = stringResource(R.string.device_action_revoke))
                    }
                }
            }
        }
    }
}
