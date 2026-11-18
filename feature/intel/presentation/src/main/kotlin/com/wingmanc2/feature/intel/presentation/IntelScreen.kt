package com.wingmanc2.feature.intel.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wingmanc2.core.designsystem.component.ExtendedFab
import com.wingmanc2.core.designsystem.component.Overline
import com.wingmanc2.core.designsystem.component.PredicateChip
import com.wingmanc2.core.designsystem.component.SectionHeader
import com.wingmanc2.core.designsystem.component.TintChip
import com.wingmanc2.core.designsystem.component.WingmanCard
import com.wingmanc2.core.designsystem.icon.WingmanIcons
import com.wingmanc2.core.designsystem.theme.MonoFamily
import com.wingmanc2.core.designsystem.theme.WingmanShapes
import com.wingmanc2.core.designsystem.theme.WingmanSpacing
import com.wingmanc2.core.designsystem.theme.WingmanTheme
import com.wingmanc2.core.model.EntityGraph
import com.wingmanc2.core.ui.WingmanScreen
import com.wingmanc2.core.ui.graph.EntityGraphView
import kotlinx.collections.immutable.ImmutableList

private val GraphHeight = 238.dp
private val QueryBarHeight = 48.dp
private val LineageRowHeight = 32.dp
private val FormatColumnWidth = 96.dp
private val FabClearance = 56.dp + WingmanSpacing.margin * 2

@Composable
internal fun IntelScreen(
    contentPadding: PaddingValues,
    onPlanRecon: () -> Unit,
    viewModel: IntelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WingmanScreen(title = "Intel", subtitle = "NOVA knowledge graph · on-device") {
        when (val current = state) {
            IntelUiState.Loading -> Unit
            is IntelUiState.Ready -> IntelContent(
                state = current,
                query = viewModel.query,
                onNodeClick = viewModel::onFocus,
                onPlanRecon = {
                    current.focusId?.let(viewModel::onFocus)
                    onPlanRecon()
                },
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun ColumnScope.IntelContent(
    state: IntelUiState.Ready,
    query: TextFieldState,
    onNodeClick: (Long) -> Unit,
    onPlanRecon: () -> Unit,
    contentPadding: PaddingValues,
) {
    val bottom = contentPadding.calculateBottomPadding()
    Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottom + FabClearance),
        ) {
            item(key = "query", contentType = "query") {
                QueryBar(query, state.latency, Modifier.padding(horizontal = WingmanSpacing.margin))
            }
            item(key = "predicates", contentType = "predicates") {
                Interpretation(state.predicates, Modifier.padding(top = 10.dp, bottom = WingmanSpacing.sm))
            }
            item(key = "graph", contentType = "graph") {
                GraphCard(state.graphMeta, state.graph, onNodeClick, Modifier.padding(horizontal = WingmanSpacing.margin))
            }
            val lineage = state.lineage
            if (lineage != null) {
                item(key = "lineage", contentType = "lineage") {
                    LineageCard(lineage, Modifier.padding(start = WingmanSpacing.margin, end = WingmanSpacing.margin, top = 10.dp))
                }
            }
        }
        PlanReconFab(
            visible = state.focusId != null,
            onClick = onPlanRecon,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = WingmanSpacing.margin, bottom = bottom + WingmanSpacing.margin),
        )
    }
}

@Composable
private fun PlanReconFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        ExtendedFab(text = "Plan recon", icon = WingmanIcons.Route, onClick = onClick)
    }
}

@Composable
private fun QueryBar(query: TextFieldState, latency: String, modifier: Modifier = Modifier) {
    val colors = WingmanTheme.colors
    val typography = WingmanTheme.typography
    val keyboard = LocalSoftwareKeyboardController.current
    val textStyle = remember(typography, colors) { typography.body.copy(color = colors.text) }
    val latencyStyle = remember(typography) { typography.label.copy(fontFamily = MonoFamily) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(QueryBarHeight)
            .background(colors.surface2, WingmanShapes.pill)
            .border(1.dp, colors.ai, WingmanShapes.pill)
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(WingmanIcons.Search, contentDescription = null, tint = colors.ai, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(WingmanSpacing.sm))
        BasicTextField(
            state = query,
            modifier = Modifier.weight(1f),
            textStyle = textStyle,
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(colors.ai),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { keyboard?.hide() },
        )
        Spacer(Modifier.width(WingmanSpacing.grid))
        Text(latency, style = latencyStyle, color = colors.muted, maxLines = 1)
    }
}

@Composable
private fun Interpretation(predicates: ImmutableList<String>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WingmanSpacing.xxs)) {
        Overline("NOVA interpreted as", Modifier.padding(horizontal = WingmanSpacing.margin))
        LazyRow(
            contentPadding = PaddingValues(horizontal = WingmanSpacing.margin),
            horizontalArrangement = Arrangement.spacedBy(WingmanSpacing.xs),
        ) {
            items(predicates, key = { it }) { PredicateChip(it) }
        }
    }
}

@Composable
private fun GraphCard(meta: String, graph: EntityGraph?, onNodeClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    WingmanCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(top = WingmanSpacing.margin, bottom = 2.dp)) {
        SectionHeader("Entity graph", Modifier.padding(horizontal = WingmanSpacing.margin), meta = meta)
        Spacer(Modifier.height(WingmanSpacing.grid))
        if (graph != null) {
            EntityGraphView(graph, onNodeClick, Modifier.fillMaxWidth().height(GraphHeight))
        } else {
            Box(Modifier.fillMaxWidth().height(GraphHeight), contentAlignment = Alignment.Center) {
                Text("No entity matches this query", style = WingmanTheme.typography.body, color = WingmanTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun LineageCard(lineage: LineageUi, modifier: Modifier = Modifier) {
    WingmanCard(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = WingmanSpacing.margin, end = WingmanSpacing.margin, top = WingmanSpacing.margin, bottom = WingmanSpacing.sm),
    ) {
        SectionHeader("Source lineage", meta = lineage.entityKey)
        Spacer(Modifier.height(WingmanSpacing.sm))
        if (lineage.rows.isEmpty()) {
            Text(
                "No raw source observations",
                Modifier.height(LineageRowHeight).padding(top = WingmanSpacing.xs),
                style = WingmanTheme.typography.bodySmall,
                color = WingmanTheme.colors.muted,
            )
        }
        for (row in lineage.rows) {
            key(row.id) { ObservationRow(row) }
        }
        Spacer(Modifier.height(WingmanSpacing.xxs))
        FusedResult(lineage.fusedTitle, lineage.fusedDetail)
    }
}

@Composable
private fun ObservationRow(row: LineageRow) {
    val colors = WingmanTheme.colors
    val typography = WingmanTheme.typography
    val chipStyle = remember(typography) { typography.tag.copy(fontSize = 10.sp) }
    Row(Modifier.fillMaxWidth().height(LineageRowHeight), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(FormatColumnWidth)) {
            TintChip(row.format, colors.text2, mono = true, height = 22.dp, textStyle = chipStyle, containerColor = colors.surface3)
        }
        Text(row.payload, Modifier.weight(1f), style = typography.dataSmall, color = colors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(thickness = 1.dp, color = colors.line)
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun FusedResult(title: String, detail: String) {
    val colors = WingmanTheme.colors
    val typography = WingmanTheme.typography
    val titleStyle = remember(typography) { typography.bodySmall.copy(fontWeight = FontWeight.SemiBold) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.ok.copy(alpha = 0.10f), FusedShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(WingmanIcons.Check, contentDescription = null, tint = colors.ok, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(WingmanSpacing.xs))
        Column {
            Text(title, style = titleStyle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = typography.chipMono, color = colors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val FusedShape = RoundedCornerShape(10.dp)
