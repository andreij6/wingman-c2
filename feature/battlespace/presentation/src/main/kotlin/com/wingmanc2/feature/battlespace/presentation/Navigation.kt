package com.wingmanc2.feature.battlespace.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.wingmanc2.core.navigation.BattlespaceRoute
import com.wingmanc2.core.navigation.IntelRoute
import com.wingmanc2.core.navigation.Navigator
import com.wingmanc2.core.navigation.PlanRoute

fun EntryProviderScope<NavKey>.battlespaceEntries(navigator: Navigator, contentPadding: PaddingValues) {
    entry<BattlespaceRoute> {
        BattlespaceScreen(
            contentPadding = contentPadding,
            onViewLineage = { navigator.switchTab(IntelRoute) },
            onPlanRecon = { navigator.switchTab(PlanRoute) },
        )
    }
}
