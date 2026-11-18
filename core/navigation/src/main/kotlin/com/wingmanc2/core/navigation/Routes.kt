package com.wingmanc2.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface TopLevelRoute : NavKey

@Serializable data object BattlespaceRoute : TopLevelRoute
@Serializable data object IntelRoute : TopLevelRoute
@Serializable data object PlanRoute : TopLevelRoute
@Serializable data object MissionRoute : TopLevelRoute
@Serializable data object SyncRoute : TopLevelRoute

val TopLevelRoutes: List<TopLevelRoute> = listOf(BattlespaceRoute, IntelRoute, PlanRoute, MissionRoute, SyncRoute)

interface Navigator {
    fun switchTab(route: TopLevelRoute)
    fun back()
}
