package com.wingmanc2.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

internal object WingmanPalette {
    val Background = Color(0xFF0A0E13)
    val Surface = Color(0xFF11171F)
    val Surface2 = Color(0xFF18202A)
    val Surface3 = Color(0xFF222C38)
    val Line = Color(0xFF232D3A)
    val Text = Color(0xFFE7ECF3)
    val Text2 = Color(0xFFA3AFBF)
    val Muted = Color(0xFF6B7889)
    val Primary = Color(0xFF8AB4FF)
    val OnPrimary = Color(0xFF0B1B36)
    val Ai = Color(0xFFB79CFF)
    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFF5A524)
    val Critical = Color(0xFFFF6B6B)
    val Friend = Color(0xFF4FC3F7)
    val Hostile = Color(0xFFFF6B6B)
    val Unknown = Color(0xFFF7D154)
    val Neutral = Color(0xFF6EDB8F)
    val MapBackground = Color(0xFF0D131A)
    val Scrim = Color(0xFF05080C)
}

@Immutable
class WingmanColors(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val ai: Color,
    val ok: Color,
    val warn: Color,
    val critical: Color,
    val friend: Color,
    val hostile: Color,
    val unknown: Color,
    val neutral: Color,
    val mapBackground: Color,
    val scrim: Color,
) {
    companion object {
        val Dark = WingmanColors(
            background = WingmanPalette.Background,
            surface = WingmanPalette.Surface,
            surface2 = WingmanPalette.Surface2,
            surface3 = WingmanPalette.Surface3,
            line = WingmanPalette.Line,
            text = WingmanPalette.Text,
            text2 = WingmanPalette.Text2,
            muted = WingmanPalette.Muted,
            primary = WingmanPalette.Primary,
            onPrimary = WingmanPalette.OnPrimary,
            ai = WingmanPalette.Ai,
            ok = WingmanPalette.Ok,
            warn = WingmanPalette.Warn,
            critical = WingmanPalette.Critical,
            friend = WingmanPalette.Friend,
            hostile = WingmanPalette.Hostile,
            unknown = WingmanPalette.Unknown,
            neutral = WingmanPalette.Neutral,
            mapBackground = WingmanPalette.MapBackground,
            scrim = WingmanPalette.Scrim,
        )
    }
}

val LocalWingmanColors = staticCompositionLocalOf { WingmanColors.Dark }

internal fun WingmanColors.toMaterialColorScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primary.copy(alpha = 0.18f).compositeOver(surface),
    onPrimaryContainer = primary,
    secondary = ai,
    onSecondary = onPrimary,
    tertiary = ok,
    onTertiary = background,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surface2,
    onSurfaceVariant = text2,
    surfaceContainerLowest = background,
    surfaceContainerLow = surface,
    surfaceContainer = surface,
    surfaceContainerHigh = surface2,
    surfaceContainerHighest = surface3,
    inverseSurface = text,
    inverseOnSurface = background,
    outline = line,
    outlineVariant = line,
    error = critical,
    onError = background,
    scrim = scrim,
)
