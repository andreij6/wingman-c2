package com.wingmanc2.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.theme.WingmanTheme

const val MapTagScrimAlpha = 0.78f

@Composable
fun MapTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(WingmanTheme.colors.scrim.copy(alpha = MapTagScrimAlpha), MapTagShape)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        style = WingmanTheme.typography.tag,
        color = color,
        maxLines = 1,
    )
}

private val MapTagShape = RoundedCornerShape(4.dp)
