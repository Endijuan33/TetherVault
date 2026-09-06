package com.tethervault.app.presentation.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tethervault.app.presentation.devices.DevicesScreen
import com.tethervault.app.presentation.navigation.TetherVaultDestination
import com.tethervault.app.presentation.settings.SettingsScreen
import com.tethervault.app.presentation.vouchers.VouchersScreen

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                TetherVaultDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(destination.icon, contentDescription = null)
                        },
                        label = {
                            Text(stringResource(destination.labelRes))
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TetherVaultDestination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TetherVaultDestination.HOME.route) {
                HomeScreen()
            }
            composable(TetherVaultDestination.DEVICES.route) {
                DevicesScreen()
            }
            composable(TetherVaultDestination.VOUCHERS.route) {
                VouchersScreen()
            }
            composable(TetherVaultDestination.SETTINGS.route) {
                SettingsScreen()
            }
        }
    }
}
