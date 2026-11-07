package com.example.fieldtrack.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.billing.JobTerms
import com.example.fieldtrack.data.db.FuelTerms
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyInput
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * The terms of a job as they are being typed: what is agreed beyond the rate.
 *
 * Text for the amounts, so a field can be emptied and retyped without fighting back; the
 * real numbers come out of [toTerms]. [open] is only whether the section is unfolded.
 */
data class TermsDraft(
    val minimumText: String = "",
    val arrivalText: String = "",
    val waitingText: String = "",
    val fuelTerms: FuelTerms = FuelTerms.Included,
    val implementMachineId: String? = null,
    val coOperatorIds: Set<String> = emptySet(),
    val open: Boolean = false,
) {
    fun toTerms() = JobTerms(
        minimumMinor = MoneyInput.parse(minimumText) ?: 0L,
        arrivalMinor = MoneyInput.parse(arrivalText) ?: 0L,
        fuelTerms = fuelTerms,
        waitingRateMinor = MoneyInput.parse(waitingText) ?: 0L,
        implementMachineId = implementMachineId,
        coOperatorIds = coOperatorIds.sorted().joinToString(",").ifBlank { null },
    )

    /** Whether anything is agreed beyond the plain rate. */
    val isPlain: Boolean get() = toTerms().isPlain

    companion object {
        /** The usual terms of a rate, for a job being set up from it. Machines and people are the job's own. */
        fun of(rate: RateEntity?, keep: TermsDraft = TermsDraft()) = if (rate == null) keep else keep.copy(
            minimumText = if (rate.minimumMinor > 0) MoneyInput.text(rate.minimumMinor) else "",
            arrivalText = if (rate.arrivalMinor > 0) MoneyInput.text(rate.arrivalMinor) else "",
            waitingText = if (rate.waitingRateMinor > 0) MoneyInput.text(rate.waitingRateMinor) else "",
            fuelTerms = rate.fuelTerms,
        )
    }
}

/** Who may be put on a job, by id and name. */
data class Crew(val id: String, val name: String)

/**
 * "Terms": a minimum, an arrival charge, who pays for the diesel, waiting time, the
 * implement, and anyone else on the job.
 *
 * Folded away until asked for, because most jobs are just a rate and a quantity and a
 * farmer handing over a phone should not be walked past five questions that do not apply.
 * When something is agreed the folded line says what, so it is never hidden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermsSection(
    draft: TermsDraft,
    onChange: (TermsDraft) -> Unit,
    modifier: Modifier = Modifier,
    /** Machines that can be the implement. Empty hides the question (the rate card has no job to put one on). */
    implements: List<MachineEntity> = emptyList(),
    /** People who can be added to the job. Empty hides the question. */
    crew: List<Crew> = emptyList(),
) {
    val colors = FieldTrackTheme.colors
    val terms = draft.toTerms()
    val summary = listOfNotNull(
        terms.minimumMinor.takeIf { it > 0 }?.let { stringResource(R.string.terms_sum_minimum, money(it)) },
        terms.arrivalMinor.takeIf { it > 0 }?.let { stringResource(R.string.terms_sum_arrival, money(it)) },
        terms.waitingRateMinor.takeIf { it > 0 }?.let { stringResource(R.string.terms_sum_waiting, money(it)) },
        when (terms.fuelTerms) {
            FuelTerms.Included -> null
            FuelTerms.CustomerSupplies -> stringResource(R.string.terms_sum_fuel_theirs)
            FuelTerms.BilledExtra -> stringResource(R.string.terms_sum_fuel_billed)
        },
        terms.implementMachineId?.let { id -> implements.firstOrNull { it.id == id }?.name },
        terms.coOperatorIds?.let { stringResource(R.string.terms_sum_crew, it.split(',').size) },
    ).joinToString(" · ").ifBlank { stringResource(R.string.terms_none) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FtCard(padding = 0.dp) {
            FtListRow(
                title = stringResource(R.string.terms_title),
                subtitle = summary,
                subtitleMaxLines = 3,
                showChevron = true,
                onClick = { onChange(draft.copy(open = !draft.open)) },
            )
        }
        if (draft.open) {
            val amountKeyboard = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number
            FtTextField(
                label = stringResource(R.string.terms_minimum),
                value = draft.minimumText,
                onValueChange = { onChange(draft.copy(minimumText = MoneyInput.clean(it))) },
                prefix = Money.style.symbol,
                keyboardType = amountKeyboard,
                helper = stringResource(R.string.terms_minimum_note),
            )
            FtTextField(
                label = stringResource(R.string.terms_arrival),
                value = draft.arrivalText,
                onValueChange = { onChange(draft.copy(arrivalText = MoneyInput.clean(it))) },
                prefix = Money.style.symbol,
                keyboardType = amountKeyboard,
                helper = stringResource(R.string.terms_arrival_note),
            )
            FtSectionLabel(stringResource(R.string.terms_diesel))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    FuelTerms.Included to R.string.terms_diesel_included,
                    FuelTerms.CustomerSupplies to R.string.terms_diesel_theirs,
                    FuelTerms.BilledExtra to R.string.terms_diesel_billed,
                ).forEach { (option, label) ->
                    FtChip(
                        label = stringResource(label),
                        selected = draft.fuelTerms == option,
                        onClick = { onChange(draft.copy(fuelTerms = option)) },
                    )
                }
            }
            Text(
                text = stringResource(
                    when (draft.fuelTerms) {
                        FuelTerms.Included -> R.string.terms_diesel_included_note
                        FuelTerms.CustomerSupplies -> R.string.terms_diesel_theirs_note
                        FuelTerms.BilledExtra -> R.string.terms_diesel_billed_note
                    }
                ),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
            FtTextField(
                label = stringResource(R.string.terms_waiting),
                value = draft.waitingText,
                onValueChange = { onChange(draft.copy(waitingText = MoneyInput.clean(it))) },
                prefix = Money.style.symbol,
                suffix = stringResource(R.string.terms_per_hour),
                keyboardType = amountKeyboard,
                helper = stringResource(R.string.terms_waiting_note),
            )
            if (implements.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.terms_implement))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FtChip(
                        label = stringResource(R.string.terms_no_implement),
                        selected = draft.implementMachineId == null,
                        onClick = { onChange(draft.copy(implementMachineId = null)) },
                    )
                    implements.forEach { machine ->
                        FtChip(
                            label = machine.name,
                            selected = draft.implementMachineId == machine.id,
                            onClick = { onChange(draft.copy(implementMachineId = machine.id)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.terms_implement_note),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            }
            if (crew.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.terms_crew))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    crew.forEach { person ->
                        FtChip(
                            label = person.name,
                            selected = person.id in draft.coOperatorIds,
                            onClick = {
                                onChange(
                                    draft.copy(
                                        coOperatorIds = if (person.id in draft.coOperatorIds)
                                            draft.coOperatorIds - person.id else draft.coOperatorIds + person.id
                                    )
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.terms_crew_note),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}
