package com.wingmanc2.feature.plan.presentation

import androidx.compose.runtime.Immutable
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.feature.plan.domain.DEFAULT_TARGET_KEY
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal enum class CoaTone { Recommended, Exposed, Neutral }

@Immutable
internal data class IntentUi(
    val text: String,
    val quoted: String,
    val constraints: ImmutableList<String>,
)

@Immutable
internal data class CoaUi(
    val code: String,
    val letter: String,
    val title: String,
    val tone: CoaTone,
    val headline: String,
    val match: String,
    val risk: String,
    val eta: String,
    val reserve: String,
    val summary: String,
    val rationale: ImmutableList<String>,
)

@Immutable
internal data class RouteLineUi(
    val code: String,
    val letter: String,
    val tone: CoaTone,
    val points: ImmutableList<LocalPoint>,
    val orbitRadiusM: Double?,
)

@Immutable
internal data class RoutePreviewUi(
    val asset: LocalPoint,
    val targetId: Long,
    val targetAffiliation: Affiliation,
    val targetKind: EntityKind,
    val target: LocalPoint,
    val threat: LocalPoint?,
    val threatRadiusM: Double,
    val routes: ImmutableList<RouteLineUi>,
)

@Immutable
internal data class PlanResultUi(
    val preview: RoutePreviewUi,
    val courses: ImmutableList<CoaUi>,
)

@Immutable
internal data class PlanUiState(
    val targetKey: String,
    val intent: IntentUi?,
    val planning: Boolean,
    val preview: RoutePreviewUi?,
    val courses: ImmutableList<CoaUi>,
    val selectedCode: String?,
    val dispatching: Boolean,
) {
    val canDispatch: Boolean get() = selectedCode != null && !planning && !dispatching

    companion object {
        val Initial = PlanUiState(
            targetKey = DEFAULT_TARGET_KEY,
            intent = null,
            planning = true,
            preview = null,
            courses = persistentListOf(),
            selectedCode = null,
            dispatching = false,
        )
    }
}
