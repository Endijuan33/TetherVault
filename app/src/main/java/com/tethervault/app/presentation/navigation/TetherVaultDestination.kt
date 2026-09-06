package com.tethervault.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.tethervault.app.R

enum class TetherVaultDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int
) {
    HOME("home", Icons.Outlined.Home, R.string.nav_home),
    DEVICES("devices", Icons.Outlined.Devices, R.string.nav_devices),
    VOUCHERS("vouchers", Icons.Outlined.ConfirmationNumber, R.string.nav_vouchers),
    SETTINGS("settings", Icons.Outlined.Settings, R.string.nav_settings)
}
