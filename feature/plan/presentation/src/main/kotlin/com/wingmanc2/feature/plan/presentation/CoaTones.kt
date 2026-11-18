package com.wingmanc2.feature.plan.presentation

import androidx.compose.ui.graphics.Color
import com.wingmanc2.core.designsystem.theme.WingmanColors

internal fun WingmanColors.accentOf(tone: CoaTone): Color = when (tone) {
    CoaTone.Recommended -> ok
    CoaTone.Exposed -> warn
    CoaTone.Neutral -> text2
}

internal fun WingmanColors.routeOf(tone: CoaTone): Color = when (tone) {
    CoaTone.Recommended -> ok
    CoaTone.Exposed -> warn
    CoaTone.Neutral -> muted
}
