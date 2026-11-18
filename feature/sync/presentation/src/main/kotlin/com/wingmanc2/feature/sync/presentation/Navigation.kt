package com.wingmanc2.feature.sync.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.wingmanc2.core.navigation.Navigator
import com.wingmanc2.core.navigation.SyncRoute

fun EntryProviderScope<NavKey>.syncEntries(navigator: Navigator, contentPadding: PaddingValues) {
    entry<SyncRoute> { SyncScreen(contentPadding = contentPadding) }
}
