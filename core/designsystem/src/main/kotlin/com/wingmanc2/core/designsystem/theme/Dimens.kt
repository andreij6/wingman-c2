package com.wingmanc2.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object WingmanSpacing {
    val xxs = 4.dp
    val xs = 6.dp
    val grid = 8.dp
    val sm = 12.dp
    val margin = 16.dp
    val lg = 24.dp
}

object WingmanShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val pill = CircleShape

    internal val material = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = small,
        medium = medium,
        large = large,
        extraLarge = large,
    )
}
