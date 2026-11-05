package pl.mysttic.trainings.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import pl.mysttic.trainings.R

/*
 * Sora (Google Fonts, OFL) — jeden krój, cztery wagi (D-32). 300 do wielkich liczb i nagłówków wyświetlanych,
 * 400 do treści, 500 do metadanych, 600 do tytułów i przycisków. Nic powyżej 600.
 * W res/font leży jeden plik: wariant zmienny. Wagi bierzemy z osi „wght” (wymaga API 26, minSdk = 26).
 */
private fun sora(weight: Int) = Font(
    resId = R.font.sora,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Sora = FontFamily(sora(300), sora(400), sora(500), sora(600))

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.25).sp),
    displaySmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 23.sp),
    titleMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

/** Nagłówki sekcji, etykieta stanu playera, data. Zawsze tertiary i wersaliki. */
val Overline = TextStyle(
    fontFamily = Sora,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 12.sp,
    letterSpacing = 1.8.sp,
)

/*
 * Liczby playera: waga 300, cyfry tabularne, interlinia równa rozmiarowi.
 * Policzone pod czytanie z półtora metra — nie zmniejszać.
 * Interlinia równa rozmiarowi przycięłaby glify, dlatego wysokość jest wyśrodkowana i przycięta z obu stron
 * (odpowiednik CSS-owego line-height: 1 z prototypu).
 */
private val TightNumbers = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun number(size: Int) = TextStyle(
    fontFamily = Sora,
    fontWeight = FontWeight.Light,
    fontSize = size.sp,
    lineHeight = size.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = TightNumbers,
)

val PlayerCountdown = number(208)
val PlayerRest = number(176)
val PlayerSetValue = number(132)
val StatValue = number(26)
val StepperValue = number(30)
