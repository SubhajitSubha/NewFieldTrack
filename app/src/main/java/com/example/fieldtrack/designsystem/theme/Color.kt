package com.example.fieldtrack.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * FieldTrack palette.
 *
 * Built for a phone held at arm's length in direct sun by someone who may not read
 * fluently. Two rules the values below exist to satisfy:
 *
 *  - Every pair that must be told apart differs in lightness as well as hue, so the
 *    UI survives sunlight, cheap screens and colour blindness.
 *  - Every text colour clears 4.5:1 on the surface it is used on (3:1 at 24sp+).
 */
object FtPalette {
    // Ground and surfaces — warm paper, never sterile white.
    val Paper = Color(0xFFF5F3EE)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFEFEEE9)
    val Line = Color(0xFFE2E0D8)
    val LineStrong = Color(0xFFD8D5CC)

    // Ink. Secondary is 7.3:1 on Surface, tertiary 4.9:1 — both safe for body text.
    val Ink = Color(0xFF16231C)
    val InkSecondary = Color(0xFF4A5A51)
    val InkTertiary = Color(0xFF6B7B72)

    // Primary — field green. 6.4:1 on Surface; white on it clears AA at any size.
    val Green = Color(0xFF0E6B3D)
    val GreenDark = Color(0xFF0A5430)
    val GreenWash = Color(0xFFE4F0E8)
    /** Brighter green reserved for the WhatsApp send action, so it reads as "outward". */
    val GreenBright = Color(0xFF1EA34A)

    // Harvest amber — money in motion, paused work, things needing attention soon.
    val Amber = Color(0xFFB4600E)
    val AmberWash = Color(0xFFFBEEDC)
    val AmberInk = Color(0xFF8A4A0B)

    // Due red — money owed, breakdowns.
    val Red = Color(0xFFAE2A1E)
    val RedWash = Color(0xFFFBE9E7)
    val RedInk = Color(0xFF8E2217)

    // Record blue — completed, informational, "a human must decide".
    val Blue = Color(0xFF1B4FC4)
    val BlueWash = Color(0xFFE6EDFB)
    val BlueInk = Color(0xFF173F9C)

    // On dark surfaces (the running-work screen).
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkMuted = Color(0xB8FFFFFF)
    val OnDarkFaint = Color(0x29FFFFFF)
    /** Money on a dark ground; amber is unreadable there, this is 8.9:1 on GreenDark. */
    val Gold = Color(0xFFFFD79A)
    val LiveDot = Color(0xFF7FE3A6)

    /** Single-hue chart ramp, ordered light to dark. Rank reads without a legend. */
    val Chart1 = Color(0xFF0E6B3D)
    val Chart2 = Color(0xFF3B8C5F)
    val Chart3 = Color(0xFF6FAD89)
    val Chart4 = Color(0xFFA3C9B2)
    val Chart5 = Color(0xFFBFD9C8)
}

/**
 * Semantic colours the UI actually names. Components reference these, never [FtPalette]
 * directly, so a future dark theme is a second instance of this class and nothing else.
 */
@Immutable
data class FtColors(
    val paper: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val line: Color,
    val lineStrong: Color,

    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,

    val primary: Color,
    val primaryPressed: Color,
    val primaryWash: Color,
    val onPrimary: Color,
    val send: Color,

    val money: Color,
    val moneyWash: Color,
    val moneyInk: Color,

    val due: Color,
    val dueWash: Color,
    val dueInk: Color,

    val info: Color,
    val infoWash: Color,
    val infoInk: Color,

    val onDark: Color,
    val onDarkMuted: Color,
    val onDarkFaint: Color,
    val moneyOnDark: Color,
    val liveDot: Color,

    val chart: List<Color>,
)

internal val FtLightColors = FtColors(
    paper = FtPalette.Paper,
    surface = FtPalette.Surface,
    surfaceMuted = FtPalette.SurfaceMuted,
    line = FtPalette.Line,
    lineStrong = FtPalette.LineStrong,

    ink = FtPalette.Ink,
    inkSecondary = FtPalette.InkSecondary,
    inkTertiary = FtPalette.InkTertiary,

    primary = FtPalette.Green,
    primaryPressed = FtPalette.GreenDark,
    primaryWash = FtPalette.GreenWash,
    onPrimary = FtPalette.OnDark,
    send = FtPalette.GreenBright,

    money = FtPalette.Amber,
    moneyWash = FtPalette.AmberWash,
    moneyInk = FtPalette.AmberInk,

    due = FtPalette.Red,
    dueWash = FtPalette.RedWash,
    dueInk = FtPalette.RedInk,

    info = FtPalette.Blue,
    infoWash = FtPalette.BlueWash,
    infoInk = FtPalette.BlueInk,

    onDark = FtPalette.OnDark,
    onDarkMuted = FtPalette.OnDarkMuted,
    onDarkFaint = FtPalette.OnDarkFaint,
    moneyOnDark = FtPalette.Gold,
    liveDot = FtPalette.LiveDot,

    chart = listOf(
        FtPalette.Chart1,
        FtPalette.Chart2,
        FtPalette.Chart3,
        FtPalette.Chart4,
        FtPalette.Chart5,
    ),
)
