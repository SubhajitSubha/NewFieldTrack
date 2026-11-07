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
import androidx.compose.foundation.layout.width
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
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.season.seasonStartsSummary

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
    /** Opens the editor for when seasons begin. Used only when [SeasonState.canChangeSeasons]. */
    onChangeSeasons: () -> Unit,
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
            }.let { span ->
                // Named seasons say which one this is: "Kharif · 1 Jun 2026 until today".
                state.seasonName?.let { stringResource(R.string.season_named_span, it, span) } ?: span
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

            // When seasons begin, said on the one screen that is all about them: the
            // business's own dates, which differ from one district to the next, and
            // the owner's way to change them. Above the early returns below so it is
            // here whether or not there is anything to total.
            if (state.seasonStarts.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.season_begin_line,
                            seasonStartsSummary(state.seasonStarts),
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                    )
                    if (state.canChangeSeasons) {
                        FtButton(
                            text = stringResource(R.string.season_change),
                            onClick = onChangeSeasons,
                            tone = FtButtonTone.Secondary,
                            height = 40.dp,
                            // A width of its own: a button fills what it is given, and in
                            // a row beside the line of dates it left them none — one
                            // letter to a row.
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .width(112.dp),
                        )
                    }
                }
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

            if (state.loaded && state.earnedMinor == 0L && state.costsMinor == 0L) {
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
                    amount = money(state.earnedMinor),
                    strong = true,
                )
                Spacer(Modifier.height(11.dp))
                FtRowDivider()
                Spacer(Modifier.height(11.dp))
                SeasonLine(
                    label = stringResource(R.string.season_diesel),
                    amount = "−" + money(state.fuelMinor),
                    cost = true,
                )
                Spacer(Modifier.height(9.dp))
                SeasonLine(
                    label = stringResource(R.string.season_service),
                    amount = "−" + money(state.serviceMinor),
                    cost = true,
                )
                if (state.seesPeopleCost) {
                    Spacer(Modifier.height(9.dp))
                    SeasonLine(
                        label = stringResource(R.string.season_people),
                        amount = "−" + money(state.peopleMinor),
                        cost = true,
                    )
                    Text(
                        text = stringResource(R.string.season_people_note),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    if (state.hireMinor > 0) {
                        Spacer(Modifier.height(9.dp))
                        SeasonLine(
                            label = stringResource(R.string.income_hire),
                            amount = "−" + money(state.hireMinor),
                            cost = true,
                        )
                        Text(
                            text = stringResource(R.string.income_hire_note),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (state.loanMinor > 0) {
                        Spacer(Modifier.height(9.dp))
                        SeasonLine(
                            label = stringResource(R.string.season_loan),
                            amount = "−" + money(state.loanMinor),
                            cost = true,
                        )
                        Text(
                            text = stringResource(R.string.season_loan_note),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }

                if (state.canTotal) {
                    Spacer(Modifier.height(13.dp))
                    FtRowDivider()
                    Spacer(Modifier.height(13.dp))
                    SeasonLine(
                        label = stringResource(R.string.season_left),
                        amount = money(state.leftMinor),
                        strong = true,
                        good = state.leftMinor >= 0,
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

            // Said as a count, because it is the thing an owner would otherwise have
            // to find by opening every job. Only where every cost is priced, the same
            // rule the job's own page keeps.
            if (state.seesPeopleCost && state.lostJobs > 0) {
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.season_lost, state.lostJobs, state.lostJobs, money(state.lostMinor),
                        ),
                        style = FieldTrackTheme.type.sectionTitle,
                        color = colors.dueInk,
                    )
                    Text(
                        text = stringResource(R.string.season_lost_note),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                        modifier = Modifier.padding(top = 4.dp),
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
            if (state.owedByCustomersMinor > 0) {
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = stringResource(
                            R.string.season_on_paper, money(state.owedByCustomersMinor),
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
