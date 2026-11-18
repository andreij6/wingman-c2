package com.wingmanc2.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

@Composable
fun WingmanTheme(
    colors: WingmanColors = WingmanColors.Dark,
    typography: WingmanTypography = WingmanTypography.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(colors) { colors.toMaterialColorScheme() }
    val materialTypography = remember(typography) { typography.toMaterialTypography() }
    CompositionLocalProvider(LocalWingmanColors provides colors, LocalWingmanTypography provides typography) {
        MaterialTheme(colorScheme = colorScheme, typography = materialTypography, shapes = WingmanShapes.material, content = content)
    }
}

object WingmanTheme {
    val colors: WingmanColors
        @Composable @ReadOnlyComposable get() = LocalWingmanColors.current

    val typography: WingmanTypography
        @Composable @ReadOnlyComposable get() = LocalWingmanTypography.current
}
