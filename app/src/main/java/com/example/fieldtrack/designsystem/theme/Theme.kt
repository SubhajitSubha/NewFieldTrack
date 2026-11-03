package com.example.fieldtrack.designsystem.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The FieldTrack theme.
 *
 * Reach for [FieldTrackTheme.colors], [FieldTrackTheme.type], [FieldTrackTheme.dimens]
 * and [FieldTrackTheme.shapes] in UI code — never a raw hex, sp or dp literal. A
 * Material [MaterialTheme] is installed underneath so stock M3 components (Scaffold,
 * ripple, text selection) pick up the same palette, but our own components read the
 * locals below, because our colour roles do not map cleanly onto Material's.
 *
 * Light by default, because this app is mostly used outdoors in daylight. The dark
 * theme is for the cab at night and for people who simply prefer it; it is a full
 * second [FtColors], tuned for contrast, not the light one inverted.
 */
object FieldTrackTheme {
    val colors: FtColors
        @Composable @ReadOnlyComposable get() = LocalFtColors.current

    val type: FtTypography
        @Composable @ReadOnlyComposable get() = LocalFtTypography.current

    val dimens: FtDimens
        @Composable @ReadOnlyComposable get() = LocalFtDimens.current

    val shapes: FtShapes
        @Composable @ReadOnlyComposable get() = LocalFtShapes.current
}

/**
 * @param dark the dark palette. Defaults to the phone's own setting; the app passes
 *   what the person chose in Display.
 * @param textScale bigger text, multiplied over the phone's own font size.
 */
@Composable
fun FieldTrackTheme(
    dark: Boolean = isSystemInDarkTheme(),
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) FtDarkColors else FtLightColors
    val type = remember(textScale) { FtDefaultTypography.scaled(textScale) }

    CompositionLocalProvider(
        LocalFtColors provides colors,
        LocalFtTypography provides type,
        LocalFtDimens provides FtDimens(),
        LocalFtShapes provides FtShapes(),
        LocalTextStyle provides type.body.copy(color = colors.ink),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            content = content,
        )
    }
}

private fun FtColors.toMaterialScheme() = if (this === FtDarkColors) darkScheme() else lightScheme()

private fun FtColors.darkScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryWash,
    onPrimaryContainer = primaryPressed,
    secondary = money,
    onSecondary = onPrimary,
    secondaryContainer = moneyWash,
    onSecondaryContainer = moneyInk,
    tertiary = info,
    onTertiary = onPrimary,
    tertiaryContainer = infoWash,
    onTertiaryContainer = infoInk,
    background = paper,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    surfaceVariant = surfaceMuted,
    onSurfaceVariant = inkSecondary,
    surfaceContainerLow = surface,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceMuted,
    outline = line,
    outlineVariant = lineStrong,
    error = due,
    onError = onPrimary,
    errorContainer = dueWash,
    onErrorContainer = dueInk,
)

private fun FtColors.lightScheme() = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryWash,
    onPrimaryContainer = primaryPressed,
    secondary = money,
    onSecondary = onDark,
    secondaryContainer = moneyWash,
    onSecondaryContainer = moneyInk,
    tertiary = info,
    onTertiary = onDark,
    tertiaryContainer = infoWash,
    onTertiaryContainer = infoInk,
    background = paper,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    surfaceVariant = surfaceMuted,
    onSurfaceVariant = inkSecondary,
    outline = line,
    outlineVariant = lineStrong,
    error = due,
    onError = onDark,
    errorContainer = dueWash,
    onErrorContainer = dueInk,
)

/**
 * Something that stands for paper — a bill, a receipt — drawn as paper whatever
 * the theme. A farmer is sent a white receipt; showing the person a dark one first
 * would be showing them something they are not about to send. The text size stays
 * the reader's own.
 */
@Composable
fun OnPaper(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalFtColors provides FtLightColors,
        LocalTextStyle provides LocalFtTypography.current.body.copy(color = FtLightColors.ink),
        content = content,
    )
}

internal val LocalFtColors = staticCompositionLocalOf<FtColors> {
    error("FtColors not provided — wrap the UI in FieldTrackTheme { }")
}
internal val LocalFtTypography = staticCompositionLocalOf<FtTypography> {
    error("FtTypography not provided — wrap the UI in FieldTrackTheme { }")
}
internal val LocalFtDimens = staticCompositionLocalOf { FtDimens() }
internal val LocalFtShapes = staticCompositionLocalOf { FtShapes() }
