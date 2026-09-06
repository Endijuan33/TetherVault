package com.tethervault.app.presentation.vouchers

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tethervault.app.R
import com.tethervault.app.domain.model.Voucher
import com.tethervault.app.presentation.components.EmptyState
import com.tethervault.app.util.TimeFormatter
import kotlinx.coroutines.launch

private val AvailableColor = Color(0xFF2E7D32)
private val UsedColor = Color(0xFF757575)
private val ExpiredColor = Color(0xFFF9A825)

@Composable
fun VouchersScreen(
    viewModel: VouchersViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val vouchers by viewModel.vouchers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var voucherPendingDelete by remember { mutableStateOf<Voucher?>(null) }
    var showGenerateDialog by remember { mutableStateOf(false) }

    fun showSnackbar(messageRes: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(messageRes)) }
    }

    fun copyVoucherCode(code: String) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("voucher", code))
        showSnackbar(R.string.voucher_copied)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showGenerateDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.vouchers_generate)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.vouchers_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            if (vouchers.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ConfirmationNumber,
                    title = stringResource(R.string.vouchers_empty_title),
                    subtitle = stringResource(R.string.vouchers_empty_subtitle)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vouchers, key = Voucher::id) { voucher ->
                        VoucherRow(
                            voucher = voucher,
                            onCopy = { copyVoucherCode(voucher.code) },
                            onDelete = { voucherPendingDelete = voucher }
                        )
                    }
                }
            }
        }
    }

    voucherPendingDelete?.let { voucher ->
        AlertDialog(
            onDismissRequest = { voucherPendingDelete = null },
            title = { Text(stringResource(R.string.voucher_delete_confirm_title)) },
            text = { Text(stringResource(R.string.voucher_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVoucher(voucher)
                        voucherPendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { voucherPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showGenerateDialog) {
        GenerateVoucherDialog(
            onGenerate = { hours ->
                viewModel.generateVoucher(hours)
                showGenerateDialog = false
                showSnackbar(R.string.voucher_generated)
            },
            onDismiss = { showGenerateDialog = false }
        )
    }
}

private enum class DurationOption(val hours: Int?, val labelRes: Int) {
    ONE_HOUR(1, R.string.duration_1_hour),
    HOURS_24(24, R.string.duration_24_hours),
    DAYS_7(7 * 24, R.string.duration_7_days),
    CUSTOM(null, R.string.duration_custom)
}

@Composable
private fun GenerateVoucherDialog(
    onGenerate: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf(DurationOption.HOURS_24) }
    var customHours by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voucher_generate_title)) },
        text = {
            Column {
                DurationOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedOption == option,
                                onClick = { selectedOption = option }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option }
                        )
                        Text(text = stringResource(option.labelRes))
                    }
                }
                if (selectedOption == DurationOption.CUSTOM) {
                    OutlinedTextField(
                        value = customHours,
                        onValueChange = { input ->
                            if (input.all(Char::isDigit)) customHours = input
                        },
                        label = { Text(stringResource(R.string.duration_custom_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hours = selectedOption.hours ?: customHours.toIntOrNull()
                    if (hours != null && hours > 0) {
                        onGenerate(hours)
                    }
                }
            ) {
                Text(stringResource(R.string.voucher_generate_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private enum class VoucherStatus(val labelRes: Int, val color: Color) {
    AVAILABLE(R.string.voucher_available, AvailableColor),
    USED(R.string.voucher_used, UsedColor),
    EXPIRED(R.string.voucher_expired, ExpiredColor)
}

private fun resolveStatus(voucher: Voucher): VoucherStatus = when {
    voucher.isUsed -> VoucherStatus.USED
    voucher.expiresAt <= System.currentTimeMillis() -> VoucherStatus.EXPIRED
    else -> VoucherStatus.AVAILABLE
}

@Composable
private fun VoucherRow(
    voucher: Voucher,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val status = resolveStatus(voucher)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voucher.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.voucher_expires_at, TimeFormatter.format(voucher.expiresAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.voucher_max_devices, voucher.maxDevices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(status.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = status.color
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.voucher_copy)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.voucher_delete)
                )
            }
        }
    }
}
