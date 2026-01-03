package com.openbaby.monitor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openbaby.monitor.ui.screens.HomeScreen
import com.openbaby.monitor.ui.screens.MonitoringScreen
import com.openbaby.monitor.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Monitoring : Screen("monitoring")
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                permissionsGranted = permissionsGranted,
                onRequestPermissions = onRequestPermissions,
                onStartMonitoring = {
                    navController.navigate(Screen.Monitoring.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Monitoring.route) {
            MonitoringScreen(
                onStopMonitoring = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
