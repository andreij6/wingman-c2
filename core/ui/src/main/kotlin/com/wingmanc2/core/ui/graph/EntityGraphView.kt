package com.wingmanc2.core.ui.graph

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingmanc2.core.designsystem.component.rememberAffiliationGlyphs
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.OntologyEntity
import com.wingmanc2.core.model.RelationType
import java.util.Locale
import com.wingmanc2.core.ui.entityColor

private const val FOCUS_RADIUS_DP = 24f
private const val NODE_RADIUS_DP = 20f
private const val HALO_DP = 8f

@Composable
fun EntityGraphView(
    graph: EntityGraph,
    onNodeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxNeighbors: Int = 4,
    maxGhosts: Int = 6,
) {
    val layout = remember(graph, maxNeighbors, maxGhosts) { GraphLayout.of(graph, maxNeighbors, maxGhosts) } ?: return
    val colors = WingmanTheme.colors
    val typography = WingmanTheme.typography
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val glyphs = rememberAffiliationGlyphs(12.dp)
    val focusGlyphs = rememberAffiliationGlyphs(13.dp)
    val titleStyle = remember(typography, colors) { typography.tag.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.text) }
    val subtitleStyle = remember(typography, colors) { typography.caption.copy(color = colors.muted) }
    val edgeStyle = remember(typography) { typography.tag.copy(fontSize = 9.5.sp) }
    val currentOnNodeClick by rememberUpdatedState(onNodeClick)

    Spacer(
        modifier
            .semantics { contentDescription = "Entity graph focused on ${layout.focus.key}, ${layout.nodes.size - 1} related entities" }
            .pointerInput(layout) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val reach = (NODE_RADIUS_DP + HALO_DP) * density
                    for (i in layout.nodes.indices) {
                        val dx = layout.x(i, w) - tap.x
                        val dy = layout.y(i, h) - tap.y
                        if (dx * dx + dy * dy <= reach * reach) {
                            currentOnNodeClick(layout.nodes[i].id)
                            break
                        }
                    }
                }
            }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val n = layout.nodes.size
                val xs = FloatArray(n) { layout.x(it, w) }
                val ys = FloatArray(n) { layout.y(it, h) }
                val nodeColors = Array(n) { colors.entityColor(layout.nodes[it].kind, layout.nodes[it].affiliation) }
                val titles = Array(n) { measurer.measure(layout.nodes[it].key, titleStyle, maxLines = 1) }
                val subtitles = Array(n) { measurer.measure(if (it == 0) layout.focus.focusSubtitle() else layout.nodes[it].subtitle.substringBefore(" · "), subtitleStyle, maxLines = 1) }
                val edgeLabels = Array(n - 1) { k ->
                    val edge = layout.edges[k]
                    measurer.measure(edge.label.ifBlank { edge.relation.name }, edgeStyle.copy(color = nodeColors[k + 1]), maxLines = 1)
                }
                val ghostCount = layout.ghostParents.size
                val gx = FloatArray(ghostCount) { layout.ghostX(it, w, density) }
                val gy = FloatArray(ghostCount) { layout.ghostY(it, h, density) }
                val edgeWidth = 1.5.dp.toPx()
                val inferredDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                val nodeStroke = Stroke(width = 1.5.dp.toPx())
                val focusStroke = Stroke(width = 2.dp.toPx())
                val hairline = Stroke(width = 1.dp.toPx())
                val pillHeight = 18.dp.toPx()
                val pillPad = 5.dp.toPx()
                val pillCorner = CornerRadius(pillHeight / 2f)
                val labelGap = 4.dp.toPx()
                val labelLift = 36.dp.toPx()
                val subtitleOffset = 16.dp.toPx()
                val focusRadius = FOCUS_RADIUS_DP.dp.toPx()
                val nodeRadius = NODE_RADIUS_DP.dp.toPx()
                val halo = HALO_DP.dp.toPx()
                val ghostRadius = 4.dp.toPx()

                onDrawBehind {
                    for (g in 0 until ghostCount) {
                        val parent = layout.ghostParents[g]
                        drawLine(colors.line, Offset(gx[g], gy[g]), Offset(xs[parent], ys[parent]), hairline.width)
                    }
                    for (g in 0 until ghostCount) drawCircle(colors.surface3, ghostRadius, Offset(gx[g], gy[g]))
                    for (i in 1 until n) {
                        val dash = if (layout.edges[i - 1].relation == RelationType.INFERRED) inferredDash else null
                        drawLine(nodeColors[i], Offset(xs[i], ys[i]), Offset(xs[0], ys[0]), edgeWidth, pathEffect = dash, alpha = 0.7f)
                    }
                    for (i in 1 until n) {
                        val label = edgeLabels[i - 1]
                        val pillWidth = label.size.width + 2 * pillPad
                        val topLeft = Offset((xs[i] + xs[0]) / 2f - pillWidth / 2f, (ys[i] + ys[0]) / 2f - pillHeight / 2f)
                        val pillSize = Size(pillWidth, pillHeight)
                        drawRoundRect(colors.surface, topLeft, pillSize, pillCorner)
                        drawRoundRect(colors.line, topLeft, pillSize, pillCorner, style = hairline)
                        drawText(label, topLeft = Offset(topLeft.x + pillPad, topLeft.y + (pillHeight - label.size.height) / 2f))
                    }
                    for (i in 0 until n) {
                        val center = Offset(xs[i], ys[i])
                        val focused = i == 0
                        val radius = if (focused) focusRadius else nodeRadius
                        if (focused) drawCircle(nodeColors[i], radius + halo, center, alpha = 0.10f)
                        drawCircle(colors.surface2, radius, center)
                        drawCircle(nodeColors[i], radius, center, style = if (focused) focusStroke else nodeStroke)
                        val node = layout.nodes[i]
                        (if (focused) focusGlyphs else glyphs).draw(this, center, node.affiliation, node.kind, node.kind == EntityKind.ASSET)
                        val titleTop = if (layout.isAboveFocus(i)) center.y - radius - labelLift else center.y + radius + labelGap
                        drawText(titles[i], topLeft = Offset(center.x - titles[i].size.width / 2f, titleTop))
                        drawText(subtitles[i], topLeft = Offset(center.x - subtitles[i].size.width / 2f, titleTop + subtitleOffset))
                    }
                }
            },
    )
}

private fun OntologyEntity.focusSubtitle(): String =
    "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${String.format(Locale.ROOT, "%.2f", confidence)}"
