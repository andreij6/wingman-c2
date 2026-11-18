package com.wingmanc2.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanTheme

enum class ChipLeading { None, Dot, Check }

@Composable
fun TintChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    leading: ChipLeading = ChipLeading.None,
    mono: Boolean = false,
    height: Dp = 28.dp,
    textStyle: TextStyle = if (mono) WingmanTheme.typography.chipMono else WingmanTheme.typography.chip,
    containerColor: Color = color.copy(alpha = 0.14f),
) {
    Row(
        modifier = modifier
            .height(height)
            .background(containerColor, WingmanShapes.pill)
            .padding(start = leading.startPadding, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (leading) {
            ChipLeading.None -> Unit
            ChipLeading.Dot -> {
                StatusDot(color)
                Spacer(Modifier.width(6.5.dp))
            }
            ChipLeading.Check -> {
                Icon(WingmanIcons.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
            }
        }
        Text(text, style = textStyle, color = color, maxLines = 1)
    }
}

private val ChipLeading.startPadding: Dp
    get() = when (this) {
        ChipLeading.None -> 10.dp
        ChipLeading.Dot -> 10.5.dp
        ChipLeading.Check -> 8.dp
    }

@Composable
fun PredicateChip(text: String, modifier: Modifier = Modifier) {
    TintChip(text = text, color = WingmanTheme.colors.ai, modifier = modifier, mono = true, height = 26.dp)
}

@Composable
fun AffiliationFilterChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WingmanTheme.colors
    val style = WingmanTheme.typography.label.copy(fontSize = 12.5.sp)
    val shape = if (selected) WingmanShapes.pill else FilterShape
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(colors.primary.copy(alpha = 0.18f)) else Modifier.border(1.dp, colors.line, shape))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (selected) 8.dp else 11.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(WingmanIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = style, fontWeight = FontWeight.SemiBold, color = colors.primary, maxLines = 1)
        } else {
            StatusDot(color, size = 8.dp)
            Spacer(Modifier.width(7.dp))
            Text(label, style = style, color = colors.text2, maxLines = 1)
        }
    }
}

private val FilterShape = RoundedCornerShape(8.dp)
