package com.example.fieldtrack.feature.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.feedback.RatingStep
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** A note over the app with up to two answers underneath. Text above, buttons below, so it holds at any text size. */
@Composable
private fun NoteBar(
    text: String,
    modifier: Modifier = Modifier,
    secondary: Pair<String, () -> Unit>? = null,
    primary: Pair<String, () -> Unit>? = null,
) {
    val colors = FieldTrackTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.infoWash)
            .padding(horizontal = FieldTrackTheme.dimens.screenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = text, style = FieldTrackTheme.type.bodySmall, color = colors.infoInk)
        if (secondary != null || primary != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                secondary?.let { (label, onClick) -> FtButton(text = label, onClick = onClick, tone = FtButtonTone.Quiet, height = 44.dp, modifier = Modifier.weight(1f)) }
                primary?.let { (label, onClick) -> FtButton(text = label, onClick = onClick, height = 44.dp, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** Said all the time the practice business is on screen, so nobody mistakes it for their own. */
@Composable
fun PracticeBar(onLeave: () -> Unit, modifier: Modifier = Modifier) {
    NoteBar(
        text = stringResource(R.string.sample_banner),
        modifier = modifier,
        primary = stringResource(R.string.sample_leave) to onLeave,
    )
}

/** The one-time "is it helping?" question, and what follows each answer. */
@Composable
fun RatingBar(
    step: RatingStep,
    canWrite: Boolean,
    onAnswer: (Boolean) -> Unit,
    onLater: () -> Unit,
    onRate: () -> Unit,
    onWrite: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (step) {
        RatingStep.Hidden -> Unit
        RatingStep.Ask -> NoteBar(
            text = stringResource(R.string.rating_ask),
            modifier = modifier,
            secondary = stringResource(R.string.rating_no) to { onAnswer(false) },
            primary = stringResource(R.string.rating_yes) to { onAnswer(true) },
        )
        RatingStep.Happy -> NoteBar(
            text = stringResource(R.string.rating_happy),
            modifier = modifier,
            secondary = stringResource(R.string.rating_later) to onLater,
            primary = stringResource(R.string.rating_rate) to onRate,
        )
        RatingStep.Unhappy -> NoteBar(
            text = stringResource(R.string.rating_unhappy),
            modifier = modifier,
            secondary = stringResource(R.string.rating_nothanks) to onNo,
            primary = if (canWrite) stringResource(R.string.rating_write) to onWrite else null,
        )
    }
}
