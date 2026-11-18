package com.wingmanc2

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.wingmanc2.core.designsystem.component.NavigationItem
import com.wingmanc2.core.designsystem.component.WingmanNavigationBar
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.navigation.BattlespaceRoute
import com.wingmanc2.core.navigation.IntelRoute
import com.wingmanc2.core.navigation.MissionRoute
import com.wingmanc2.core.navigation.PlanRoute
import com.wingmanc2.core.navigation.SyncRoute
import com.wingmanc2.core.navigation.TopLevelRoute
import com.wingmanc2.core.navigation.TopLevelRoutes
import com.wingmanc2.core.ui.LocalLinkState
import com.wingmanc2.feature.battlespace.presentation.battlespaceEntries
import com.wingmanc2.feature.intel.presentation.intelEntries
import com.wingmanc2.feature.mission.presentation.missionEntries
import com.wingmanc2.feature.plan.presentation.planEntries
import com.wingmanc2.feature.sync.presentation.syncEntries
import kotlinx.collections.immutable.toImmutableList

private fun TopLevelRoute.navigationItem(): NavigationItem = when (this) {
    BattlespaceRoute -> NavigationItem("Map", WingmanIcons.Map)
    IntelRoute -> NavigationItem("Intel", WingmanIcons.Hub)
    PlanRoute -> NavigationItem("Plan", WingmanIcons.Route)
    MissionRoute -> NavigationItem("Mission", WingmanIcons.Drone)
    SyncRoute -> NavigationItem("Sync", WingmanIcons.Sync)
}

private val NavigationItems = TopLevelRoutes.map { it.navigationItem() }.toImmutableList()

@Composable
fun WingmanApp(viewModel: AppViewModel = hiltViewModel()) {
    val linkState by viewModel.linkState.collectAsStateWithLifecycle()
    val navigator = rememberTopLevelNavigator()
    CompositionLocalProvider(LocalLinkState provides linkState) {
        Scaffold(
            containerColor = WingmanTheme.colors.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                WingmanNavigationBar(
                    items = NavigationItems,
                    selectedIndex = navigator.currentIndex,
                    onSelect = { navigator.switchTab(TopLevelRoutes[it]) },
                )
            },
        ) { contentPadding ->
            val provider = remember(navigator, contentPadding) {
                entryProvider<NavKey> {
                    battlespaceEntries(navigator, contentPadding)
                    intelEntries(navigator, contentPadding)
                    planEntries(navigator, contentPadding)
                    missionEntries(navigator, contentPadding)
                    syncEntries(navigator, contentPadding)
                }
            }
            val entriesByTab = navigator.backStacks.mapValues { (_, stack) ->
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = provider,
                )
            }
            NavDisplay(
                entries = navigator.visibleRoutes.flatMap { entriesByTab.getValue(it) },
                onBack = navigator::back,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
            )
        }
    }
}
