package com.wingmanc2.feature.mission.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.wingmanc2.core.navigation.MissionRoute
import com.wingmanc2.core.navigation.Navigator
import com.wingmanc2.core.navigation.PlanRoute

fun EntryProviderScope<NavKey>.missionEntries(navigator: Navigator, contentPadding: PaddingValues) {
    entry<MissionRoute> {
        MissionScreen(contentPadding = contentPadding, onOpenPlanner = { navigator.switchTab(PlanRoute) })
    }
}
