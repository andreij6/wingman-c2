package com.wingmanc2.core.ui

import androidx.compose.ui.graphics.Color
import com.wingmanc2.core.designsystem.theme.WingmanColors
import com.wingmanc2.core.model.Affiliation
import com.wingmanc2.core.model.EntityKind

fun WingmanColors.entityColor(kind: EntityKind, affiliation: Affiliation): Color = when (kind) {
    EntityKind.ZONE -> warn
    EntityKind.CLASS -> ai
    else -> affiliation(affiliation)
}
