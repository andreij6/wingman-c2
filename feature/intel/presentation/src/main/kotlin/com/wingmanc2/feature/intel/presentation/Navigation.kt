package com.wingmanc2.feature.intel.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.wingmanc2.core.navigation.IntelRoute
import com.wingmanc2.core.navigation.Navigator
import com.wingmanc2.core.navigation.PlanRoute

fun EntryProviderScope<NavKey>.intelEntries(navigator: Navigator, contentPadding: PaddingValues) {
    entry<IntelRoute> {
        IntelScreen(contentPadding = contentPadding, onPlanRecon = { navigator.switchTab(PlanRoute) })
    }
}
