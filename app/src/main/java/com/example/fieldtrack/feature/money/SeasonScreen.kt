package com.example.fieldtrack.feature.money

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear

/**
 * A season's work, added up.
 *
 * The one screen where nothing has to be left out. A machine's own figures
 * have to disclaim the month's wages and the season advance, because no
 * machine can carry them; a season is exactly the stretch of time they were
 * for, so here they belong in the total like everything else.
 *
 * And the total is profit on paper, not cash in hand — said underneath,
 * because a contractor reading a good season off this screen while half of it
 * is still owed by farmers is the misreading that costs money.
 */
@Composable
fun SeasonScreen(
    state: SeasonState,
    onBack: () -> Unit,
    onWindow: (SeasonWindow) -> Unit,
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
            title = stringResource(R.string.season_title),
            subtitle = when (state.window) {
                SeasonWindow.Now -> stringResource(
                    R.string.season_span_now, dayMonthYear(state.fromMillis),
                )
                SeasonWindow.Last -> stringResource(
                    R.string.season_span,
                    dayMonthYear(state.fromMillis),
                    dayMonthYear(state.toMillis),
                )
            },
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FtChip(
                    label = stringResource(R.string.season_this),
                    selected = state.window == SeasonWindow.Now,
                    onClick = { onWindow(SeasonWindow.Now) },
                )
                FtChip(
                    label = stringResource(R.string.season_last),
                    selected = state.window == SeasonWindow.Last,
                    onClick = { onWindow(SeasonWindow.Last) },
                )
            }

            if (!state.canSeeMoney) {
                FtCard {
                    Text(
                        text = stringResource(R.string.money_out_locked),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
                return@Column
            }

            if (state.loaded && state.earnedRupees == 0L && state.costsRupees == 0L) {
                FtCard {
                    Text(
                        text = stringResource(R.string.season_nothing),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
                return@Column
            }

            FtCard {
                SeasonLine(
                    label = stringResource(R.string.season_earned),
                    amount = rupees(state.earnedRupees),
                    strong = true,
                )
                Spacer(Modifier.height(11.dp))
                FtRowDivider()
                Spacer(Modifier.height(11.dp))
                SeasonLine(
                    label = stringResource(R.string.season_diesel),
                    amount = "−" + rupees(state.fuelRupees),
                    cost = true,
                )
                Spacer(Modifier.height(9.dp))
                SeasonLine(
                    label = stringResource(R.string.season_service),
                    amount = "−" + rupees(state.serviceRupees),
                    cost = true,
                )
                if (state.seesPeopleCost) {
                    Spacer(Modifier.height(9.dp))
                    SeasonLine(
                        label = stringResource(R.string.season_people),
                        amount = "−" + rupees(state.peopleRupees),
                        cost = true,
                    )
                    Text(
                        text = stringResource(R.string.season_people_note),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }

                if (state.canTotal) {
                    Spacer(Modifier.height(13.dp))
                    FtRowDivider()
                    Spacer(Modifier.height(13.dp))
                    SeasonLine(
                        label = stringResource(R.string.season_left),
                        amount = rupees(state.leftRupees),
                        strong = true,
                        good = state.leftRupees >= 0,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (!state.seesPeopleCost) {
                            stringResource(R.string.season_no_total_hidden)
                        } else {
                            stringResource(R.string.season_no_total, state.unpricedCount)
                        },
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }

            if (state.canTotal) {
                FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                    Text(
                        text = stringResource(R.string.season_everything_lands),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }

            // Said whenever there is money owed, total or no total. A good
            // season on paper with half of it still out in the villages is
            // the misreading this screen could most easily cause.
            if (state.owedByCustomersRupees > 0) {
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = stringResource(
                            R.string.season_on_paper, rupees(state.owedByCustomersRupees),
                        ),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                }
            }

            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}

@Composable
private fun SeasonLine(
    label: String,
    amount: String,
    strong: Boolean = false,
    cost: Boolean = false,
    good: Boolean = true,
) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (strong) FieldTrackTheme.type.sectionTitle else FieldTrackTheme.type.body,
            color = if (strong) colors.ink else colors.inkSecondary,
        )
        Text(
            text = amount,
            style = if (strong) FieldTrackTheme.type.moneyMedium else FieldTrackTheme.type.body,
            color = when {
                cost -> colors.due
                strong && good -> colors.money
                strong -> colors.due
                else -> colors.ink
            },
        )
    }
}
