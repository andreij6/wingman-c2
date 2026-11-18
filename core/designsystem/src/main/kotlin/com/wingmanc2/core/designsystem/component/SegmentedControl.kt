package com.wingmanc2.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SegmentedControl(
    options: ImmutableList<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = WingmanTheme.colors.primary,
) {
    val colors = WingmanTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(WingmanShapes.pill)
            .border(1.dp, colors.line, WingmanShapes.pill)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) Spacer(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selected) Icon(WingmanIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Text(
                        text = option,
                        style = WingmanTheme.typography.control,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) accent else colors.text2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
