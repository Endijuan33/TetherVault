package com.tethervault.app.presentation.main

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tethervault.app.R
import com.tethervault.app.domain.model.HotspotState
import com.tethervault.app.domain.model.VpnState
import com.tethervault.app.util.Constants
import com.tethervault.app.util.P2pAddressResolver

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hotspotState by viewModel.hotspotState.collectAsStateWithLifecycle()
    var permissionsGranted by remember(context) { mutableStateOf(requiredPermissionsGranted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))
        StatusSection(hotspotState)
        Spacer(Modifier.height(32.dp))
        if (!permissionsGranted) {
            Text(
                text = stringResource(R.string.home_permissions_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = {
                if (permissionsGranted) {
                    when (hotspotState) {
                        is HotspotState.Running, HotspotState.Starting -> viewModel.stopHotspot()
                        else -> viewModel.startHotspot()
                    }
                } else {
                    permissionLauncher.launch(requiredPermissions())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (hotspotState is HotspotState.Running) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            val labelRes = when (hotspotState) {
                is HotspotState.Running, HotspotState.Starting -> R.string.home_action_stop
                else -> R.string.home_action_start
            }
            Text(text = stringResource(labelRes))
        }

        Spacer(Modifier.height(24.dp))
        VpnStatusSection(vpnState)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                when (vpnState) {
                    VpnState.Running, VpnState.Starting -> viewModel.stopVpn()
                    else -> {
                        val prepareIntent = VpnService.prepare(context)
                        if (prepareIntent != null) {
                            vpnPermissionLauncher.launch(prepareIntent)
                        } else {
                            viewModel.startVpn()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            val vpnLabelRes = when (vpnState) {
                VpnState.Running, VpnState.Starting -> R.string.home_action_stop_vpn
                else -> R.string.home_action_start_vpn
            }
            Text(text = stringResource(vpnLabelRes))
        }
    }
}

@Composable
private fun StatusSection(state: HotspotState) {
    when (state) {
        HotspotState.Idle -> StatusText(
            text = stringResource(R.string.home_status_idle),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HotspotState.Starting -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            StatusText(
                text = stringResource(R.string.home_status_starting),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is HotspotState.Running -> CredentialsCard(state)
        is HotspotState.Error -> StatusText(
            text = stringResource(R.string.home_status_error, state.message),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun VpnStatusSection(state: VpnState) {
    when (state) {
        VpnState.Idle -> StatusText(
            text = stringResource(R.string.vpn_status_idle),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VpnState.Starting -> StatusText(
            text = stringResource(R.string.vpn_status_starting),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VpnState.Running -> StatusText(
            text = stringResource(R.string.vpn_status_active),
            color = MaterialTheme.colorScheme.primary
        )
        is VpnState.Error -> StatusText(
            text = stringResource(R.string.vpn_status_error, state.message),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CredentialsCard(state: HotspotState.Running) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_status_active),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_ssid_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.ssid,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            if (state.password != null) {
                Text(
                    text = stringResource(R.string.home_password_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.password,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = stringResource(R.string.home_open_network),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            val portalAddress = remember {
                P2pAddressResolver.getGroupOwnerAddress()
                    ?: P2pAddressResolver.FALLBACK_GROUP_OWNER_IP
            }
            Text(
                text = stringResource(
                    R.string.home_voucher_hint,
                    "http://$portalAddress:${Constants.PORTAL_HTTP_PORT}"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

private fun requiredPermissionsGranted(context: android.content.Context): Boolean {
    return requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
