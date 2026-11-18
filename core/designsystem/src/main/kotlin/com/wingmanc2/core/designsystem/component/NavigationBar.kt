package com.wingmanc2.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class NavigationItem(val label: String, val icon: ImageVector)

@Composable
fun WingmanNavigationBar(
    items: ImmutableList<NavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WingmanTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(colors.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .height(80.dp)
            .selectableGroup(),
    ) {
        items.forEachIndexed { index, item ->
            NavigationTab(item, selected = index == selectedIndex, onClick = { onSelect(index) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavigationTab(item: NavigationItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = WingmanTheme.colors
    val progress by animateFloatAsState(if (selected) 1f else 0f, label = "indicator")
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .size(64.dp, 32.dp)
                .drawBehind {
                    val width = size.width * (0.5f + 0.5f * progress)
                    drawRoundRect(
                        color = colors.primary,
                        topLeft = Offset((size.width - width) / 2f, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                        alpha = 0.18f * progress,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = if (selected) colors.primary else colors.text2, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label,
            style = WingmanTheme.typography.label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.text else colors.text2,
            maxLines = 1,
        )
    }
}
