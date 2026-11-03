package com.example.fieldtrack.designsystem.layout

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the window has across, in the three steps Android's own guidance
 * uses: a phone held upright, a phone on its side or a small tablet, and a tablet.
 *
 * Height is deliberately not a class. Every screen scrolls, so a short window costs
 * nothing but scrolling — except where a screen is built around a fixed hero, and
 * those (the live job) look at height themselves.
 */
enum class WidthClass {
    /** Under 600dp: a phone held upright. One column, tabs along the bottom. */
    Compact,

    /** 600 to 840dp: a phone on its side, a small tablet. Tabs move to a side rail. */
    Medium,

    /** 840dp and over: a tablet. Screens with two kinds of content show both at once. */
    Expanded;

    val wide: Boolean get() = this != Compact

    companion object {
        fun of(width: Dp): WidthClass = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            else -> Expanded
        }
    }
}

/** The window's width class, provided once by the app shell. */
val LocalWidthClass = staticCompositionLocalOf { WidthClass.Compact }

/**
 * The widest a single column of reading gets.
 *
 * A form or a list stretched across a 10" tablet puts the label on the left edge
 * and the value on the right one, with a forearm's width between; past this, extra
 * width becomes margin rather than line length.
 */
val ReadableWidth: Dp = 680.dp

/** The widest a two-column screen gets before the extra becomes margin. */
val TwoPaneWidth: Dp = 1120.dp
