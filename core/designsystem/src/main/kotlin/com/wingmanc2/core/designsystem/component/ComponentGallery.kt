package com.wingmanc2.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState
import kotlinx.collections.immutable.persistentListOf

@Preview(widthDp = 412)
@Composable
private fun ComponentGalleryPreview() {
    WingmanTheme {
        Column(
            modifier = Modifier.background(WingmanTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkChip(LinkState(LinkMode.ONLINE, 42, 0, 0, null))
                LinkChip(LinkState(LinkMode.DENIED, null, 100, 4, null))
            }
            DdilBanner(queued = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AffiliationFilterChip("All 14", WingmanTheme.colors.primary, selected = true, onClick = {})
                AffiliationFilterChip("Hostile 3", WingmanTheme.colors.hostile, selected = false, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TintChip("Standoff ≥ 200 m", WingmanTheme.colors.primary, leading = ChipLeading.Check)
                PredicateChip("kind:EMITTER")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Affiliation.entries.forEach { AffiliationSymbol(it, size = 20.dp) }
                AffiliationSymbol(Affiliation.FRIEND, isAir = true, size = 20.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WingmanButton("Plan recon", onClick = {})
                WingmanButton("Replan", onClick = {}, variant = ButtonVariant.Tonal)
                WingmanButton("RTB", onClick = {}, variant = ButtonVariant.Danger)
            }
            SegmentedControl(persistentListOf("Online", "Degraded", "Denied"), selectedIndex = 2, onSelect = {}, accent = WingmanTheme.colors.warn)
            WingmanCard {
                SectionHeader("Courses of action", meta = "Ranked by intent match")
                KeyValue("Bearing", "042°")
            }
            ExtendedFab("Plan recon", WingmanIcons.Route, onClick = {})
            WingmanNavigationBar(
                items = persistentListOf(
                    NavigationItem("Map", WingmanIcons.Map),
                    NavigationItem("Intel", WingmanIcons.Hub),
                    NavigationItem("Plan", WingmanIcons.Route),
                    NavigationItem("Mission", WingmanIcons.Drone),
                    NavigationItem("Sync", WingmanIcons.Sync),
                ),
                selectedIndex = 0,
                onSelect = {},
            )
        }
    }
}
