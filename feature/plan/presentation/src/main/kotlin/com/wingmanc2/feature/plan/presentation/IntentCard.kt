package com.wingmanc2.feature.plan.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingmanc2.core.designsystem.component.ButtonVariant
import com.wingmanc2.core.designsystem.component.ChipLeading
import com.wingmanc2.core.designsystem.component.Overline
import com.wingmanc2.core.designsystem.component.TintChip
import com.wingmanc2.core.designsystem.component.WingmanButton
import com.wingmanc2.core.designsystem.component.WingmanCard
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun IntentCard(intent: IntentUi, onApply: (String) -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val apply = {
        if (draft.text.isNotBlank()) {
            onApply(draft.text)
            editing = false
        }
    }
    WingmanCard(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        contentPadding = PaddingValues(horizontal = WingmanSpacing.margin, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Commander's intent", Modifier.weight(1f))
            if (!editing) {
                EditAction(onClick = {
                    draft = TextFieldValue(intent.text, TextRange(intent.text.length))
                    editing = true
                })
            }
        }
        Spacer(Modifier.height(WingmanSpacing.xs))
        if (editing) {
            IntentEditor(draft = draft, onDraftChange = { draft = it }, onCancel = { editing = false }, onApply = apply)
        } else {
            Text(intent.quoted, style = IntentTextStyle(), color = WingmanTheme.colors.text)
        }
        Spacer(Modifier.height(10.dp))
        ConstraintChips(intent.constraints)
    }
}

@Composable
private fun IntentTextStyle() = WingmanTheme.typography.body.copy(fontSize = 14.5.sp, lineHeight = 20.sp)

@Composable
private fun EditAction(onClick: () -> Unit) {
    val primary = WingmanTheme.colors.primary
    Row(
        modifier = Modifier
            .clip(WingmanShapes.pill)
            .clickable(role = Role.Button, onClickLabel = "Edit commander's intent", onClick = onClick)
            .padding(horizontal = WingmanSpacing.xxs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(WingmanIcons.Edit, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(WingmanSpacing.xxs))
        Text("Edit", style = WingmanTheme.typography.control, fontWeight = FontWeight.SemiBold, color = primary)
    }
}

@Composable
private fun IntentEditor(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val colors = WingmanTheme.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    BasicTextField(
        value = draft,
        onValueChange = onDraftChange,
        textStyle = IntentTextStyle().copy(color = colors.text),
        cursorBrush = SolidColor(colors.primary),
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onApply() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .clip(WingmanShapes.small)
            .background(colors.surface2)
            .border(1.dp, colors.primary.copy(alpha = 0.5f), WingmanShapes.small)
            .padding(WingmanSpacing.sm),
    )
    Spacer(Modifier.height(WingmanSpacing.grid))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.grid, Alignment.End)) {
        WingmanButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.Outlined, height = 36.dp, textStyle = WingmanTheme.typography.control)
        WingmanButton(text = "Apply", onClick = onApply, enabled = draft.text.isNotBlank(), height = 36.dp, textStyle = WingmanTheme.typography.control)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConstraintChips(constraints: ImmutableList<String>) {
    if (constraints.isEmpty()) {
        Text("No constraints parsed · doctrine defaults apply", style = WingmanTheme.typography.bodySmall, color = WingmanTheme.colors.muted)
        return
    }
    val chipStyle = WingmanTheme.typography.chip.copy(fontSize = 11.5.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.xs), verticalArrangement = Arrangement.spacedBy(WingmanSpacing.xs)) {
        constraints.forEach { constraint ->
            TintChip(text = constraint, color = WingmanTheme.colors.primary, leading = ChipLeading.Check, height = 26.dp, textStyle = chipStyle)
        }
    }
}
