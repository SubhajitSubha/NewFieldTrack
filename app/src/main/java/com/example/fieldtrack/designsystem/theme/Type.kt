package com.example.fieldtrack.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.fieldtrack.R

/**
 * Figtree, bundled rather than fetched from Google Fonts at runtime: this app is used
 * where there is no signal, and a downloadable font would mean a first launch with no
 * type. It ships as one variable file, so all six weights cost 62 KB in total.
 *
 * minSdk is 26, so [FontVariation] axis settings apply on every device we support.
 */
@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    resId = R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val FigtreeFamily = FontFamily(
    figtree(400),
    figtree(500),
    figtree(600),
    figtree(700),
    figtree(800),
)

/**
 * The scale. Deliberately larger than a stock Material scale throughout — body text
 * starts at 16sp and money is set at 34–40sp, because the number people came to see
 * should be legible without squinting.
 *
 * Anything numeric uses tabular figures so digits do not jitter as a timer ticks or a
 * total recalculates.
 */
@Immutable
data class FtTypography(
    /** The single biggest number on a screen: a total, a balance, a running count. */
    val moneyHero: TextStyle,
    val moneyLarge: TextStyle,
    val moneyMedium: TextStyle,
    /** Timers and counters inside cards. */
    val numeric: TextStyle,

    val screenTitle: TextStyle,
    /** Screen title that has a subtitle under it, so the pair fits the bar. */
    val screenTitleCompact: TextStyle,
    val sectionTitle: TextStyle,
    val rowTitle: TextStyle,

    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /** Small, wide, uppercase group headings — "OWED TO YOU", "TODAY'S WORK". */
    val overline: TextStyle,
    val buttonLarge: TextStyle,
    val button: TextStyle,
)

/** Fixed-width digits, so a ticking timer or a recalculating total does not jitter. */
private const val TABULAR = "tnum"

internal val FtDefaultTypography = FtTypography(
    moneyHero = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W800,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.025).em,
        fontFeatureSettings = TABULAR,
    ),
    moneyLarge = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W800,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR,
    ),
    moneyMedium = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W800,
        fontSize = 25.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR,
    ),
    numeric = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W800,
        fontSize = 17.sp,
        lineHeight = 21.sp,
        fontFeatureSettings = TABULAR,
    ),

    screenTitle = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W800,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.015).em,
    ),
    screenTitleCompact = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W800,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.01).em,
    ),
    sectionTitle = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W800,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    rowTitle = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W700,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),

    body = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    label = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    caption = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    overline = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W700,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.em,
    ),
    buttonLarge = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W800,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
    ),
    button = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.W700,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    ),
)
