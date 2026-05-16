package com.example.connect

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.connect.data.model.ConnectionStatus
import com.example.connect.ui.components.NotificationBanner
import com.example.connect.ui.screens.FilesScreen
import com.example.connect.ui.screens.HomeScreen
import com.example.connect.ui.screens.NotificationsScreen
import com.example.connect.ui.theme.Charcoal600
import com.example.connect.ui.theme.Charcoal800
import com.example.connect.ui.theme.Charcoal900
import com.example.connect.ui.theme.ConnectTheme
import com.example.connect.ui.theme.ElectricCyan
import com.example.connect.ui.theme.TextSecondary
import com.example.connect.ui.theme.screens.ScanScreen
import com.example.connect.viewmodel.ConnectViewModel

object Routes {
    const val SCAN          = "scan"
    const val HOME          = "home"
    const val FILES         = "files"
    const val NOTIFICATIONS = "notifications"
}

data class BottomNavItem(
    val label:      String,
    val route:      String,
    val icon:       ImageVector,
    val badgeCount: Int = 0
)

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConnectTheme {
                ConnectApp()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun ConnectApp() {
    val viewModel: ConnectViewModel = viewModel()
    val uiState by viewModel.uiStateWithTransfers.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    val currentRoute = navController
        .currentBackStackEntryAsState()
        .value?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.HOME,
        Routes.NOTIFICATIONS,
        Routes.FILES,
        Routes.SCAN
    )

    Scaffold(
        containerColor = Charcoal900,
        bottomBar = {
            if (showBottomBar) {
                ConnectBottomBar(
                    currentRoute = currentRoute,
                    unreadCount  = uiState.unreadNotificationCount,
                    onNavigate   = { route ->
                        if (route == Routes.SCAN) {
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {

            NavHost(
                navController    = navController,
                startDestination = Routes.SCAN
            ) {
                composable(Routes.SCAN) {
                    ScanScreen(onDeviceScanned = { deviceId ->
                        viewModel.onDeviceScanned(deviceId)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SCAN) { inclusive = true }
                        }
                    })
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        uiState      = uiState,
                        onDisconnect = { viewModel.disconnect() }
                    )
                }
                composable(Routes.FILES) {
                    FilesScreen(
                        uiState    = uiState,
                        onSendFile = { uri, mime -> viewModel.sendFile(uri, mime) }
                    )
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(
                        uiState    = uiState,
                        onMarkRead = { viewModel.markNotificationsRead() }
                    )
                }
            }

            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                NotificationBanner(
                    notification = uiState.bannerNotification,
                    onDismiss    = { viewModel.dismissBanner() }
                )
            }
        }
    }
}

@Composable
fun ConnectBottomBar(
    currentRoute: String?,
    unreadCount:  Int,
    onNavigate:   (String) -> Unit
) {
    // Always 4 tabs with unique routes — no duplicates, so only one can ever highlight.
    // Home tab is always Routes.HOME; Scanner tab is always Routes.SCAN.
    val items = listOf(
        BottomNavItem(
            label = "Home",
            route = Routes.HOME,
            icon  = Icons.Filled.Home
        ),
        BottomNavItem(
            label = "Files",
            route = Routes.FILES,
            icon  = Icons.Filled.AccountBox
        ),
        BottomNavItem(
            label      = "Notifications",
            route      = Routes.NOTIFICATIONS,
            icon       = Icons.Filled.Notifications,
            badgeCount = unreadCount
        ),
        BottomNavItem(
            label = "Scanner",
            route = Routes.SCAN,
            icon  = Icons.Filled.AddCircle
        )
    )

    NavigationBar(
        containerColor = Charcoal800,
        tonalElevation = androidx.compose.ui.unit.Dp.Unspecified
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick  = { onNavigate(item.route) },
                icon     = {
                    BadgedBox(
                        badge = {
                            if (item.badgeCount > 0) {
                                Badge { Text(item.badgeCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector        = item.icon,
                            contentDescription = item.label
                        )
                    }
                },
                label  = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = ElectricCyan,
                    selectedTextColor   = ElectricCyan,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor      = Charcoal600
                )
            )
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.padding(all = 32.dp)
    ) {
        Text(text = name, color = com.example.connect.ui.theme.TextPrimary)
    }
}