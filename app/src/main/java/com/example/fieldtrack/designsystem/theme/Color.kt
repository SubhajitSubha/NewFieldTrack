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

    // Ink. Secondary is 7.3:1 on Surface. Tertiary is 5.3:1 on Surface and 4.8:1 on
    // Paper — it was 6B7B72, which measured 4.0:1 on Paper rather than the 4.9 this
    // line used to claim; ThemeTest now holds every pair to its number.
    val Ink = Color(0xFF16231C)
    val InkSecondary = Color(0xFF4A5A51)
    val InkTertiary = Color(0xFF5F6F66)

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

    /**
     * The one deep card a screen gets — today's money, the job in hand. Dark in
     * both themes, with [onDark] text on it, which is why it is its own token:
     * [ink] is a text colour, and in the dark theme text goes light.
     */
    val deep: Color,

    /** The working ground: the live job, the summary of a bill. Green, and dark in both themes. */
    val deepGreen: Color,

    /**
     * Red as a ground under white text — "they owe". Not [due], which in the dark
     * theme is a light coral so it can be read as text, and white on it fails.
     */
    val dueDeep: Color,

    /** Red as text on the deep card — "1 broken down". Light enough to read on it in both themes. */
    val dueOnDark: Color,

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

    deep = FtPalette.Ink,
    deepGreen = FtPalette.GreenDark,
    dueDeep = FtPalette.Red,
    dueOnDark = FtPalette.RedWash,

    chart = listOf(
        FtPalette.Chart1,
        FtPalette.Chart2,
        FtPalette.Chart3,
        FtPalette.Chart4,
        FtPalette.Chart5,
    ),
)

/**
 * The dark theme.
 *
 * Not the light one inverted. The ground is a green-black rather than grey so the
 * app still reads as itself; greens, ambers and reds are lifted until they clear
 * 4.5:1 on the surfaces they sit on; and the washes behind status colours are
 * deep tints rather than pastels, which glow on a dark screen. Buttons keep the
 * same meaning — solid green is still the way forward — with dark text on the
 * brighter green, because white on a light green fails contrast.
 *
 * The deep cards keep their dark grounds. In this theme they are lifted above the
 * page rather than darker than it, so the one card per screen still stands out.
 */
internal val FtDarkColors = FtColors(
    paper = Color(0xFF0D1310),
    surface = Color(0xFF161E19),
    surfaceMuted = Color(0xFF1D2621),
    line = Color(0xFF29342D),
    lineStrong = Color(0xFF36433B),

    // 15:1, 9.6:1 and 6.0:1 on surface.
    ink = Color(0xFFE9EFEB),
    inkSecondary = Color(0xFFB6C3BB),
    inkTertiary = Color(0xFF8E9E95),

    primary = Color(0xFF52C283),
    primaryPressed = Color(0xFF9EDDB9),
    primaryWash = Color(0xFF16301F),
    onPrimary = Color(0xFF052011),
    send = Color(0xFF3FDA75),

    money = Color(0xFFE6A253),
    moneyWash = Color(0xFF33260F),
    moneyInk = Color(0xFFF5C98B),

    due = Color(0xFFEE6E61),
    dueWash = Color(0xFF3A1A16),
    dueInk = Color(0xFFF6AAA0),

    info = Color(0xFF7FA6F3),
    infoWash = Color(0xFF172544),
    infoInk = Color(0xFFB6CBF8),

    onDark = FtPalette.OnDark,
    onDarkMuted = FtPalette.OnDarkMuted,
    onDarkFaint = FtPalette.OnDarkFaint,
    moneyOnDark = FtPalette.Gold,
    liveDot = FtPalette.LiveDot,

    deep = Color(0xFF1C3427),
    deepGreen = Color(0xFF0E4529),
    dueDeep = Color(0xFF8C2A20),
    dueOnDark = Color(0xFFF6AAA0),

    // Brightest first on a dark ground, so rank one is still the one that stands out.
    chart = listOf(
        Color(0xFF6FD39A),
        Color(0xFF4FB57D),
        Color(0xFF3C9163),
        Color(0xFF2D6E4B),
        Color(0xFF225238),
    ),
)
