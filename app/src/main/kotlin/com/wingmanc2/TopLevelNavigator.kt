package com.wingmanc2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.wingmanc2.core.navigation.BattlespaceRoute
import com.wingmanc2.core.navigation.Navigator
import com.wingmanc2.core.navigation.TopLevelRoute
import com.wingmanc2.core.navigation.TopLevelRoutes

@Stable
class TopLevelNavigator(
    private val selected: MutableIntState,
    val backStacks: Map<TopLevelRoute, NavBackStack<NavKey>>,
) : Navigator {
    val currentIndex: Int get() = selected.intValue
    val currentRoute: TopLevelRoute get() = TopLevelRoutes[currentIndex]

    val visibleRoutes: List<TopLevelRoute>
        get() = if (currentRoute == BattlespaceRoute) listOf(BattlespaceRoute) else listOf(BattlespaceRoute, currentRoute)

    override fun switchTab(route: TopLevelRoute) {
        selected.intValue = TopLevelRoutes.indexOf(route)
    }

    override fun back() {
        val stack = backStacks.getValue(currentRoute)
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else switchTab(BattlespaceRoute)
    }
}

@Composable
fun rememberTopLevelNavigator(): TopLevelNavigator {
    val selected = rememberSaveable { mutableIntStateOf(0) }
    val backStacks = TopLevelRoutes.associateWith { rememberNavBackStack(it) }
    return TopLevelNavigator(selected, backStacks)
}
