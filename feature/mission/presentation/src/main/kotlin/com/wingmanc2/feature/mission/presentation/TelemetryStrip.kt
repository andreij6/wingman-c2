package com.wingmanc2.feature.mission.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.component.KeyValue
import com.wingmanc2.core.designsystem.component.WingmanCard
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.ui.of

@Composable
internal fun TelemetryStrip(telemetry: () -> TelemetryUi?, modifier: Modifier = Modifier) {
    val values = telemetry() ?: return
    val text = WingmanTheme.colors.text
    WingmanCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = WingmanSpacing.sm)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            TelemetryCell("Alt AGL", values.altitude, text, values.climb)
            CellDivider()
            TelemetryCell("Speed", values.speed, text, values.heading)
            CellDivider()
            TelemetryCell("Battery", values.battery, WingmanTheme.colors.of(values.batteryTone), values.reserve)
            CellDivider()
            TelemetryCell("Link", values.link, WingmanTheme.colors.of(values.linkTone), values.linkDetail)
        }
    }
}

@Composable
private fun RowScope.TelemetryCell(label: String, value: String, valueColor: Color, detail: String) {
    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
        KeyValue(label, value, valueColor = valueColor)
        Spacer(Modifier.height(WingmanSpacing.xs))
        Text(detail, style = WingmanTheme.typography.caption, color = WingmanTheme.colors.muted, maxLines = 1)
    }
}

@Composable
private fun CellDivider() {
    Box(
        Modifier
            .padding(vertical = WingmanSpacing.xxs)
            .width(1.dp)
            .fillMaxHeight()
            .background(WingmanTheme.colors.line),
    )
}
