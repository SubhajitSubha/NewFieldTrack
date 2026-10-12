package com.example.fieldtrack.designsystem.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
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
 * There is no dark theme. Deliberately: this app is used outdoors in daylight, and a
 * half-tuned dark palette would be worse than none. The structure here makes adding one
 * a matter of supplying a second [FtColors].
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

@Composable
fun FieldTrackTheme(content: @Composable () -> Unit) {
    val colors = FtLightColors
    val type = FtDefaultTypography

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

private fun FtColors.toMaterialScheme() = lightColorScheme(
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

internal val LocalFtColors = staticCompositionLocalOf<FtColors> {
    error("FtColors not provided — wrap the UI in FieldTrackTheme { }")
}
internal val LocalFtTypography = staticCompositionLocalOf<FtTypography> {
    error("FtTypography not provided — wrap the UI in FieldTrackTheme { }")
}
internal val LocalFtDimens = staticCompositionLocalOf { FtDimens() }
internal val LocalFtShapes = staticCompositionLocalOf { FtShapes() }
