package com.tornier.prrcn.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tornier.prrcn.R
import com.tornier.prrcn.data.ThemeMode
import com.tornier.prrcn.ui.components.AcrylicBackground
import com.tornier.prrcn.ui.home.HomeScreen
import com.tornier.prrcn.ui.picker.PickerScreen
import com.tornier.prrcn.ui.settings.SettingsScreen
import com.tornier.prrcn.ui.theme.PrrcnTheme
import com.tornier.prrcn.ui.vault.VaultScreen
import com.tornier.prrcn.ui.vault.VaultsScreen
import com.tornier.prrcn.ui.viewer.ViewerScreen

object Routes {
    const val HOME = "home"
    const val VAULTS = "vaults"
    const val SETTINGS = "settings"
    const val PICKER = "picker"
    const val VAULT = "vault"
    const val VIEWER = "viewer"
}

private data class TopDest(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun PrrcnApp(vm: PrrcnViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val seed by vm.seedColor.collectAsStateWithLifecycle()
    val background by vm.background.collectAsStateWithLifecycle()

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    PrrcnTheme(seedColor = seed, darkTheme = darkTheme) {
        // Keep system-bar icon contrast in sync with the theme.
        val view = LocalView.current
        if (!view.isInEditMode) {
            LaunchedEffect(darkTheme) {
                val window = (view.context as Activity).window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }

        val surface = MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxSize()) {
            AcrylicBackground(
                bitmap = background,
                seedColor = seed,
                darkTheme = darkTheme,
                surfaceColor = surface
            )
            AppScaffold(vm)
        }
    }
}

@Composable
private fun AppScaffold(vm: PrrcnViewModel) {
    val navController = rememberNavController()
    val topDests = listOf(
        TopDest(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
        TopDest(Routes.VAULTS, R.string.nav_vaults, Icons.Rounded.Inventory2),
        TopDest(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings)
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBars = currentRoute in topDests.map { it.route }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(
                visible = showBars,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    tonalElevation = 0.dp
                ) {
                    topDests.forEach { dest ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentRoute == Routes.HOME || currentRoute == Routes.VAULTS,
                enter = fadeIn(), exit = fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.PICKER) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_import)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    contentPadding = padding,
                    onOpenVault = { vault -> vm.selectVault(vault); navController.navigate(Routes.VAULT) },
                    onSeeAll = { navController.navigate(Routes.VAULTS) },
                    onImport = { navController.navigate(Routes.PICKER) }
                )
            }
            composable(Routes.VAULTS) {
                VaultsScreen(
                    vm = vm,
                    contentPadding = padding,
                    onOpenVault = { vault -> vm.selectVault(vault); navController.navigate(Routes.VAULT) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm = vm, contentPadding = padding)
            }
            composable(Routes.PICKER) {
                PickerScreen(vm = vm, onClose = { navController.popBackStack() })
            }
            composable(Routes.VAULT) {
                VaultScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpenEntry = { index -> vm.openViewerAt(index); navController.navigate(Routes.VIEWER) }
                )
            }
            composable(Routes.VIEWER) {
                ViewerScreen(vm = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
