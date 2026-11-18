package com.wingmanc2.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, meta: String? = null, metaColor: Color = WingmanTheme.colors.muted) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.grid),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = WingmanTheme.typography.cardTitle, color = WingmanTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (meta != null) Text(meta, style = WingmanTheme.typography.label, fontWeight = FontWeight.Normal, color = metaColor, maxLines = 1)
    }
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = WingmanTheme.colors.muted) {
    val caps = remember(text) { text.uppercase() }
    Text(caps, modifier, style = WingmanTheme.typography.overline, color = color, maxLines = 1)
}

@Composable
fun KeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = WingmanTheme.colors.text,
    valueStyle: TextStyle = WingmanTheme.typography.data,
) {
    val caps = remember(label) { label.uppercase() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(WingmanSpacing.xxs)) {
        Text(caps, style = WingmanTheme.typography.keyLabel, color = WingmanTheme.colors.muted, maxLines = 1)
        Text(value, style = valueStyle, color = valueColor, maxLines = 1)
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}
