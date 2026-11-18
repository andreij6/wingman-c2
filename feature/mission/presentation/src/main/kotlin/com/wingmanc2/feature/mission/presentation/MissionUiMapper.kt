package com.wingmanc2.feature.mission.presentation

import com.wingmanc2.core.common.formatTPlus
import com.wingmanc2.core.model.AutonomyDecision
import com.wingmanc2.core.model.DecisionKind
import com.wingmanc2.core.model.LinkMode
import com.wingmanc2.core.model.LinkState
import com.wingmanc2.core.model.LocalPoint
import com.wingmanc2.core.model.MissionStatus
import com.wingmanc2.core.model.TelemetryFrame
import com.wingmanc2.core.ui.StatusTone
import com.wingmanc2.core.ui.label
import com.wingmanc2.core.ui.map.MapBounds
import com.wingmanc2.core.ui.map.MapCallout
import com.wingmanc2.core.ui.map.MapFraming
import com.wingmanc2.core.ui.map.MapLayers
import com.wingmanc2.core.ui.map.MapLiveAsset
import com.wingmanc2.core.ui.map.MapRoute
import com.wingmanc2.core.ui.map.MapSymbol
import com.wingmanc2.core.ui.map.MapThreat
import com.wingmanc2.core.ui.map.MapWaypoint
import com.wingmanc2.core.ui.map.StrokePattern
import com.wingmanc2.core.ui.map.TagPlacement
import com.wingmanc2.feature.mission.domain.LiveTelemetry
import com.wingmanc2.feature.mission.domain.MissionPicture
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale
import kotlin.math.roundToInt

internal data class RouteProgress(val index: Int, val count: Int)

private const val FRAME_MARGIN_M = 120.0
private const val FRAME_PADDING_DP = 32f
private const val BATTERY_CAUTION_MARGIN_PCT = 20

internal fun MissionPicture?.toUiState(progress: RouteProgress?, userMessage: String?): MissionUiState =
    this?.toActive(progress, userMessage) ?: MissionUiState.NoMission

internal fun LiveTelemetry?.toLiveUi(picture: MissionPicture, link: LinkState, trail: ImmutableList<LocalPoint>) = MissionLiveUi(
    telemetry = this?.frame.toTelemetryUi(picture.reserveFloorPct, link),
    clock = this?.let { formatTPlus(it.frame.tMs - picture.startedAtMs) },
    asset = this?.let { MapLiveAsset(picture.assetId, it.position, it.frame.headingDeg, trail = trail) },
)

private fun MissionPicture.toActive(progress: RouteProgress?, userMessage: String?): MissionUiState.Active {
    val waypoints = waypoints(progress?.count)
    val active = (progress?.index ?: 0).coerceIn(0, waypoints.size)
    val holding = status == MissionStatus.LOITER
    return MissionUiState.Active(
        subtitle = listOf(coaCode, assetKey, progressLabel(progress, active, waypoints.size)).filter(String::isNotEmpty).joinToString(" · "),
        status = status.label(),
        statusTone = status.tone(),
        layers = layers(waypoints, active),
        framing = framing(),
        decisions = decisions.map(AutonomyDecision::toUi).toImmutableList(),
        decisionsMeta = "Explainable · ${decisions.size}",
        holdLabel = if (holding) "Resume" else "Hold / loiter",
        holding = holding,
        canCommand = status != MissionStatus.RTB && status != MissionStatus.COMPLETE,
        userMessage = userMessage,
    )
}

private fun MissionPicture.progressLabel(progress: RouteProgress?, active: Int, count: Int): String = when {
    status == MissionStatus.RTB -> "returning to base"
    status == MissionStatus.COMPLETE -> "complete"
    progress == null -> ""
    active >= count -> "on station"
    else -> "waypoint ${active + 1} of $count"
}

private fun MissionPicture.layers(waypoints: List<LocalPoint>, active: Int): MapLayers {
    val marked = listOfNotNull(target) + threats
    return MapLayers(
        threats = marked.filter { it.threatRadiusM > 0 }.map { MapThreat(it.key, it.position, it.threatRadiusM) }.toImmutableList(),
        routes = routes(waypoints, active),
        waypoints = waypoints.mapIndexed { i, p -> MapWaypoint(i + 1, p, done = i < active, target = i == waypoints.lastIndex) }
            .toImmutableList(),
        symbols = marked.map { MapSymbol(it.id, it.affiliation, it.position, it.kind, tag = it.key, tagPlacement = TagPlacement.Below) }
            .toImmutableList(),
        callouts = rerouteCallout(waypoints, active),
    )
}

private fun MissionPicture.routes(waypoints: List<LocalPoint>, active: Int): ImmutableList<MapRoute> {
    val flown = buildList {
        home?.let(::add)
        addAll(waypoints.subList(0, active))
    }
    val remaining = buildList {
        (if (active > 0) waypoints[active - 1] else home)?.let(::add)
        addAll(waypoints.subList(active, waypoints.size))
    }
    return listOfNotNull(
        flown.takeIf { it.size > 1 }?.let { MapRoute("flown", it.toImmutableList(), StatusTone.Friend, widthDp = 2.5f) },
        remaining.takeIf { it.size > 1 }
            ?.let { MapRoute("remaining", it.toImmutableList(), StatusTone.Secondary, widthDp = 1.5f, pattern = StrokePattern.Dashed) },
    ).toImmutableList()
}

private fun MissionPicture.rerouteCallout(waypoints: List<LocalPoint>, active: Int): ImmutableList<MapCallout> {
    val reroute = decisions.firstOrNull { it.kind == DecisionKind.REROUTE } ?: return persistentListOf()
    val end = waypoints.getOrNull(active) ?: waypoints.lastOrNull() ?: return persistentListOf()
    val start = waypoints.getOrNull(active - 1) ?: end
    val anchor = LocalPoint((start.x + end.x) / 2, (start.y + end.y) / 2)
    return persistentListOf(MapCallout(anchor, reroute.title, "autonomous · ${formatTPlus(reroute.tPlusMs)}"))
}

private fun MissionPicture.framing(): MapFraming {
    val rings = listOfNotNull(target) + threats
    val bounds = MapBounds.around(route + rings.map { it.position }) ?: return MapFraming.AreaOfOperations
    val framed = rings.fold(bounds) { acc, ring -> acc.including(ring.position.x, ring.position.y, ring.threatRadiusM) }
    return MapFraming.Fit(framed.expandedBy(FRAME_MARGIN_M), paddingDp = FRAME_PADDING_DP)
}

private fun AutonomyDecision.toUi() = DecisionUi(id, kind.tone(), formatTPlus(tPlusMs), title, rationale)

private fun DecisionKind.tone(): StatusTone = when (this) {
    DecisionKind.APPROVAL, DecisionKind.RESUME -> StatusTone.Ok
    DecisionKind.REROUTE, DecisionKind.HOLD -> StatusTone.Warn
    DecisionKind.CAPTURE -> StatusTone.Friend
    DecisionKind.REPLAN -> StatusTone.Ai
    DecisionKind.RTB, DecisionKind.THREAT -> StatusTone.Critical
}

private fun MissionStatus.label(): String = when (this) {
    MissionStatus.DRAFT -> "Draft"
    MissionStatus.EN_ROUTE -> "En route"
    MissionStatus.LOITER -> "Loiter"
    MissionStatus.REPLANNING -> "Replanning"
    MissionStatus.RTB -> "RTB"
    MissionStatus.COMPLETE -> "Complete"
}

private fun MissionStatus.tone(): StatusTone = when (this) {
    MissionStatus.EN_ROUTE -> StatusTone.Ok
    MissionStatus.LOITER -> StatusTone.Warn
    MissionStatus.REPLANNING -> StatusTone.Ai
    MissionStatus.RTB -> StatusTone.Critical
    MissionStatus.DRAFT, MissionStatus.COMPLETE -> StatusTone.Muted
}

internal fun TelemetryFrame?.toTelemetryUi(reserveFloorPct: Int, link: LinkState): TelemetryUi {
    val battery = this?.batteryPct?.roundToInt()
    return TelemetryUi(
        altitude = this?.let { "${it.altitudeAglM.roundToInt()} m" } ?: "—",
        climb = this?.let { String.format(Locale.ROOT, "%+d m/s", it.climbMps.roundToInt()) } ?: "no fix",
        speed = this?.let { "${it.airspeedMps.roundToInt()} m/s" } ?: "—",
        heading = this?.let { String.format(Locale.ROOT, "hdg %03d°", it.headingDeg.roundToInt().mod(360)) } ?: "no fix",
        battery = battery?.let { "$it%" } ?: "—",
        batteryTone = when {
            battery == null -> StatusTone.Muted
            battery <= reserveFloorPct -> StatusTone.Critical
            battery < reserveFloorPct + BATTERY_CAUTION_MARGIN_PCT -> StatusTone.Warn
            else -> StatusTone.Ok
        },
        reserve = "RTB at $reserveFloorPct%",
        link = link.mode.label,
        linkTone = if (link.mode == LinkMode.ONLINE) StatusTone.Ok else StatusTone.Warn,
        linkDetail = link.detail(),
    )
}

private fun LinkState.detail(): String = when {
    mode == LinkMode.DENIED || rttMs == null -> "local 5 Hz"
    mode == LinkMode.DEGRADED -> "rtt $rttMs ms · $lossPct% loss"
    else -> "rtt $rttMs ms"
}
