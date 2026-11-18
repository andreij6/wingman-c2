package com.wingmanc2.core.ui

import androidx.compose.ui.graphics.Color
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind
import com.wingmanc2.core.model.LinkMode

enum class StatusTone { Ok, Warn, Critical, Ai, Primary, Friend, Secondary, Muted }

fun WingmanColors.of(tone: StatusTone): Color = when (tone) {
    StatusTone.Ok -> ok
    StatusTone.Warn -> warn
    StatusTone.Critical -> critical
    StatusTone.Ai -> ai
    StatusTone.Primary -> primary
    StatusTone.Friend -> friend
    StatusTone.Secondary -> text2
    StatusTone.Muted -> muted
}

fun WingmanColors.affiliationColor(affiliation: Affiliation): Color = when (affiliation) {
    Affiliation.FRIEND -> friend
    Affiliation.HOSTILE -> hostile
    Affiliation.UNKNOWN -> unknown
    Affiliation.NEUTRAL -> neutral
}

fun WingmanColors.entityColor(kind: EntityKind, affiliation: Affiliation): Color = when (kind) {
    EntityKind.ZONE -> warn
    EntityKind.CLASS -> ai
    else -> affiliationColor(affiliation)
}

fun WingmanColors.linkColor(mode: LinkMode): Color = if (mode == LinkMode.ONLINE) ok else warn
