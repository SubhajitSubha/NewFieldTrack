package com.example.fieldtrack.feature.more

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.billing.store.StoreOffer
import com.example.fieldtrack.data.billing.store.StorePeriod
import com.example.fieldtrack.data.plan.Need
import com.example.fieldtrack.data.plan.PlanLimits
import com.example.fieldtrack.data.plan.PlanStatus
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PricingScreenState(
    val loaded: Boolean = false,
    val status: PlanStatus = PlanStatus.Free,
    val machines: Int = 0,
    val people: Int = 0,
    val offers: List<StoreOffer> = emptyList(),
    /** Whether this phone can buy at all: it has a store, and its owner is the one asking. */
    val canBuy: Boolean = false,
    val untilLabel: String? = null,
)

/**
 * What the business is on, what that allows, and how to change it.
 *
 * Kept honest about the one promise the plan makes: nothing is ever taken away. A plan that
 * lapses stops *adding* a team, machines and the extras, and nothing else; every record stays
 * visible, every export works, and everybody can keep recording work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PricingViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val restoreNote = MutableStateFlow<Boolean?>(null)

    val state: StateFlow<PricingScreenState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.db.orgDao().observeEntitlement(s.orgId),
            app.storeBilling.offers,
        ) { entitlement, offers ->
            val today = java.time.LocalDate.now(com.example.fieldtrack.data.region.BusinessClock.zone).toEpochDay()
            PricingScreenState(
                loaded = true,
                status = com.example.fieldtrack.data.plan.Plans.status(entitlement, today),
                machines = app.db.machineDao().liveMachineCount(s.orgId),
                people = app.db.orgDao().activeMemberCount(s.orgId),
                offers = offers,
                canBuy = Capability.EditBusiness in s.can && offers.isNotEmpty(),
                untilLabel = entitlement?.untilDay?.let { day ->
                    com.example.fieldtrack.feature.dayMonthYear(
                        java.time.LocalDate.ofEpochDay(day).atStartOfDay(com.example.fieldtrack.data.region.BusinessClock.zone).toInstant().toEpochMilli()
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PricingScreenState())

    init {
        viewModelScope.launch {
            app.sessionStore.load()
            app.storeBilling.connect()
        }
        // A purchase that goes through while this is open is handed to the server at once.
        viewModelScope.launch {
            app.storeBilling.completed.collect { purchase -> runCatching { app.purchaseClaims.claim(purchase) } }
        }
    }

    fun buy(activity: Activity, offer: StoreOffer) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        app.storeBilling.buy(activity, offer, s.orgId)
    }

    /** For somebody who paid before and has a new phone, or whose payment has not shown yet. */
    fun restore() = viewModelScope.launch {
        app.storeBilling.connect()
        app.storeBilling.active().forEach { runCatching { app.purchaseClaims.claim(it) } }
    }
}

@Composable
fun PricingScreen(
    state: PricingScreenState,
    need: Need?,
    onBuy: (StoreOffer) -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.pricing_title),
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
            need?.let {
                FtCard(background = colors.moneyWash, borderColor = colors.moneyWash) {
                    Text(
                        when (it) {
                            Need.MoreMachines -> stringResource(R.string.plan_need_machines, PlanLimits.FREE_MACHINES)
                            Need.TeamMember -> stringResource(R.string.plan_need_team)
                            Need.BookingLink -> stringResource(R.string.plan_need_link)
                            Need.ProofOfWork -> stringResource(R.string.plan_need_proof)
                        },
                        style = FieldTrackTheme.type.bodySmall, color = colors.moneyInk,
                    )
                }
            }
            FtCard {
                Text(stringResource(R.string.plan_you_are).uppercase(), style = FieldTrackTheme.type.overline, color = colors.inkTertiary)
                Text(
                    stringResource(
                        when (state.status) {
                            PlanStatus.Free, PlanStatus.Lapsed -> R.string.plan_free_name
                            is PlanStatus.Active, is PlanStatus.Grace -> R.string.plan_business_name
                        }
                    ),
                    style = FieldTrackTheme.type.screenTitle, color = colors.ink, modifier = Modifier.padding(top = 4.dp),
                )
                when (val st = state.status) {
                    is PlanStatus.Active -> Text(
                        listOfNotNull(
                            state.untilLabel?.let { stringResource(R.string.plan_runs_to, it) },
                            if (st.founding) stringResource(R.string.plan_founding) else null,
                        ).joinToString(" · "),
                        style = FieldTrackTheme.type.bodySmall, color = colors.inkSecondary, modifier = Modifier.padding(top = 4.dp),
                    )
                    is PlanStatus.Grace -> Text(
                        pluralStringResource(R.plurals.plan_grace, st.graceLeft.toInt(), st.graceLeft.toInt()),
                        style = FieldTrackTheme.type.bodySmall, color = colors.dueInk, modifier = Modifier.padding(top = 4.dp),
                    )
                    PlanStatus.Lapsed -> Text(stringResource(R.string.plan_lapsed), style = FieldTrackTheme.type.bodySmall, color = colors.inkSecondary, modifier = Modifier.padding(top = 4.dp))
                    PlanStatus.Free -> Unit
                }
                Text(
                    stringResource(R.string.plan_usage, state.machines, state.people),
                    style = FieldTrackTheme.type.caption, color = colors.inkTertiary, modifier = Modifier.padding(top = 6.dp),
                )
            }

            FtSectionLabel(stringResource(R.string.plan_free_has))
            FtCard { Text(stringResource(R.string.plan_free_list, PlanLimits.FREE_MACHINES), style = FieldTrackTheme.type.bodySmall, color = colors.ink) }
            FtSectionLabel(stringResource(R.string.plan_business_adds))
            FtCard { Text(stringResource(R.string.plan_business_list), style = FieldTrackTheme.type.bodySmall, color = colors.ink) }

            if (state.canBuy) {
                FtSectionLabel(stringResource(R.string.plan_choose))
                state.offers.sortedBy { it.period }.forEach { offer ->
                    FtButton(
                        text = stringResource(
                            if (offer.period == StorePeriod.Season) R.string.plan_buy_season else R.string.plan_buy_year,
                            offer.priceText,
                        ) + if (offer.founding) " · " + stringResource(R.string.plan_founding_short) else "",
                        onClick = { onBuy(offer) },
                        tone = if (offer.period == StorePeriod.Year) FtButtonTone.Primary else FtButtonTone.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(stringResource(R.string.plan_season_note), style = FieldTrackTheme.type.caption, color = colors.inkTertiary)
            } else if (state.status == PlanStatus.Free || state.status == PlanStatus.Lapsed) {
                Text(stringResource(R.string.plan_no_store), style = FieldTrackTheme.type.caption, color = colors.inkTertiary)
            }
            FtButton(
                text = stringResource(R.string.plan_restore),
                onClick = onRestore,
                tone = FtButtonTone.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.plan_promise), style = FieldTrackTheme.type.caption, color = colors.inkSecondary)
            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}
