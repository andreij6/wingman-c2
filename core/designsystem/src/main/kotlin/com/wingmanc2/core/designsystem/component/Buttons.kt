package com.wingmanc2.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanTheme

enum class ButtonVariant { Filled, Tonal, Outlined, Danger }

@Immutable
private class ButtonColors(val container: Color, val content: Color, val border: Color)

private fun WingmanColors.forVariant(variant: ButtonVariant) = when (variant) {
    ButtonVariant.Filled -> ButtonColors(primary, onPrimary, Color.Transparent)
    ButtonVariant.Tonal -> ButtonColors(surface3, text, Color.Transparent)
    ButtonVariant.Outlined -> ButtonColors(Color.Transparent, primary, line)
    ButtonVariant.Danger -> ButtonColors(critical.copy(alpha = 0.16f), critical, Color.Transparent)
}

@Composable
fun WingmanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    textStyle: TextStyle = WingmanTheme.typography.button,
) {
    val colors = WingmanTheme.colors.forVariant(variant)
    Row(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(WingmanShapes.pill)
            .background(colors.container)
            .border(1.dp, colors.border, WingmanShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = textStyle, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ExtendedFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WingmanTheme.colors
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(FabShape)
            .background(colors.primary)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = WingmanTheme.typography.cardTitle, color = colors.onPrimary, maxLines = 1)
    }
}

private val FabShape = RoundedCornerShape(16.dp)
