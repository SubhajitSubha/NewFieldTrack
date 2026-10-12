package com.example.fieldtrack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** How prominent a money figure is. */
enum class FtMoneySize { Hero, Large, Medium, Row }

/**
 * A rupee amount.
 *
 * Always goes through here rather than `Text(rupees(x))`, so Indian digit grouping,
 * tabular figures and the due/settled colour rule stay consistent everywhere. A figure
 * someone is owed is red; one they have received is green; everything else is ink.
 */
@Composable
fun FtMoney(
    amount: Long,
    modifier: Modifier = Modifier,
    size: FtMoneySize = FtMoneySize.Medium,
    tone: FtMoneyTone = FtMoneyTone.Neutral,
    onDark: Boolean = false,
) {
    val colors = FieldTrackTheme.colors
    val type = FieldTrackTheme.type
    val style = when (size) {
        FtMoneySize.Hero -> type.moneyHero
        FtMoneySize.Large -> type.moneyLarge
        FtMoneySize.Medium -> type.moneyMedium
        FtMoneySize.Row -> type.numeric
    }
    val color = when {
        onDark && tone == FtMoneyTone.Neutral -> colors.onDark
        onDark -> colors.moneyOnDark
        tone == FtMoneyTone.Due -> colors.due
        tone == FtMoneyTone.Received -> colors.primaryPressed
        else -> colors.ink
    }
    Text(text = rupees(amount), style = style, color = color, modifier = modifier)
}

enum class FtMoneyTone { Neutral, Due, Received }

/**
 * The hero control on the running-work screen: the billable quantity, with a large
 * minus and plus on either side.
 *
 * The number shown here is whatever the job is billed by — acres, trips, quintals,
 * kilometres. It is never the clock unless the job is charged by the hour. Adjusting
 * the figure you get paid for should not require hunting for a smaller control
 * further down the screen.
 */
@Composable
fun FtQuantityStepper(
    value: String,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementContentDescription: String,
    incrementContentDescription: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = true,
    /**
     * Tapping the figure to type it outright.
     *
     * Forty bags is forty taps on the plus, which is not a thing anyone will do
     * standing beside a running thresher.
     */
    onTypeValue: (() -> Unit)? = null,
    typeValueContentDescription: String? = null,
) {
    val colors = FieldTrackTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FtStepperButton(
            icon = FieldTrackIcons.Minus,
            contentDescription = decrementContentDescription,
            onClick = onDecrement,
            onDark = onDark,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onTypeValue != null) {
                        Modifier
                            .clip(FieldTrackTheme.shapes.medium)
                            .clickable(role = Role.Button, onClick = onTypeValue)
                            .semantics {
                                typeValueContentDescription?.let { contentDescription = it }
                            }
                    } else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = FieldTrackTheme.type.moneyHero.copy(fontSize = 76.sp, lineHeight = 78.sp),
                color = if (onDark) colors.onDark else colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = unit,
                style = FieldTrackTheme.type.body,
                color = if (onDark) colors.onDarkMuted else colors.inkTertiary,
            )
        }
        FtStepperButton(
            icon = FieldTrackIcons.Add,
            contentDescription = incrementContentDescription,
            onClick = onIncrement,
            filled = true,
            onDark = onDark,
        )
    }
}

/**
 * A secondary fact on the running-work screen — elapsed time, distance, load. Reads as
 * supporting information so it never competes with the billable figure.
 */
@Composable
fun FtMetricChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onDark: Boolean = true,
) {
    val colors = FieldTrackTheme.colors
    Row(
        modifier = modifier
            .clip(FieldTrackTheme.shapes.chip)
            .background(if (onDark) colors.onDarkFaint else colors.surfaceMuted)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val content = if (onDark) colors.onDark else colors.inkSecondary
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(text = label, style = FieldTrackTheme.type.caption, color = content)
    }
}

/**
 * A labelled figure in a card — "TODAY / ₹8,200 / 3 jobs done".
 * [value] is pre-formatted so this works for money, hours and counts alike.
 */
@Composable
fun FtStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    valueColor: Color = FieldTrackTheme.colors.ink,
    labelColor: Color = FieldTrackTheme.colors.inkTertiary,
) {
    Column(modifier) {
        Text(label.uppercase(), style = FieldTrackTheme.type.overline, color = labelColor)
        Text(
            text = value,
            style = FieldTrackTheme.type.moneyMedium,
            color = valueColor,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (footnote != null) {
            Text(
                text = footnote,
                style = FieldTrackTheme.type.caption,
                color = labelColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
