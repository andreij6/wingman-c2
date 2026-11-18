package com.wingmanc2.feature.plan.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.wingmanc2.core.navigation.MissionRoute
import com.wingmanc2.core.navigation.Navigator
import com.wingmanc2.core.navigation.PlanRoute

fun EntryProviderScope<NavKey>.planEntries(navigator: Navigator, contentPadding: PaddingValues) {
    entry<PlanRoute> {
        PlanScreen(contentPadding = contentPadding, onDispatched = { navigator.switchTab(MissionRoute) })
    }
}
