package com.wingmanc2.core.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.component.AffiliationGlyphs
import com.wingmanc2.core.designsystem.component.MapTagScrimAlpha
import com.wingmanc2.core.designsystem.component.rememberAffiliationGlyphs
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.designsystem.theme.WingmanTheme

@Stable
internal class MapPaint(
    val colors: WingmanColors,
    val glyphs: AffiliationGlyphs,
    val assetGlyphs: AffiliationGlyphs,
    private val measurer: TextMeasurer,
    private val tagStyle: TextStyle,
    private val waypointStyle: TextStyle,
    private val calloutTitleStyle: TextStyle,
    private val calloutDetailStyle: TextStyle,
    val density: Float,
) {
    val hairline = Stroke(width = dp(1f))
    val selectionRing = Stroke(width = dp(1.5f))
    val tagHeight = dp(18f)
    private val tagPadding = dp(5f)
    private val tagCorner = CornerRadius(dp(4f))

    fun dp(value: Float) = value * density

    fun stroke(widthDp: Float, pattern: StrokePattern) = Stroke(
        width = dp(widthDp),
        cap = if (pattern == StrokePattern.Solid) StrokeCap.Round else StrokeCap.Butt,
        join = StrokeJoin.Round,
        pathEffect = if (pattern == StrokePattern.Solid) null else PathEffect.dashPathEffect(floatArrayOf(dp(pattern.onDp), dp(pattern.offDp))),
    )

    fun tag(text: String, color: Color) = measurer.measure(text, tagStyle.copy(color = color), maxLines = 1)

    fun waypointLabel(number: Int, color: Color) = measurer.measure(number.toString(), waypointStyle.copy(color = color), maxLines = 1)

    fun markerLabel(text: String) = measurer.measure(text, waypointStyle.copy(color = colors.background, fontSize = 10.5.sp), maxLines = 1)

    fun calloutTitle(text: String, color: Color) = measurer.measure(text, calloutTitleStyle.copy(color = color), maxLines = 1)

    fun calloutDetail(text: String) = measurer.measure(text, calloutDetailStyle.copy(color = colors.text2), maxLines = 1)

    fun tagWidth(layout: TextLayoutResult) = layout.size.width + 2 * tagPadding

    fun DrawScope.drawTag(layout: TextLayoutResult, left: Float, top: Float) {
        drawRoundRect(colors.scrim, Offset(left, top), Size(tagWidth(layout), tagHeight), tagCorner, alpha = MapTagScrimAlpha)
        drawText(layout, topLeft = Offset(left + tagPadding, top + (tagHeight - layout.size.height) / 2f))
    }

    fun DrawScope.drawCentered(layout: TextLayoutResult, center: Offset) {
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
    }
}

@Composable
internal fun rememberMapPaint(): MapPaint {
    val colors = WingmanTheme.colors
    val typography = WingmanTheme.typography
    val density = LocalDensity.current.density
    val glyphs = rememberAffiliationGlyphs(14.dp)
    val assetGlyphs = rememberAffiliationGlyphs(16.dp)
    val measurer = rememberTextMeasurer(cacheSize = 48)
    return remember(colors, typography, density, glyphs, assetGlyphs, measurer) {
        MapPaint(
            colors = colors,
            glyphs = glyphs,
            assetGlyphs = assetGlyphs,
            measurer = measurer,
            tagStyle = typography.tag,
            waypointStyle = typography.tag.copy(fontSize = 9.sp),
            calloutTitleStyle = typography.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            calloutDetailStyle = typography.caption.copy(fontSize = 10.sp),
            density = density,
        )
    }
}
