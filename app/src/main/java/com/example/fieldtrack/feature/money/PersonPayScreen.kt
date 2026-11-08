package com.example.fieldtrack.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.repository.HireBalance
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.labelRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One payment to a person, as the history lists it. */
data class PersonPayRow(
    val id: String,
    val atMillis: Long,
    val amountMinor: Long,
    val covers: PayoutCovers,
    val method: PaymentMethod?,
    val note: String?,
    val voided: Boolean,
)

data class PersonPayState(
    val loaded: Boolean = false,
    /** False for somebody who may not see what anybody is paid. */
    val mayLook: Boolean = false,
    val mayPay: Boolean = false,
    val name: String = "",
    val paidMinor: Long = 0,
    val advanceOutstandingMinor: Long = 0,
    /** What is owed for a machine of theirs the business uses, if there is one. */
    val hire: HireBalance? = null,
    val rows: List<PersonPayRow> = emptyList(),
)

/**
 * Everything paid to one person: what, when, how and for what, what they still owe from an
 * advance, what is owed them for a machine, and a way to take a mistaken payment back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonPayViewModel(
    private val app: FieldFlowApplication,
    private val membershipId: String,
) : ViewModel() {

    val state: StateFlow<PersonPayState> = app.sessionStore.session.flatMapLatest { s ->
        val mayLook = Capability.SeePeopleCost in s.can
        // A membership's id is the business's id and the person's, joined: see membershipId().
        val personId = membershipId.removePrefix(s.orgId + "_")
        combine(
            app.ledgerRepository.observePayoutsTo(s.orgId, personId),
            app.ledgerRepository.observeAdvanceOutstanding(s.orgId, personId),
            app.hireRepository.observe(s.orgId),
            app.teamRepository.observeEveryone(s.orgId),
        ) { payouts, advance, hire, people ->
            val name = people.firstOrNull { it.personId == personId }?.displayName.orEmpty()
            if (!mayLook) return@combine PersonPayState(loaded = true, name = name)
            PersonPayState(
                loaded = true,
                mayLook = true,
                mayPay = Capability.PayOut in s.can,
                name = name,
                paidMinor = payouts.filter { it.voidedAt == null }.sumOf { it.amountMinor },
                advanceOutstandingMinor = advance,
                hire = hire.firstOrNull { it.personId == personId },
                rows = payouts.map {
                    PersonPayRow(it.id, it.atMillis, it.amountMinor, it.covers, it.method, it.note, it.voidedAt != null)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonPayState())

    fun takeBack(payoutId: String) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        runCatching { app.ledgerRepository.voidPayout(s.orgId, s.can, payoutId, byPersonId = s.personId) }
    }
}

/**
 * What one person has been paid, drawn inside their page under Money: the total, an advance
 * still to take back, what is owed for their machine, every payment with what it was for,
 * and a way to pay them. A mistaken payment is taken back with a button on its own line,
 * never by a tap on a card that does not look like it can be tapped.
 */
@Composable
fun PersonPayContent(
    state: PersonPayState,
    onPay: () -> Unit,
    onTakeBack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (state.loaded && !state.mayLook) {
            FtCard {
                Text(
                    text = stringResource(R.string.money_out_locked),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            return@Column
        }
        FtCard(background = colors.deep, borderColor = colors.deep) {
            Text(
                text = stringResource(R.string.person_pay_paid).uppercase(),
                style = FieldTrackTheme.type.caption,
                color = colors.onDarkMuted,
            )
            Spacer(Modifier.height(3.dp))
            Text(text = money(state.paidMinor), style = FieldTrackTheme.type.moneyHero, color = colors.onDark)
        }
        if (state.mayPay) {
            FtButton(text = stringResource(R.string.person_pay_pay, state.name), onClick = onPay, icon = com.example.fieldtrack.designsystem.icon.FieldTrackIcons.Minus)
        }
        if (state.advanceOutstandingMinor > 0) {
            FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                Text(
                    text = stringResource(R.string.person_pay_advance, money(state.advanceOutstandingMinor)),
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.dueInk,
                )
            }
        }
        state.hire?.let { hire ->
            FtCard {
                Text(
                    text = stringResource(R.string.person_pay_hire, hire.machines.joinToString(", ") { it.name }),
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.hire_owed_paid, money(hire.owedMinor), money(hire.paidMinor)),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = stringResource(R.string.hire_left, money(hire.balanceMinor.coerceAtLeast(0))),
                    style = FieldTrackTheme.type.rowTitle,
                    color = if (hire.balanceMinor > 0) colors.dueInk else colors.ink,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (state.loaded && state.rows.isEmpty()) {
            FtCard {
                Text(
                    text = stringResource(R.string.person_pay_none),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
        }
        state.rows.groupBy { dayMonthYear(it.atMillis) }.forEach { (day, rows) ->
            FtSectionLabel(day)
            rows.forEach { row ->
                FtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    when (row.covers) {
                                        PayoutCovers.Job -> R.string.money_out_for_job
                                        PayoutCovers.Day -> R.string.money_out_for_day
                                        PayoutCovers.Period -> R.string.money_out_for_period
                                        PayoutCovers.Advance -> R.string.money_out_for_advance
                                        PayoutCovers.Hire -> R.string.money_out_for_hire
                                    }
                                ),
                                style = FieldTrackTheme.type.body.copy(
                                    textDecoration = if (row.voided) TextDecoration.LineThrough else null,
                                ),
                                color = if (row.voided) colors.inkTertiary else colors.ink,
                            )
                            Text(
                                text = listOfNotNull(
                                    row.method?.let { stringResource(it.labelRes) },
                                    row.note?.takeIf { it.isNotBlank() },
                                    if (row.voided) stringResource(R.string.person_pay_taken_back) else null,
                                ).joinToString(" · "),
                                style = FieldTrackTheme.type.caption,
                                color = colors.inkSecondary,
                            )
                        }
                        Text(
                            text = money(row.amountMinor),
                            style = FieldTrackTheme.type.sectionTitle.copy(
                                textDecoration = if (row.voided) TextDecoration.LineThrough else null,
                            ),
                            color = if (row.voided) colors.inkTertiary else colors.ink,
                        )
                    }
                    if (state.mayPay && !row.voided) {
                        TextButton(
                            onClick = { confirming = row.id },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                text = stringResource(R.string.person_pay_back_button),
                                style = FieldTrackTheme.type.label,
                                color = colors.dueInk,
                            )
                        }
                    }
                }
            }
        }
    }

    confirming?.let { id ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.person_pay_back_title)) },
            text = { Text(stringResource(R.string.person_pay_back_body)) },
            confirmButton = {
                TextButton(onClick = { onTakeBack(id); confirming = null }) {
                    Text(stringResource(R.string.person_pay_back_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.person_pay_back_no)) }
            },
        )
    }
}
