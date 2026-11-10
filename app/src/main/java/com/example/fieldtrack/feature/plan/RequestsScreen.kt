package com.example.fieldtrack.feature.plan

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.auth.PhoneNumbers
import com.example.fieldtrack.data.auth.formatPhone
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.db.RequestStatus
import com.example.fieldtrack.data.db.entity.BookingRequestEntity
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.Sharing
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.workLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RequestsState(
    val loaded: Boolean = false,
    val requests: List<BookingRequestEntity> = emptyList(),
    val canPlan: Boolean = false,
)

/**
 * What farmers have asked for through the business's public page.
 *
 * A request is only ever that. Accepting one makes a booking on the day asked for (or tomorrow
 * when none was) in the farmer's own account, matched by phone number or made new, and opens it
 * so the planner can give it a machine and a driver. Declining marks it; either way the farmer
 * hears nothing from the app, only from whoever calls or messages them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<RequestsState> = app.sessionStore.session.flatMapLatest { s ->
        app.planRepository.observeRequests(s.orgId).map {
            RequestsState(loaded = true, requests = it, canPlan = Capability.PlanWork in s.can)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestsState())

    init { viewModelScope.launch { app.sessionStore.load() } }

    /** Accepts [id] and hands back the booking made, or null if somebody else had already dealt with it. */
    fun accept(id: String, onBooked: (String) -> Unit) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        val request = app.planRepository.findRequest(s.orgId, id)?.takeIf { it.status == RequestStatus.New } ?: return@launch
        val customerId = customerFor(s.orgId, request, s.personId)
        val settings = app.directoryRepository.observePlanSettings(s.orgId).first()
        val day = request.day ?: (PlanDay.today(System.currentTimeMillis(), settings.dayStartHour) + 1)
        val bookingId = app.planRepository.save(
            can = s.can, orgId = s.orgId, id = null, customerId = customerId, fieldId = null,
            workType = request.workType ?: "Other", day = day, part = DayPart.WholeDay, startMinute = null,
            machineId = null, operatorPersonId = null, note = request.note, byPersonId = s.personId,
        )
        if (app.planRepository.settleRequest(s.can, s.orgId, id, RequestStatus.Accepted, bookingId)) onBooked(bookingId)
    }

    fun decline(id: String) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        app.planRepository.settleRequest(s.can, s.orgId, id, RequestStatus.Declined)
    }

    /** The farmer in this business with that phone number, or a new one. */
    private suspend fun customerFor(orgId: String, request: BookingRequestEntity, personId: String): String {
        val wanted = PhoneNumbers.normalize(request.phone).takeIf { it.isNotEmpty() }
        if (wanted != null) {
            app.directoryRepository.observeCustomers(orgId).first()
                .firstOrNull { PhoneNumbers.normalize(it.phone) == wanted }?.let { return it.id }
        }
        return app.directoryRepository.addCustomer(orgId, request.name, wanted, request.village, byPersonId = personId)
    }
}

@Composable
fun RequestsScreen(
    state: RequestsState,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val context = LocalContext.current
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.requests_title),
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
            if (state.loaded && state.requests.isEmpty()) {
                Text(stringResource(R.string.requests_none), style = FieldTrackTheme.type.body, color = colors.inkSecondary)
            }
            state.requests.forEach { r ->
                FtCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(r.name, style = FieldTrackTheme.type.rowTitle, color = colors.ink)
                        Text(
                            listOfNotNull(
                                r.workType?.let { workLabel(it) },
                                r.day?.let { dayMonthYear(java.time.LocalDate.ofEpochDay(it).atStartOfDay(com.example.fieldtrack.data.region.BusinessClock.zone).toInstant().toEpochMilli()) }
                                    ?: stringResource(R.string.requests_any_day),
                                r.village,
                            ).joinToString(" · "),
                            style = FieldTrackTheme.type.bodySmall, color = colors.inkSecondary,
                        )
                        r.phone?.let { Text(formatPhone(it), style = FieldTrackTheme.type.bodySmall, color = colors.inkSecondary) }
                        r.note?.let { Text(it, style = FieldTrackTheme.type.caption, color = colors.inkTertiary) }
                        when (r.status) {
                            RequestStatus.New -> if (state.canPlan) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    FtButton(
                                        text = stringResource(R.string.requests_decline), onClick = { onDecline(r.id) },
                                        tone = FtButtonTone.Quiet, height = 44.dp, modifier = Modifier.weight(1f),
                                    )
                                    FtButton(
                                        text = stringResource(R.string.requests_accept), onClick = { onAccept(r.id) },
                                        height = 44.dp, modifier = Modifier.weight(1f),
                                    )
                                }
                                if (r.phone != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        FtButton(
                                            text = stringResource(R.string.requests_call), onClick = { dial(context, r.phone) },
                                            tone = FtButtonTone.Secondary, height = 44.dp, modifier = Modifier.weight(1f),
                                        )
                                        FtButton(
                                            text = stringResource(R.string.requests_message),
                                            onClick = { Sharing.message(context, r.phone, context.getString(R.string.requests_reply, r.name)) },
                                            tone = FtButtonTone.Secondary, height = 44.dp, modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            RequestStatus.Accepted -> Text(stringResource(R.string.requests_accepted), style = FieldTrackTheme.type.caption, color = colors.moneyInk)
                            RequestStatus.Declined -> Text(stringResource(R.string.requests_declined), style = FieldTrackTheme.type.caption, color = colors.inkTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}

private fun dial(context: Context, phone: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${PhoneNumbers.normalize(phone)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
