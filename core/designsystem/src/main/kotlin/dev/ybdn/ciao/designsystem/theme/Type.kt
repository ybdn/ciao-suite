package dev.ybdn.ciao.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.ybdn.ciao.designsystem.R

/** Police d'affichage (titres, grands chiffres) : une seule graisse, très épaisse. */
val DisplayFont = FontFamily(Font(R.font.archivo_black))

/** Police de texte : DM Sans est une police variable, chaque graisse en est une instance. */
@OptIn(ExperimentalTextApi::class)
val BodyFont = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.dm_sans,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Étiquettes techniques en majuscules (équivalent du `font-mono uppercase` du site). */
val LabelMono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    letterSpacing = 0.08.em,
)

val CiaoTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = DisplayFont, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.025.em),
)
