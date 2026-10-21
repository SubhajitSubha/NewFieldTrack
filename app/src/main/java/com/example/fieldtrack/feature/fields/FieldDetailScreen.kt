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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.areaWithUnit
import com.example.fieldtrack.feature.workLabel

/**
 * One plot, and what has happened on it.
 *
 * "Start work here" is the button rather than a link back to the customer,
 * because the field is the thing being stood in. It carries the plot through
 * into the job, which is the only way the earnings figure above it ever
 * becomes true.
 */
@Composable
fun FieldDetailScreen(
    state: FieldDetailState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenMaps: () -> Unit,
    onStartWork: () -> Unit,
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
            title = state.name,
            subtitle = listOfNotNull(state.customerName, state.village)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FactCard(
                    label = stringResource(R.string.field_size),
                    value = areaWithUnit(state.area, state.unit),
                    modifier = Modifier.weight(1f),
                )
                FactCard(
                    label = stringResource(R.string.field_crop),
                    value = state.crop?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.field_crop_none),
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.canSeeMoney) {
                FtCard(background = colors.ink, borderColor = colors.ink) {
                    Text(
                        text = stringResource(R.string.field_earned).uppercase(),
                        style = FieldTrackTheme.type.caption,
                        color = colors.paper,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = rupees(state.earnedRupees),
                        style = FieldTrackTheme.type.moneyHero,
                        color = colors.paper,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.field_earned_jobs, state.jobs.size, state.jobs.size,
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = colors.paper,
                    )
                }
            }

            FtCard(onClick = if (state.hasPin) onOpenMaps else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FtIconTile(FieldTrackIcons.Field, size = 42.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = stringResource(
                                if (state.hasPin) R.string.field_open_maps
                                else R.string.field_no_pin
                            ),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        if (state.hasPin) {
                            Text(
                                text = "%.4f, %.4f".format(state.latitude, state.longitude),
                                style = FieldTrackTheme.type.caption,
                                color = colors.inkSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            FtSectionLabel(stringResource(R.string.field_work_here))
            FtCard(padding = 0.dp) {
                if (state.jobs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.field_no_work),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(15.dp),
                    )
                }
                state.jobs.forEachIndexed { index, job ->
                    if (index > 0) FtRowDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = workLabel(job.workType),
                                style = FieldTrackTheme.type.rowTitle,
                                color = colors.ink,
                            )
                            Text(
                                text = stringResource(
                                    R.string.field_job_line,
                                    dayMonth(job.workedAtMillis),
                                    areaWithUnit(job.quantity, job.unit),
                                ),
                                style = FieldTrackTheme.type.caption,
                                color = colors.inkSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (state.canSeeMoney) {
                            Text(
                                text = rupees(job.amountRupees),
                                style = FieldTrackTheme.type.sectionTitle,
                                color = colors.ink,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(
            Modifier.padding(dimens.screenPadding).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            FtButton(text = stringResource(R.string.field_start_here), onClick = onStartWork)
            if (state.canEdit) {
                FtButton(
                    text = stringResource(R.string.field_edit_title),
                    onClick = onEdit,
                    tone = FtButtonTone.Secondary,
                    height = 50.dp,
                )
            }
        }
    }
}

@Composable
private fun FactCard(label: String, value: String, modifier: Modifier = Modifier) {
    FtCard(modifier = modifier, padding = 14.dp) {
        Text(
            text = label.uppercase(),
            style = FieldTrackTheme.type.overline,
            color = FieldTrackTheme.colors.inkTertiary,
        )
        Text(
            text = value,
            style = FieldTrackTheme.type.sectionTitle,
            color = FieldTrackTheme.colors.ink,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
