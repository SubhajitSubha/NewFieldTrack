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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.BuildConfig
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.labelRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MoreState(
    val displayName: String = "",
    val businessName: String = "",
    val role: MemberRole = MemberRole.Owner,
    val machineCount: Int = 0,
    val customerCount: Int = 0,
    val pendingUploads: Int = 0,
    val rateCount: Int = 0,
    val canSeeMoney: Boolean = true,
    /** Its own switch: a colleague's wage is not a customer balance. */
    val canSeePeopleCost: Boolean = false,
    /** Null until a server has actually confirmed holding this phone's work. */
    val lastBackupAt: Long? = null,
    /** False for a driver, who may not rename the business they work for. */
    val canEditBusiness: Boolean = false,
    val memberCount: Int = 0,
    val canSeeTeam: Boolean = false,
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    /** False for a farmer trading under their own name rather than a business name. */
    val isBusiness: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MoreViewModel(app: FieldFlowApplication) : ViewModel() {


    val state: StateFlow<MoreState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.directoryRepository.observeMachines(s.orgId),
            app.directoryRepository.observeCustomers(s.orgId),
            app.db.outboxDao().observePendingCount(),
            app.teamRepository.observeTeam(s.orgId),
            // Paired because combine tops out at five typed sources, and these two
            // are both just small numbers for the rows below.
            combine(
                app.syncEngine.status,
                app.directoryRepository.observeRateCard(s.orgId),
            ) { sync, rates -> sync to rates.size },
        ) { machines, customers, pending, team, (sync, rateCount) ->
            MoreState(
                displayName = s.displayName,
                businessName = s.businessName,
                role = s.role,
                machineCount = machines.size,
                customerCount = customers.size,
                pendingUploads = pending,
                lastBackupAt = sync.lastSuccessAt,
                rateCount = rateCount,
                canSeeMoney = s.canSeeMoney,
                canSeePeopleCost = Capability.SeePeopleCost in s.can,
                canEditBusiness = Capability.EditBusiness in s.can,
                memberCount = team.size,
                // A one-person outfit has no team to manage; the row appears when
                // business mode is switched on, which is where drivers come from.
                canSeeTeam = app.sessionStore.isBusiness(),
                landUnit = s.landUnit,
                isBusiness = app.sessionStore.isBusiness(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }
}

/**
 * The catch-all tab.
 *
 * Shows only what exists. Rows for settings that have not been built would be a list
 * of doors opening onto nothing, which is a worse answer than saying plainly that
 * backup is not switched on yet.
 */
@Composable
fun MoreScreen(
    state: MoreState,
    onEditProfile: () -> Unit,
    onTeam: () -> Unit,
    /** Null for a driver: the business is not theirs to rename. */
    onEditBusiness: (() -> Unit)?,
    onBackup: () -> Unit,
    onRates: () -> Unit,
    onMoneyOut: () -> Unit,
    onSeason: () -> Unit,
    onMachines: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(title = stringResource(R.string.more_title))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            FtCard(padding = 16.dp, onClick = onEditProfile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FtAvatar(
                        initials = initialsOf(state.displayName),
                        size = 52.dp,
                        background = colors.primary,
                        contentColor = colors.onPrimary,
                    )
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            text = state.displayName,
                            style = FieldTrackTheme.type.sectionTitle,
                            color = colors.ink,
                        )
                        Text(
                            // A solo owner trades under their own name, so repeating
                            // it here would read as a bug rather than as information.
                            text = listOf(
                                state.businessName.takeIf { it != state.displayName },
                                stringResource(
                                    if (state.role == MemberRole.Owner) R.string.more_owner
                                    else R.string.more_operator
                                ),
                            ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(Modifier.weight(1f))
                    FtIconTile(
                        icon = FieldTrackIcons.Edit,
                        size = 38.dp,
                        background = colors.surfaceMuted,
                        tint = colors.inkSecondary,
                    )
                }
            }

            FtSectionLabel(stringResource(R.string.more_your_work))
            FtCard(padding = 0.dp) {
                FtListRow(
                    title = stringResource(R.string.machines_title),
                    leading = { FtIconTile(FieldTrackIcons.Tractor, size = 40.dp) },
                    trailing = {
                        Text(
                            text = pluralStringResource(
                                R.plurals.count_machines, state.machineCount, state.machineCount
                            ),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    },
                    // Was a dead row: it named a count and did nothing when pressed,
                    // on a screen where every row beside it goes somewhere.
                    showChevron = true,
                    onClick = onMachines,
                )
                if (state.canSeeTeam) {
                    FtRowDivider()
                    FtListRow(
                        title = stringResource(R.string.more_team),
                        leading = { FtIconTile(FieldTrackIcons.Team, size = 40.dp) },
                        trailing = {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.count_members, state.memberCount, state.memberCount
                                ),
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.inkTertiary,
                            )
                        },
                        showChevron = true,
                        onClick = onTeam,
                    )
                }
                FtRowDivider()
                FtListRow(
                    title = stringResource(R.string.customers_title),
                    leading = { FtIconTile(FieldTrackIcons.Customer, size = 40.dp) },
                    trailing = {
                        Text(
                            text = pluralStringResource(
                                R.plurals.count_customers, state.customerCount, state.customerCount
                            ),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    },
                )
                // Hidden outright, not merely unopenable. A rate card is nothing
                // but the prices this business charges, so showing it to somebody
                // explicitly denied money would hand over the whole price list
                // through a door marked "settings".
                if (state.canSeeMoney) {
                FtRowDivider()
                FtListRow(
                    title = stringResource(R.string.more_rates),
                    leading = {
                        FtIconTile(
                            icon = FieldTrackIcons.Bill,
                            size = 40.dp,
                            background = colors.dueWash,
                            tint = colors.due,
                        )
                    },
                    trailing = {
                        Text(
                            text = if (state.rateCount == 0) {
                                stringResource(R.string.more_rates_none)
                            } else {
                                pluralStringResource(
                                    R.plurals.more_rates_count, state.rateCount, state.rateCount
                                )
                            },
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    },
                    showChevron = true,
                    onClick = onRates,
                )
                }

                // Behind its own switch, not the money one. Customer balances
                // and what a colleague earns are not the same kind of secret.
                if (state.canSeePeopleCost) {
                FtRowDivider()
                FtListRow(
                    title = stringResource(R.string.more_money_out),
                    subtitle = stringResource(R.string.more_money_out_note),
                    leading = {
                        FtIconTile(
                            icon = FieldTrackIcons.Bill,
                            size = 40.dp,
                        )
                    },
                    showChevron = true,
                    onClick = onMoneyOut,
                )
                FtRowDivider()
                // Above money out in importance but below it on the list, because
                // this one is read once a season and that one is read weekly.
                FtListRow(
                    title = stringResource(R.string.season_title),
                    subtitle = stringResource(R.string.more_season_note),
                    leading = { FtIconTile(icon = FieldTrackIcons.Report, size = 40.dp) },
                    showChevron = true,
                    onClick = onSeason,
                )
                }
            }

            FtSectionLabel(stringResource(R.string.more_settings))
            FtCard(padding = 0.dp) {
                // The business now lives on the profile screen, reached from the
                // card above; a second door to the same room is just confusing.
                FtListRow(
                    title = stringResource(R.string.more_language),
                    subtitle = stringResource(R.string.more_language_soon),
                    leading = { FtIconTile(FieldTrackIcons.More, size = 40.dp) },
                    trailing = {
                        Text(
                            text = stringResource(R.string.more_language_value),
                            style = FieldTrackTheme.type.label,
                            color = colors.ink,
                        )
                    },
                )
                FtRowDivider()
                FtListRow(
                    title = stringResource(R.string.more_backup),
                    // What is actually true right now, rather than a fixed label.
                    // This row is the one place most people will ever look to find
                    // out whether their records exist anywhere but this phone.
                    subtitle = when {
                        state.pendingUploads > 0 -> stringResource(
                            R.string.more_backup_waiting,
                            pluralStringResource(
                                R.plurals.sync_changes_waiting,
                                state.pendingUploads,
                                state.pendingUploads,
                            ),
                        )
                        // Nothing queued is not the same as safely on a server.
                        state.lastBackupAt == null -> stringResource(R.string.sync_never)
                        else -> stringResource(R.string.sync_all_safe)
                    },
                    leading = {
                        FtIconTile(
                            icon = if (state.pendingUploads == 0 && state.lastBackupAt != null) {
                                FieldTrackIcons.Check
                            } else {
                                FieldTrackIcons.Offline
                            },
                            size = 40.dp,
                            background = colors.moneyWash,
                            tint = colors.money,
                        )
                    },
                    showChevron = true,
                    onClick = onBackup,
                )
            }

            FtSectionLabel(stringResource(R.string.more_help))
            FtCard {
                Text(
                    text = stringResource(R.string.more_help_offline_title),
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.more_help_offline_body),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            Text(
                text = stringResource(R.string.more_version, BuildConfig.VERSION_NAME),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.padding(bottom = dimens.space4))
        }
    }
}
