package com.wingmanc2.feature.battlespace.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingmanc2.core.designsystem.component.TintChip
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme

@Composable
internal fun NovaSearchBar(
    query: TextFieldState,
    matches: Int?,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WingmanTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface2, WingmanShapes.pill)
            .padding(start = 14.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.grid),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(WingmanIcons.Search, contentDescription = null, tint = colors.text2, modifier = Modifier.size(24.dp))
        QueryField(query, onSubmit, Modifier.weight(1f))
        if (matches == null) {
            NovaChip("NOVA", "Ask NOVA", onSubmit)
        } else {
            NovaChip(if (matches == 1) "1 hit  ×" else "$matches hits  ×", "Clear NOVA query", onClear)
        }
    }
}

@Composable
private fun QueryField(query: TextFieldState, onSubmit: () -> Unit, modifier: Modifier) {
    val colors = WingmanTheme.colors
    val style = WingmanTheme.typography.body
    BasicTextField(
        state = query,
        modifier = modifier,
        textStyle = style.copy(color = colors.text),
        cursorBrush = SolidColor(colors.primary),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { onSubmit() },
        decorator = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.text.isEmpty()) {
                    Text(
                        text = "Ask NOVA about the battlespace…",
                        style = style,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                field()
            }
        },
    )
}

@Composable
private fun NovaChip(text: String, actionLabel: String, onClick: () -> Unit) {
    TintChip(
        text = text,
        color = WingmanTheme.colors.ai,
        modifier = Modifier
            .clip(WingmanShapes.pill)
            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick),
        textStyle = WingmanTheme.typography.chip.copy(fontSize = 11.sp),
    )
}
