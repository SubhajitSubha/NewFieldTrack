package com.example.fieldtrack.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.settings.DisplaySettings
import com.example.fieldtrack.data.settings.ThemeChoice
import com.example.fieldtrack.designsystem.layout.WidthClass
import com.example.fieldtrack.designsystem.theme.FtColors
import com.example.fieldtrack.designsystem.theme.FtDarkColors
import com.example.fieldtrack.designsystem.theme.FtDefaultTypography
import com.example.fieldtrack.designsystem.theme.FtLightColors
import com.example.fieldtrack.designsystem.theme.scaled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The theme's promises, checked as numbers.
 *
 * Contrast is the one that matters: the palette's own comment promises every text
 * colour clears 4.5:1 on the ground it sits on, and a colour adjusted by eye in a
 * year's time can break that without anyone seeing it on their own screen. Each
 * pair below is one the app actually draws, in both themes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeTest {

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    /** Text the app draws, on the ground it draws it on, and the bar it has to clear. */
    private fun pairs(c: FtColors) = listOf(
        Triple("ink on surface", c.ink to c.surface, 4.5),
        Triple("ink on paper", c.ink to c.paper, 4.5),
        Triple("secondary on surface", c.inkSecondary to c.surface, 4.5),
        Triple("tertiary on surface", c.inkTertiary to c.surface, 4.5),
        Triple("tertiary on paper", c.inkTertiary to c.paper, 4.5),
        Triple("tertiary on muted", c.inkTertiary to c.surfaceMuted, 4.5),
        Triple("secondary on muted", c.inkSecondary to c.surfaceMuted, 4.5),
        Triple("primary on surface", c.primary to c.surface, 4.5),
        Triple("button text on primary", c.onPrimary to c.primary, 4.5),
        Triple("button text on send", c.onPrimary to c.send, 3.0),
        Triple("selected tab on surface", c.primary to c.surface, 4.5),
        Triple("pressed green on its wash", c.primaryPressed to c.primaryWash, 4.5),
        Triple("amber ink on amber wash", c.moneyInk to c.moneyWash, 4.5),
        Triple("red ink on red wash", c.dueInk to c.dueWash, 4.5),
        Triple("red on surface", c.due to c.surface, 4.5),
        Triple("blue ink on blue wash", c.infoInk to c.infoWash, 4.5),
        Triple("white on the deep card", c.onDark to c.deep, 7.0),
        Triple("muted white on the deep card", c.onDarkMuted to c.deep, 4.5),
        Triple("gold money on the deep card", c.moneyOnDark to c.deep, 4.5),
        Triple("white on the working green", c.onDark to c.deepGreen, 7.0),
        Triple("muted white on the working green", c.onDarkMuted to c.deepGreen, 4.5),
        Triple("gold money on the working green", c.moneyOnDark to c.deepGreen, 4.5),
        Triple("white on owed red", c.onDark to c.dueDeep, 4.5),
        Triple("red figure on the deep card", c.dueOnDark to c.deep, 4.5),
        Triple("deep-card mark on a white hero button", c.deep to c.onDark, 7.0),
    )

    private fun assertReadable(name: String, colors: FtColors) {
        val failures = pairs(colors).mapNotNull { (what, pair, bar) ->
            val ratio = contrast(pair.first, pair.second)
            if (ratio < bar) "$what: %.2f:1 (needs %.1f)".format(ratio, bar) else null
        }
        assertTrue("$name theme:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `every text colour in the light theme is readable on its ground`() =
        assertReadable("Light", FtLightColors)

    @Test fun `every text colour in the dark theme is readable on its ground`() =
        assertReadable("Dark", FtDarkColors)

    @Test fun `the dark theme is dark and the deep card still stands apart`() {
        assertTrue(FtDarkColors.paper.luminance() < 0.02f)
        // Lifted above the page in the dark theme, so the one card per screen is
        // still the one that stands out rather than vanishing into it.
        assertTrue(FtDarkColors.deep.luminance() > FtDarkColors.paper.luminance())
        assertTrue(contrast(FtDarkColors.deep, FtDarkColors.paper) > 1.3)
    }

    // ------------------------------------------------------------------- text

    @Test fun `bigger text grows every style by the same factor, line height too`() {
        val base = FtDefaultTypography
        val big = base.scaled(DisplaySettings.BIGGER_TEXT_SCALE)
        listOf(
            base.body to big.body,
            base.caption to big.caption,
            base.moneyHero to big.moneyHero,
            base.button to big.button,
        ).forEach { (before, after) ->
            assertEquals(before.fontSize.value * 1.15f, after.fontSize.value, 0.01f)
            if (!before.lineHeight.isUnspecified) {
                assertEquals(before.lineHeight.value * 1.15f, after.lineHeight.value, 0.01f)
            }
        }
        assertTrue("scaling by one changes nothing", base.scaled(1f) === base)
    }

    // ------------------------------------------------------------------ sizes

    @Test fun `windows fall into the three width classes at 600 and 840`() {
        assertEquals(WidthClass.Compact, WidthClass.of(411.dp))
        assertEquals(WidthClass.Compact, WidthClass.of(599.dp))
        assertEquals(WidthClass.Medium, WidthClass.of(600.dp))
        assertEquals(WidthClass.Medium, WidthClass.of(839.dp))
        assertEquals(WidthClass.Expanded, WidthClass.of(840.dp))
        assertFalse(WidthClass.Compact.wide)
        assertTrue(WidthClass.Medium.wide)
    }

    // --------------------------------------------------------------- settings

    @Test fun `display settings are remembered on the phone`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = DisplaySettings(context)
        assertEquals(ThemeChoice.System, first.theme.value)
        assertFalse(first.biggerText.value)

        first.setTheme(ThemeChoice.Dark)
        first.setBiggerText(true)

        // A fresh reader — the app after a restart — sees what was chosen.
        val again = DisplaySettings(context)
        assertEquals(ThemeChoice.Dark, again.theme.value)
        assertTrue(again.biggerText.value)
    }
}
