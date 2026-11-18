package com.wingmanc2.feature.plan.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingmanc2.core.designsystem.component.KeyValue
import com.wingmanc2.core.designsystem.component.WingmanCard
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme

@Composable
internal fun ExpandedCoaCard(coa: CoaUi, modifier: Modifier = Modifier) {
    val accent = WingmanTheme.colors.accentOf(coa.tone)
    WingmanCard(modifier = modifier.fillMaxWidth(), borderColor = accent, borderWidth = 1.5.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterBadge(coa.letter, accent, filled = true)
            Spacer(Modifier.width(WingmanSpacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(coa.title, style = WingmanTheme.typography.cardTitle.copy(fontSize = 15.5.sp), color = WingmanTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(coa.headline, style = WingmanTheme.typography.label, color = accent, maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        CoaMetrics(coa, accent)
        Box(Modifier.padding(vertical = WingmanSpacing.sm).fillMaxWidth().height(1.dp).background(WingmanTheme.colors.line))
        Column(verticalArrangement = Arrangement.spacedBy(WingmanSpacing.xxs)) {
            coa.rationale.forEach { line -> RationaleLine(line, accent) }
        }
    }
}

@Composable
internal fun CollapsedCoaRow(coa: CoaUi, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val accent = WingmanTheme.colors.accentOf(coa.tone)
    val onClick = remember(coa.code, onSelect) { { onSelect(coa.code) } }
    WingmanCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = WingmanSpacing.margin, end = WingmanSpacing.sm, top = 11.dp, bottom = 11.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterBadge(coa.letter, accent, filled = false)
            Spacer(Modifier.width(WingmanSpacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(coa.title, style = WingmanTheme.typography.cardTitle.copy(fontSize = 14.5.sp), color = WingmanTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(coa.summary, style = WingmanTheme.typography.bodySmall.copy(fontSize = 12.sp), color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(WingmanIcons.ChevronRight, contentDescription = null, tint = WingmanTheme.colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CoaMetrics(coa: CoaUi, accent: Color) {
    val valueStyle = WingmanTheme.typography.data.copy(fontSize = 14.sp)
    Row(Modifier.fillMaxWidth()) {
        KeyValue("Match", coa.match, Modifier.weight(1f), valueColor = accent, valueStyle = valueStyle)
        KeyValue("Risk", coa.risk, Modifier.weight(1f), valueColor = accent, valueStyle = valueStyle)
        KeyValue("ETA", coa.eta, Modifier.weight(1f), valueStyle = valueStyle)
        KeyValue("Reserve", coa.reserve, Modifier.weight(1f), valueStyle = valueStyle)
    }
}

@Composable
private fun RationaleLine(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(WingmanIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(WingmanSpacing.grid))
        Text(text, style = WingmanTheme.typography.label, fontWeight = FontWeight.Normal, color = WingmanTheme.colors.text2)
    }
}

@Composable
private fun LetterBadge(letter: String, color: Color, filled: Boolean) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(28.dp)
            .then(if (filled) Modifier.background(color, shape) else Modifier.border(1.5.dp, color, shape)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = WingmanTheme.typography.label.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            color = if (filled) WingmanTheme.colors.background else color,
        )
    }
}
