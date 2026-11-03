package com.example.fieldtrack.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.unitInPhrase
import com.example.fieldtrack.feature.workLabel

/**
 * What an hour on a machine costs, and what each kind of work costs per unit.
 *
 * Every figure here is an estimate, and says so ("about") — unlike the card above
 * it on the same screen, which adds up money that was actually spent. The two are
 * kept visibly apart: this one never feeds a total and nothing above it is
 * adjusted by it.
 *
 * It says nothing before there is something to say. A machine with three hours
 * on record would "cost" whatever one diesel fill divides into, and a number like
 * that gets believed.
 */
@Composable
fun RunningCostCard(
    running: Costing.RunningCost,
    canEdit: Boolean,
    onEdit: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    FtSectionLabel(stringResource(R.string.cost_hour_title), Modifier.padding(top = 4.dp))
    FtCard {
        if (!running.enough) {
            Text(
                text = stringResource(R.string.cost_hour_not_enough),
                style = FieldTrackTheme.type.rowTitle,
                color = colors.ink,
            )
            Text(
                text = stringResource(
                    R.string.cost_hour_need,
                    Costing.MIN_HOURS.toInt(),
                    quantity(running.hours),
                ),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@FtCard
        }

        val r = running.rounded()
        CostLine(stringResource(R.string.cost_hour_diesel), rupees(r.diesel))
        Spacer(Modifier.height(9.dp))
        CostLine(stringResource(R.string.cost_hour_repairs), rupees(r.upkeep))
        Spacer(Modifier.height(9.dp))
        when (val loan = running.loan) {
            is Costing.Loan.Counted ->
                CostLine(stringResource(R.string.cost_hour_loan), rupees(r.loan ?: 0))
            Costing.Loan.NotEntered -> {
                CostLine(
                    stringResource(R.string.cost_hour_loan_none),
                    stringResource(R.string.cost_hour_loan_not_entered),
                    muted = true,
                )
                if (canEdit) EditLink(stringResource(R.string.cost_hour_add_loan), onEdit)
            }
            Costing.Loan.CannotSpreadYet -> {
                CostLine(
                    stringResource(R.string.cost_hour_loan_none),
                    stringResource(R.string.cost_hour_loan_needs_hours),
                    muted = true,
                )
                if (canEdit) EditLink(stringResource(R.string.cost_hour_add_hours), onEdit)
            }
        }
        Spacer(Modifier.height(12.dp))
        FtRowDivider()
        Spacer(Modifier.height(12.dp))
        // The sum of the parts above it as rounded, so it adds up on paper.
        CostLine(
            stringResource(R.string.cost_hour_total),
            stringResource(R.string.cost_about, rupees(r.total)),
            strong = true,
        )
        Text(
            text = stringResource(R.string.cost_hour_basis, quantity(running.hours)),
            style = FieldTrackTheme.type.caption,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Cost per bigha, per trip, per quintal — against what that work has billed. */
@Composable
fun UnitCostCard(kinds: List<Costing.UnitCost>) {
    if (kinds.isEmpty()) return
    val colors = FieldTrackTheme.colors
    FtSectionLabel(stringResource(R.string.cost_kinds_title), Modifier.padding(top = 4.dp))
    FtCard(padding = 0.dp) {
        kinds.forEachIndexed { index, kind ->
            if (index > 0) FtRowDivider()
            val keeps = kind.leftRupees >= 0
            FtListRow(
                title = stringResource(
                    R.string.cost_kind_title,
                    workLabel(kind.workType),
                    stringResource(R.string.start_rate_per, unitInPhrase(kind.unit)),
                ),
                subtitle = stringResource(
                    R.string.cost_kind_line,
                    rupees(kind.costRupees),
                    rupees(kind.billedRupees),
                ),
                trailing = {
                    Text(
                        text = stringResource(
                            if (keeps) R.string.cost_kind_keeps else R.string.cost_kind_loses,
                            rupees(kotlin.math.abs(kind.leftRupees)),
                        ),
                        style = FieldTrackTheme.type.label,
                        color = if (keeps) colors.primary else colors.due,
                    )
                },
            )
        }
    }
    Text(
        text = stringResource(R.string.cost_kinds_note),
        style = FieldTrackTheme.type.caption,
        color = colors.inkTertiary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun CostLine(
    label: String,
    amount: String,
    strong: Boolean = false,
    muted: Boolean = false,
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
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = if (strong) FieldTrackTheme.type.moneyMedium else FieldTrackTheme.type.body,
            color = if (muted) colors.inkTertiary else colors.ink,
        )
    }
}

@Composable
private fun EditLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = FieldTrackTheme.type.label,
        color = FieldTrackTheme.colors.primary,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = 12.dp),
    )
}
