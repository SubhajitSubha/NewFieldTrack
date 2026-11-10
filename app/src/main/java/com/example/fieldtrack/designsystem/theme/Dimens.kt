package com.example.fieldtrack.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, sizing and corner radii.
 *
 * The touch targets here are larger than Material's 48dp minimum on purpose: the people
 * using this are often wearing gloves, standing on a moving machine, or both.
 */
@Immutable
data class FtDimens(
    val space1: Dp = 4.dp,
    val space2: Dp = 8.dp,
    val space3: Dp = 12.dp,
    val space4: Dp = 16.dp,
    val space5: Dp = 20.dp,
    val space6: Dp = 24.dp,
    val space8: Dp = 32.dp,

    /** Standard side gutter for every screen. */
    val screenPadding: Dp = 20.dp,
    val cardPadding: Dp = 16.dp,

    /** Never build a tappable thing smaller than this. */
    val minTouchTarget: Dp = 48.dp,
    /** Full-width confirm button at the bottom of a screen. */
    val buttonHeight: Dp = 58.dp,
    val buttonHeightCompact: Dp = 50.dp,
    /** The "one more trip" / "start work" class of control. */
    val buttonHeightHero: Dp = 72.dp,
    val iconButtonSize: Dp = 46.dp,
    val fieldHeight: Dp = 60.dp,
    val bottomNavHeight: Dp = 78.dp,
    val topBarHeight: Dp = 64.dp,

    val avatarSmall: Dp = 40.dp,
    val avatar: Dp = 46.dp,
    val avatarLarge: Dp = 76.dp,

    val icon: Dp = 24.dp,
    val iconLarge: Dp = 30.dp,
    val machineIcon: Dp = 34.dp,

    val hairline: Dp = 1.dp,
    val border: Dp = 2.dp,
    /** The border on a selected choice card. */
    val borderSelected: Dp = 2.5.dp,
    /** Left edge stripe that colour-codes a job row by status. */
    val statusStripe: Dp = 5.dp,
)

@Immutable
data class FtShapes(
    val chip: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(15.dp),
    val card: RoundedCornerShape = RoundedCornerShape(18.dp),
    val large: RoundedCornerShape = RoundedCornerShape(22.dp),
    /** The sheet that rises over the running-work screen. */
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
)
