package com.example.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.R
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel
import com.example.ui.studio.StudioScreen
import com.example.ui.studio.StudioViewModel
import com.example.ui.theme.CrimsonLive
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.CyanSignalContainer
import com.example.ui.vault.VaultScreen
import com.example.ui.vault.VaultViewModel
import kotlinx.serialization.Serializable

@Serializable
object StudioRoute

@Serializable
object VaultRoute

@Serializable
data class PlayerRoute(val recordingId: Long)

@Composable
fun LiveVaultAppNav(
    container: AppContainer,
    initialRecordingIdToOpen: Long?,
    onConsumedInitialRecordingId: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = currentDestination?.hasRoute<PlayerRoute>() != true

    LaunchedEffect(initialRecordingIdToOpen) {
        if (initialRecordingIdToOpen != null && initialRecordingIdToOpen > 0L) {
            navController.navigate(PlayerRoute(initialRecordingIdToOpen))
            onConsumedInitialRecordingId()
        }
    }

    val recordingState by container.recordingCoordinator.state.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        val studioSelected = currentDestination?.hasRoute<StudioRoute>() == true
                        val vaultSelected = currentDestination?.hasRoute<VaultRoute>() == true

                        NavigationBarItem(
                            selected = studioSelected,
                            onClick = {
                                navController.navigate(StudioRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (studioSelected) Icons.Rounded.Videocam else Icons.Outlined.Videocam,
                                    contentDescription = stringResource(R.string.nav_studio),
                                    tint = if (recordingState.isBusy) CrimsonLive else if (studioSelected) CyanSignal else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = CyanSignalContainer,
                                selectedTextColor = CyanSignal
                            ),
                            modifier = Modifier.testTag("nav_studio")
                        )

                        NavigationBarItem(
                            selected = vaultSelected,
                            onClick = {
                                navController.navigate(VaultRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (vaultSelected) Icons.Rounded.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = stringResource(R.string.nav_vault)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_vault)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = CyanSignalContainer,
                                selectedIconColor = CyanSignal,
                                selectedTextColor = CyanSignal
                            ),
                            modifier = Modifier.testTag("nav_vault")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        val studioSelected = currentDestination?.hasRoute<StudioRoute>() == true
                        val vaultSelected = currentDestination?.hasRoute<VaultRoute>() == true

                        NavigationRailItem(
                            selected = studioSelected,
                            onClick = {
                                navController.navigate(StudioRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (studioSelected) Icons.Rounded.Videocam else Icons.Outlined.Videocam,
                                    contentDescription = stringResource(R.string.nav_studio)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = CyanSignalContainer,
                                selectedIconColor = CyanSignal,
                                selectedTextColor = CyanSignal
                            ),
                            modifier = Modifier.testTag("rail_studio")
                        )

                        NavigationRailItem(
                            selected = vaultSelected,
                            onClick = {
                                navController.navigate(VaultRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (vaultSelected) Icons.Rounded.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = stringResource(R.string.nav_vault)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_vault)) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = CyanSignalContainer,
                                selectedIconColor = CyanSignal,
                                selectedTextColor = CyanSignal
                            ),
                            modifier = Modifier.testTag("rail_vault")
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = StudioRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<StudioRoute> {
                        val studioVm: StudioViewModel = viewModel(
                            factory = StudioViewModel.Factory(
                                liveChecker = container.liveChecker,
                                preferencesRepository = container.preferencesRepository,
                                recordingCoordinator = container.recordingCoordinator
                            )
                        )
                        val state by studioVm.uiState.collectAsStateWithLifecycle()
                        StudioScreen(
                            uiState = state,
                            onUsernameChange = studioVm::onUsernameChange,
                            onCheckLiveStatus = { studioVm.checkLiveStatus() },
                            onSelectRecentUsername = studioVm::selectRecentUsername,
                            onSelectQuality = studioVm::setPreferredQuality,
                            onStartRecording = { creator -> studioVm.startRecording(context, creator) },
                            onStopRecording = { studioVm.stopRecording(context) },
                            onTogglePreviewMute = studioVm::togglePreviewMute,
                            onDismissBanner = studioVm::dismissBanner,
                            onOpenSavedRecording = { recordingId ->
                                studioVm.dismissBanner()
                                navController.navigate(PlayerRoute(recordingId))
                            }
                        )
                    }

                    composable<VaultRoute> {
                        val vaultVm: VaultViewModel = viewModel(
                            factory = VaultViewModel.Factory(
                                recordingRepository = container.recordingRepository
                            )
                        )
                        val state by vaultVm.uiState.collectAsStateWithLifecycle()
                        VaultScreen(
                            uiState = state,
                            onSearchQueryChange = vaultVm::onSearchQueryChange,
                            onPlayRecording = { recording ->
                                navController.navigate(PlayerRoute(recording.id))
                            },
                            onDeleteRecording = vaultVm::deleteRecording
                        )
                    }

                    composable<PlayerRoute> { entry ->
                        val route = entry.toRoute<PlayerRoute>()
                        val playerVm: PlayerViewModel = viewModel(
                            factory = PlayerViewModel.Factory(
                                recordingId = route.recordingId,
                                recordingRepository = container.recordingRepository
                            )
                        )
                        val state by playerVm.uiState.collectAsStateWithLifecycle()
                        PlayerScreen(
                            uiState = state,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
