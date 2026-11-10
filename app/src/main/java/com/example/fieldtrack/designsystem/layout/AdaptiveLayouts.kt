package com.example.fieldtrack.designsystem.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * The frame for a screen that can show two columns: paper to the edges, and the
 * screen itself centred at a width that suits what it can show.
 *
 * One column on a phone and on a small tablet, held to [ReadableWidth]; two on a
 * tablet or a phone on its side, held to [TwoPaneWidth]. The top bar and bottom
 * buttons sit inside the same frame, so they line up with the content under them
 * instead of running to the screen's edge above a centred page.
 */
@Composable
fun AdaptiveScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val twoPane = LocalWidthClass.current == WidthClass.Expanded
    Box(
        modifier
            .fillMaxSize()
            .background(FieldTrackTheme.colors.paper),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = if (twoPane) TwoPaneWidth else ReadableWidth)
                .fillMaxSize(),
            content = content,
        )
    }
}

/**
 * The scrolling body of an [AdaptiveScreen]: [primary] then [secondary] down one
 * column, or side by side when there is room.
 *
 * A screen splits its sections by what they are, not by size: the figure and who
 * did it on one side, the history on the other. On a phone the order is the order
 * of reading, so [primary] is what matters first.
 */
@Composable
fun ColumnScope.AdaptiveBody(
    spacing: Dp = 13.dp,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit,
) {
    val twoPane = LocalWidthClass.current == WidthClass.Expanded
    val padding = FieldTrackTheme.dimens.screenPadding
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (twoPane) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing)) {
                    primary()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing)) {
                    secondary()
                }
            }
        } else {
            primary()
            secondary()
        }
        Box(Modifier.padding(bottom = FieldTrackTheme.dimens.space4))
    }
}
