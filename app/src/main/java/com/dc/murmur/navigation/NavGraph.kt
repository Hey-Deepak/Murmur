package com.dc.murmur.navigation

import android.Manifest
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dc.murmur.feature.crashlogs.CrashLogsScreen
import com.dc.murmur.feature.home.HomeScreen
import com.dc.murmur.feature.insights.InsightsScreen
import com.dc.murmur.feature.people.PeopleScreen
import com.dc.murmur.feature.permission.PermissionScreen
import com.dc.murmur.feature.recordings.RecordingsScreen
import com.dc.murmur.feature.stats.StatsScreen

private sealed class Screen(val route: String, val label: String) {
    object Permissions : Screen("permissions", "Permissions")
    object Home : Screen("home", "Home")
    object Recordings : Screen("recordings", "Recordings")
    object Insights : Screen("insights", "Insights")
    object People : Screen("people", "People")
    object Stats : Screen("stats", "Stats")
    object CrashLogs : Screen("crash_logs", "Crash Logs")
}

private val bottomNavScreens = listOf(Screen.Home, Screen.Recordings, Screen.Insights, Screen.People, Screen.Stats)

@Composable
fun MurmurNavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()

    val startDestination = if (hasRequiredPermissions(context)) {
        Screen.Home.route
    } else {
        Screen.Permissions.route
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute != Screen.Permissions.route &&
            currentRoute != Screen.CrashLogs.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDest = navBackStackEntry?.destination
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = when (screen) {
                                        Screen.Home -> Icons.Default.Mic
                                        Screen.Recordings -> Icons.Default.Headphones
                                        Screen.Insights -> Icons.Default.Psychology
                                        Screen.People -> Icons.Default.People
                                        Screen.Stats -> Icons.Default.BarChart
                                        else -> Icons.Default.Mic
                                    },
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) },
                            selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Permissions.route) {
                PermissionScreen(onAllGranted = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Recordings.route) { RecordingsScreen() }
            composable(Screen.Insights.route) { InsightsScreen() }
            composable(Screen.People.route) { PeopleScreen() }
            composable(Screen.Stats.route) {
                StatsScreen(
                    onNavigateToCrashLogs = { navController.navigate(Screen.CrashLogs.route) }
                )
            }
            composable(Screen.CrashLogs.route) {
                CrashLogsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun hasRequiredPermissions(context: android.content.Context): Boolean {
    val standardPerms = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val standardGranted = standardPerms.all {
        ContextCompat.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED
    }
    val storageGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
    return standardGranted && storageGranted
}
