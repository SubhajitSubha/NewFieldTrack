package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.labelRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileState(
    val loaded: Boolean = false,
    val displayName: String = "",
    val role: MemberRole = MemberRole.Owner,
    val businessName: String = "",
    val addressLine: String? = null,
    val phone: String? = null,
    val upiId: String? = null,
    val gstin: String? = null,
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    val businessMode: Boolean = false,
    /** False for a driver, who may not change the business they work for. */
    val canEditBusiness: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<ProfileState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.db.orgDao().observeOrg(s.orgId),
            app.sessionStore.businessMode,
        ) { org, businessMode ->
            ProfileState(
                loaded = true,
                displayName = s.displayName,
                role = s.role,
                businessName = org?.name.orEmpty(),
                addressLine = org?.addressLine,
                phone = org?.phone,
                upiId = org?.upiId,
                gstin = org?.gstin,
                landUnit = org?.defaultLandUnit ?: MeasureUnit.Acre,
                businessMode = businessMode,
                canEditBusiness = Capability.EditBusiness in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    fun setBusinessMode(enabled: Boolean) = app.sessionStore.setBusinessMode(enabled)
}

/**
 * Everything about the person and the business they trade as.
 *
 * One screen rather than two, because from the farmer's side it is one question —
 * "what does the app know about me" — and because the answers are what print at the
 * top of every bill they send. Each row shows what is set now and opens the form
 * that changes it; a row that is blank says so rather than sitting empty.
 */
@Composable
fun ProfileScreen(
    state: ProfileState,
    onBack: () -> Unit,
    onEditName: () -> Unit,
    onEditBusiness: () -> Unit,
    onBusinessMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val notSet = stringResource(R.string.profile_not_set)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.profile_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                FtAvatar(
                    initials = initialsOf(state.displayName),
                    size = 88.dp,
                    background = colors.primary,
                    contentColor = colors.onPrimary,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.displayName,
                        style = FieldTrackTheme.type.sectionTitle,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(
                            if (state.role == MemberRole.Owner) R.string.more_owner
                            else R.string.more_operator
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            FtSectionLabel(stringResource(R.string.profile_you))
            FtCard(padding = 0.dp) {
                ValueRow(
                    label = stringResource(R.string.onboard_your_name),
                    value = state.displayName,
                    onClick = onEditName,
                )
            }

            FtSectionLabel(stringResource(R.string.profile_business))
            FtCard(padding = 0.dp) {
                // Every one of these prints at the top of a bill, which is why they
                // are shown here with their values rather than hidden behind a form.
                val open = onEditBusiness.takeIf { state.canEditBusiness }
                ValueRow(
                    label = stringResource(R.string.onboard_business_name),
                    value = state.businessName,
                    onClick = open,
                )
                FtRowDivider()
                ValueRow(
                    label = stringResource(R.string.profile_address),
                    value = state.addressLine ?: notSet,
                    muted = state.addressLine == null,
                    onClick = open,
                )
                FtRowDivider()
                ValueRow(
                    label = stringResource(R.string.edit_business_phone),
                    value = state.phone?.let { "+91 $it" } ?: notSet,
                    muted = state.phone == null,
                    onClick = open,
                )
                FtRowDivider()
                ValueRow(
                    label = stringResource(R.string.edit_business_upi),
                    value = state.upiId ?: notSet,
                    muted = state.upiId == null,
                    onClick = open,
                )
                FtRowDivider()
                ValueRow(
                    label = stringResource(R.string.edit_business_gstin),
                    value = state.gstin ?: notSet,
                    muted = state.gstin == null,
                    onClick = open,
                )
            }

            FtSectionLabel(stringResource(R.string.profile_measuring))
            FtCard(padding = 0.dp) {
                ValueRow(
                    label = stringResource(R.string.profile_land_measured_in),
                    value = stringResource(state.landUnit.labelRes),
                    onClick = onEditBusiness.takeIf { state.canEditBusiness },
                )
                FtRowDivider()
                Row(
                    Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_business_mode),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.profile_business_mode_note),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = state.businessMode,
                        onCheckedChange = onBusinessMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.surface,
                            uncheckedTrackColor = colors.line,
                        ),
                    )
                }
            }

            Box(Modifier.padding(bottom = dimens.space4))
        }
    }
}

/** A label on the left, what it is set to on the right, and a way to change it. */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    muted: Boolean = false,
) {
    FtListRow(
        title = label,
        trailing = {
            // Capped and clipped: a long address or UPI id would otherwise take the
            // width it wants and push the label it belongs to off the screen.
            Text(
                text = value,
                style = FieldTrackTheme.type.label,
                color = if (muted) FieldTrackTheme.colors.inkTertiary
                else FieldTrackTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 170.dp),
            )
        },
        showChevron = onClick != null,
        onClick = onClick,
    )
}
