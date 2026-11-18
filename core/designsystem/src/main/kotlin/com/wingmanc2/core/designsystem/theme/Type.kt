package com.wingmanc2.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wingmanc2.core.designsystem.R

private fun variableFamily(resId: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { weight ->
        Font(resId = resId, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

val InterFamily = variableFamily(R.font.inter, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

val MonoFamily = variableFamily(R.font.jetbrains_mono, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)

private fun inter(size: Float, weight: FontWeight, tracking: Float = 0f) =
    TextStyle(fontFamily = InterFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.em)

private fun mono(size: Float, weight: FontWeight) = TextStyle(fontFamily = MonoFamily, fontWeight = weight, fontSize = size.sp)

@Immutable
class WingmanTypography(
    val title: TextStyle = inter(22f, FontWeight.SemiBold),
    val headline: TextStyle = inter(18f, FontWeight.SemiBold),
    val cardTitle: TextStyle = inter(15f, FontWeight.SemiBold),
    val body: TextStyle = inter(14f, FontWeight.Normal),
    val bodySmall: TextStyle = inter(12.5f, FontWeight.Normal),
    val label: TextStyle = inter(12f, FontWeight.Medium),
    val caption: TextStyle = inter(10.5f, FontWeight.Normal),
    val button: TextStyle = inter(14f, FontWeight.SemiBold),
    val control: TextStyle = inter(13f, FontWeight.Medium),
    val chip: TextStyle = inter(12f, FontWeight.SemiBold),
    val chipMono: TextStyle = mono(10.5f, FontWeight.Normal),
    val overline: TextStyle = inter(11f, FontWeight.SemiBold, tracking = 0.08f),
    val keyLabel: TextStyle = inter(10.5f, FontWeight.SemiBold, tracking = 0.06f),
    val data: TextStyle = mono(15f, FontWeight.Bold),
    val dataSmall: TextStyle = mono(11f, FontWeight.Normal),
    val tag: TextStyle = mono(10.5f, FontWeight.Bold),
) {
    internal fun toMaterialTypography() = Typography(
        titleLarge = title,
        titleMedium = headline,
        titleSmall = cardTitle,
        bodyLarge = body,
        bodyMedium = bodySmall,
        bodySmall = caption,
        labelLarge = button,
        labelMedium = label,
        labelSmall = overline,
    )

    companion object {
        val Default = WingmanTypography()
    }
}

val LocalWingmanTypography = staticCompositionLocalOf { WingmanTypography.Default }
