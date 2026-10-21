package com.example.fieldtrack.feature.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.isToday
import com.example.fieldtrack.feature.areaWithUnit

/**
 * Every plot one farmer has, under the names he uses for them.
 *
 * The figures beside each come from the jobs that named it, so a list of
 * fields with nothing against them means the work is being recorded without
 * saying where — which is worth noticing, and is why the counts are on the
 * card rather than buried on the detail.
 */
@Composable
fun FieldsScreen(
    state: FieldsState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.fields_title),
            subtitle = state.customerName,
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (state.rows.isNotEmpty()) {
                item(key = "total") {
                    FtCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.fields_total_land),
                                style = FieldTrackTheme.type.body,
                                color = colors.inkSecondary,
                            )
                            val total = state.totalArea
                            val unit = state.totalUnit
                            if (total != null && unit != null) {
                                Text(
                                    text = areaWithUnit(total, unit),
                                    style = FieldTrackTheme.type.moneyMedium,
                                    color = colors.ink,
                                )
                            }
                        }
                        // Bigha is not the same size in two neighbouring
                        // districts, so there is no conversion to reach for.
                        if (state.totalArea == null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.fields_mixed_units),
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.inkSecondary,
                            )
                        }
                    }
                }
            }

            items(state.rows, key = { it.id }) { row ->
                FieldCard(row = row, onClick = { onOpen(row.id) })
            }

            if (state.loaded && state.rows.isEmpty()) {
                item(key = "empty") {
                    FtCard {
                        Text(
                            text = stringResource(R.string.fields_none_title),
                            style = FieldTrackTheme.type.sectionTitle,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = stringResource(R.string.fields_none_note),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }

            item(key = "tail") { Spacer(Modifier.height(dimens.screenPadding)) }
        }

        if (state.canEdit) {
            Column(Modifier.padding(dimens.screenPadding).fillMaxWidth()) {
                FtButton(text = stringResource(R.string.fields_add), onClick = onAdd)
            }
        }
    }
}

@Composable
private fun FieldCard(row: FieldRow, onClick: () -> Unit) {
    val colors = FieldTrackTheme.colors
    FtCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtIconTile(FieldTrackIcons.Field, size = 44.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = row.name,
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                )
                row.crop?.takeIf { it.isNotBlank() }?.let { crop ->
                    Text(
                        text = crop,
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                text = areaWithUnit(row.area, row.unit),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
        }
        Spacer(Modifier.height(11.dp))
        FtRowDivider()
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = when {
                    row.lastWorkedAt == null -> stringResource(R.string.fields_never_worked)
                    isToday(row.lastWorkedAt) -> stringResource(R.string.fields_worked_today)
                    else -> stringResource(R.string.fields_last_worked, dayMonth(row.lastWorkedAt))
                },
                style = FieldTrackTheme.type.caption,
                color = colors.inkSecondary,
            )
            if (row.jobCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.fields_jobs_here, row.jobCount, row.jobCount,
                    ),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkSecondary,
                )
            }
        }
    }
}
