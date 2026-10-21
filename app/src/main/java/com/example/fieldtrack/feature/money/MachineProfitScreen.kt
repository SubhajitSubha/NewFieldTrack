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
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * What a machine brought in, against what it cost to run.
 *
 * The subtractions are shown rather than netted off, because an owner deciding
 * whether a harvester earns its keep is really asking which of the three costs
 * is eating it — and a single figure cannot answer that.
 */
@Composable
fun MachineProfitScreen(
    state: MachineProfitState,
    onBack: () -> Unit,
    onSeeMoneyOut: () -> Unit,
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
            title = stringResource(R.string.profit_title),
            subtitle = state.machineName,
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            FtCard {
                MoneyLine(
                    label = stringResource(R.string.profit_earned),
                    amount = rupees(state.earnedRupees),
                    strong = true,
                )
                Spacer(Modifier.height(11.dp))
                FtRowDivider()
                Spacer(Modifier.height(11.dp))
                MoneyLine(
                    label = stringResource(R.string.profit_fuel),
                    amount = "−" + rupees(state.fuelRupees),
                    cost = true,
                )
                Spacer(Modifier.height(9.dp))
                MoneyLine(
                    label = stringResource(R.string.profit_service),
                    amount = "−" + rupees(state.serviceRupees),
                    cost = true,
                )
                if (state.seesPeopleCost) {
                    Spacer(Modifier.height(9.dp))
                    MoneyLine(
                        label = stringResource(R.string.profit_people),
                        amount = "−" + rupees(state.peopleRupees),
                        cost = true,
                    )
                }
                Spacer(Modifier.height(13.dp))
                FtRowDivider()
                Spacer(Modifier.height(13.dp))
                if (state.seesPeopleCost) {
                    MoneyLine(
                        label = stringResource(R.string.profit_left),
                        amount = rupees(state.leftRupees),
                        strong = true,
                        good = state.leftRupees >= 0,
                    )
                } else {
                    // No total at all rather than one missing its people costs.
                    // A figure a third short reads as profit, and somebody
                    // might act on it.
                    Text(
                        text = stringResource(R.string.profit_no_total),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }

            if (state.seesPeopleCost && state.owedRupees > 0) {
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    // Inside the figure above, not beside it: what the work
                    // cost does not change on the day the cash moves. Said
                    // here so nobody reads the total as money already spent.
                    Text(
                        text = stringResource(R.string.profit_owed, rupees(state.owedRupees)),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                }
            }

            if (state.seesPeopleCost && state.leftOutRupees > 0) {
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = stringResource(
                            R.string.profit_left_out, rupees(state.leftOutRupees),
                        ),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                }
            }

            Text(
                text = stringResource(R.string.profit_since_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(4.dp))
        }

        if (state.seesPeopleCost) {
            Column(Modifier.padding(dimens.screenPadding).fillMaxWidth()) {
                com.example.fieldtrack.designsystem.component.FtButton(
                    text = stringResource(R.string.profit_see_money_out),
                    onClick = onSeeMoneyOut,
                    tone = com.example.fieldtrack.designsystem.component.FtButtonTone.Secondary,
                )
            }
        }
    }
}

@Composable
private fun MoneyLine(
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
