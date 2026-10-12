package com.example.fieldtrack.feature.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A job this payment would settle, named as the farmer would name it. */
data class ClearedLine(
    val title: String,
    val dateLabel: String,
    val clearedRupees: Long,
    val fully: Boolean,
)

data class RecordPaymentState(
    val customerName: String = "",
    val owedRupees: Long = 0,
    /**
     * What the amount typed so far would settle, oldest debt first.
     *
     * The point of the screen for a farmer handing over a season's takings: not just
     * "₹9,000 received" but which jobs that closes and which one it leaves half paid.
     */
    val clears: List<ClearedLine> = emptyList(),
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.Cash,
    val saving: Boolean = false,
) {
    val amountRupees: Long get() = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val canSave: Boolean get() = amountRupees > 0 && !saving
    val leftAfterRupees: Long get() = (owedRupees - amountRupees).coerceAtLeast(0)
}

class RecordPaymentViewModel(
    private val app: FieldFlowApplication,
    private val customerId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordPaymentState())
    val state: StateFlow<RecordPaymentState> = _state.asStateFlow()

    /** The khata as it stands, so the allocation preview needs no round trip. */
    private var entries: List<LedgerEntryEntity> = emptyList()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val customer = app.directoryRepository.observeCustomer(s.orgId, customerId).first()
            val owed = app.ledgerRepository.observeBalance(s.orgId, customerId).first()
            entries = app.ledgerRepository.observeLedger(s.orgId, customerId).first()
            _state.update {
                it.copy(customerName = customer?.name.orEmpty(), owedRupees = owed)
            }
        }
    }

    fun setAmount(value: String) = _state.update {
        withPreview(it.copy(amountText = value.filter { c -> c.isDigit() }))
    }

    fun takeAll() = _state.update { withPreview(it.copy(amountText = it.owedRupees.toString())) }

    /** Recomputes which jobs the typed amount would settle. */
    private fun withPreview(state: RecordPaymentState): RecordPaymentState =
        state.copy(
            clears = clearedBy(entries, state.amountRupees).map {
                ClearedLine(
                    title = it.entry.note.orEmpty(),
                    dateLabel = dayMonth(it.entry.atMillis),
                    clearedRupees = it.clearedRupees,
                    fully = it.fully,
                )
            },
        )

    fun setMethod(method: PaymentMethod) = _state.update { it.copy(method = method) }

    fun save(onDone: (Long) -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: return@launch
        app.ledgerRepository.recordPayment(
            orgId = s.orgId,
            customerId = customerId,
            amountRupees = current.amountRupees,
            method = current.method,
            byPersonId = s.personId,
        )
        _state.update { it.copy(saving = false) }
        onDone(current.amountRupees)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordPaymentScreen(
    state: RecordPaymentState,
    onBack: () -> Unit,
    onAmount: (String) -> Unit,
    onTakeAll: () -> Unit,
    onMethod: (PaymentMethod) -> Unit,
    onSaveAndSend: () -> Unit,
    onJustSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(R.string.payment_title),
            subtitle = stringResource(R.string.payment_from, state.customerName),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_close_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FtCard(padding = 15.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.payment_currently_owes),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = rupees(state.owedRupees),
                        style = FieldTrackTheme.type.moneyMedium,
                        color = colors.due,
                    )
                }
            }

            FtTextField(
                label = stringResource(R.string.payment_amount),
                value = state.amountText,
                onValueChange = onAmount,
                prefix = "₹",
                keyboardType = KeyboardType.Number,
                focused = true,
            )

            // Quick amounts, plus the whole balance. Most payments in this business
            // are a round number or the lot.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(2_000L, 5_000L, 10_000L).forEach { amount ->
                    FtChip(
                        label = rupees(amount),
                        selected = state.amountRupees == amount,
                        onClick = { onAmount(amount.toString()) },
                    )
                }
                if (state.owedRupees > 0) {
                    FtChip(
                        label = stringResource(R.string.payment_all_of_it),
                        selected = state.amountRupees == state.owedRupees,
                        onClick = onTakeAll,
                    )
                }
            }

            // What this money actually closes. A farmer handing over the season's
            // takings is not thinking "₹9,000 received", they are thinking "that is
            // the harvesting and the levelling done, and half the hauling".
            if (state.clears.isNotEmpty()) {
                FtCard(background = colors.primaryWash, borderColor = colors.primaryWash) {
                    FtSectionLabel(
                        stringResource(R.string.payment_clears),
                        color = colors.primaryPressed,
                    )
                    state.clears.take(CLEARS_SHOWN).forEach { line ->
                        Row(
                            Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = listOfNotNull(
                                    line.title.ifBlank { stringResource(R.string.finish_this_job) },
                                    line.dateLabel,
                                ).joinToString(" · "),
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (line.fully) rupees(line.clearedRupees)
                                else stringResource(
                                    R.string.payment_clears_part,
                                    rupees(line.clearedRupees),
                                ),
                                style = FieldTrackTheme.type.caption,
                                color = if (line.fully) colors.primaryPressed else colors.inkSecondary,
                            )
                        }
                    }
                    if (state.clears.size > CLEARS_SHOWN) {
                        Text(
                            text = stringResource(
                                R.string.payment_clears_more,
                                state.clears.size - CLEARS_SHOWN,
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (state.amountRupees > state.owedRupees) {
                        Text(
                            text = stringResource(
                                R.string.payment_leaves_credit,
                                rupees(state.amountRupees - state.owedRupees),
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Column {
                Text(
                    text = stringResource(R.string.payment_how_paid).uppercase(),
                    style = FieldTrackTheme.type.overline,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentMethod.entries.forEach { method ->
                        FtChip(
                            label = stringResource(method.labelRes()),
                            selected = state.method == method,
                            onClick = { onMethod(method) },
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(FieldTrackTheme.shapes.medium)
                    .background(colors.primaryWash)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.payment_left_after),
                    style = FieldTrackTheme.type.label,
                    color = colors.primaryPressed,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = rupees(state.leftAfterRupees),
                    style = FieldTrackTheme.type.moneyMedium,
                    color = colors.primaryPressed,
                )
            }

            Box(Modifier.padding(bottom = dimens.space4))
        }

        Column(
            Modifier.padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FtButton(
                text = stringResource(R.string.payment_save_and_send),
                onClick = onSaveAndSend,
                enabled = state.canSave,
                height = 60.dp,
            )
            FtButton(
                text = stringResource(R.string.payment_just_save),
                onClick = onJustSave,
                tone = FtButtonTone.Quiet,
                enabled = state.canSave,
                height = 50.dp,
            )
        }
    }
}

@Composable
private fun PaymentMethod.labelRes(): Int = when (this) {
    PaymentMethod.Cash -> R.string.method_cash
    PaymentMethod.Upi -> R.string.method_upi
    PaymentMethod.Bank -> R.string.method_bank
    PaymentMethod.Cheque -> R.string.method_cheque
}

/** Enough to recognise the jobs without turning the card into a list. */
private const val CLEARS_SHOWN = 4
