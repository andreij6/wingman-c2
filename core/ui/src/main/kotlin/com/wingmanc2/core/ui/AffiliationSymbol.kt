package com.wingmanc2.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind

@Stable
class AffiliationGlyphs internal constructor(sizePx: Float, density: Float, private val colors: WingmanColors, private val drone: Painter) {
    val extentPx: Float = sizePx * 0.75f

    private val s = sizePx
    private val frame = Stroke(width = 1.5f * density)
    private val zoneStroke = Stroke(width = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * density, 2f * density)))
    private val droneTint = ColorFilter.tint(colors.background)
    private val droneSize = Size(0.84f * s, 0.84f * s)
    private val small = CornerRadius(2f * density)
    private val tiny = CornerRadius(1.5f * density)

    private val friendGround = Path().apply { addRoundRect(RoundRect(-0.72f * s, -0.5f * s, 0.72f * s, 0.5f * s, small)) }

    private val friendAir = Path().apply {
        val half = 0.75f * s
        val dome = CornerRadius(half)
        addRoundRect(RoundRect(Rect(-half, -0.55f * s, half, 0.55f * s), dome, dome, small, small))
    }

    private val hostile = Path().apply {
        val half = 0.475f * s
        addRoundRect(RoundRect(-half, -half, half, half, tiny))
        transform(Matrix().apply { rotateZ(45f) })
    }

    private val unknown = Path().apply {
        val r = 0.3f * s
        val o = r * 0.85f
        addOval(Rect(Offset(-o, 0f), r))
        addOval(Rect(Offset(o, 0f), r))
        addOval(Rect(Offset(0f, -o), r))
        addOval(Rect(Offset(0f, o), r))
        addRect(Rect(-r, -r, r, r))
    }

    private val neutral = Path().apply { addRoundRect(RoundRect(-0.5f * s, -0.5f * s, 0.5f * s, 0.5f * s, tiny)) }

    private val zone = Path().apply { addRoundRect(RoundRect(-0.5f * s, -0.5f * s, 0.5f * s, 0.5f * s, small)) }

    fun draw(scope: DrawScope, center: Offset, affiliation: Affiliation, kind: EntityKind, isAir: Boolean) {
        scope.translate(center.x, center.y) {
            when {
                kind == EntityKind.ZONE -> drawPath(zone, colors.warn, style = zoneStroke)
                kind == EntityKind.CLASS -> drawCircle(colors.ai, radius = 0.36f * s, center = Offset.Zero)
                affiliation == Affiliation.FRIEND && isAir -> {
                    drawPath(friendAir, colors.friend)
                    drawPath(friendAir, colors.background, style = frame)
                    translate(-0.42f * s, -0.32f * s) { with(drone) { draw(droneSize, colorFilter = droneTint) } }
                }
                affiliation == Affiliation.FRIEND -> framed(friendGround, colors.friend)
                affiliation == Affiliation.HOSTILE -> framed(hostile, colors.hostile)
                affiliation == Affiliation.NEUTRAL -> framed(neutral, colors.neutral)
                else -> drawPath(unknown, colors.unknown)
            }
        }
    }

    private fun DrawScope.framed(path: Path, fill: Color) {
        drawPath(path, fill)
        drawPath(path, colors.background, style = frame)
    }
}

@Composable
fun rememberAffiliationGlyphs(size: Dp): AffiliationGlyphs {
    val density = LocalDensity.current
    val colors = WingmanTheme.colors
    val drone = rememberVectorPainter(WingmanIcons.Drone)
    return remember(size, density, colors, drone) {
        AffiliationGlyphs(with(density) { size.toPx() }, density.density, colors, drone)
    }
}

@Composable
fun AffiliationSymbol(
    affiliation: Affiliation,
    modifier: Modifier = Modifier,
    kind: EntityKind = EntityKind.TRACK,
    isAir: Boolean = false,
    size: Dp = 14.dp,
) {
    val glyphs = rememberAffiliationGlyphs(size)
    Spacer(
        modifier
            .size(size * 1.5f)
            .semantics { contentDescription = "${affiliation.name.lowercase()} ${kind.name.lowercase()}" }
            .drawBehind { glyphs.draw(this, center, affiliation, kind, isAir) },
    )
}
