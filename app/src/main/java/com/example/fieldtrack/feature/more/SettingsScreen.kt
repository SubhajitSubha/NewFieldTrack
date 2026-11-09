package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.auth.formatPhone
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.plan.PlanSettings
import com.example.fieldtrack.data.season.SeasonCalendar
import com.example.fieldtrack.data.season.SeasonStart
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.settings.AppLanguage
import com.example.fieldtrack.data.settings.LockAfter
import com.example.fieldtrack.data.settings.ThemeChoice
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtDropdown
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtPick
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtSwitchRow
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.plan.timeOfDay
import com.example.fieldtrack.feature.season.seasonStartsSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the account section needs to offer adding a Google account to the sign-in. */
data class GoogleLink(val email: String?, val note: String?, val onAdd: () -> Unit)

data class SettingsState(
    val loaded: Boolean = false,
    val displayName: String = "",
    val role: MemberRole = MemberRole.Owner,
    val businessName: String = "",
    val orgId: String = "",
    val personId: String = "",
    /**
     * False for the only owner of a business.
     *
     * Not a permission so much as an arithmetic fact: a business whose last owner
     * walks out can never have its team, its rates or its details changed again
     * by anybody.
     */
    val canLeave: Boolean = false,
    /**
     * The number this person signed in with, which is not the business's number on its
     * bills: a contractor's own mobile and the one he prints are routinely different.
     */
    val signedInPhone: String? = null,
    /** The Google account they signed in with or added, if any. */
    val signedInEmail: String? = null,
    /** Businesses that go with the account if it is deleted: theirs alone, nobody else to run them. */
    val soleOwnerOf: List<String> = emptyList(),
    /** Advance this person still owes the business, said when they are about to leave it. */
    val advanceOwedMinor: Long = 0,
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    /** The name of the business's own land unit, as saved. */
    val ownLandUnit: String = "",
    /** The currency the business keeps its books in. */
    val currencyCode: String = "INR",
    /** When the business's seasons begin, as the owner set them (or the usual dates). */
    val seasonStarts: List<SeasonStart> = SeasonCalendar.Default.starts,
    /** How the plan is read: the hour its day turns over, and whether drivers see everybody's work. */
    val plan: PlanSettings = PlanSettings(),
    val businessMode: Boolean = false,
    /** The business asks its crew for proof of work. */
    val proofOfWork: Boolean = false,
    /** False for anybody who may not change the business they work for. */
    val canEditBusiness: Boolean = false,
    val bookingLinkOn: Boolean = false,
    /** Changes made on this phone that have not reached the server yet. */
    val pendingUploads: Int = 0,
    /** Null until a server has actually confirmed holding this phone's work. */
    val lastBackupAt: Long? = null,
)

/** Choices that belong to this phone rather than to the business: they travel nowhere. */
data class PhonePrefs(
    val language: String? = null,
    val theme: ThemeChoice = ThemeChoice.System,
    val biggerText: Boolean = false,
    /** Whether the phone has a screen lock the app can ask for at all. */
    val lockAvailable: Boolean = false,
    val lockOn: Boolean = false,
    val lockAfter: LockAfter = LockAfter.Right,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<SettingsState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.db.orgDao().observeOrg(s.orgId),
            app.sessionStore.businessMode,
            app.teamRepository.observeTeam(s.orgId),
            app.db.outboxDao().observePendingCount(),
            app.syncEngine.status,
        ) { org, businessMode, team, pending, sync ->
            val otherOwners = team.count {
                it.role == MemberRole.Owner && it.active && it.personId != s.personId
            }
            SettingsState(
                loaded = true,
                displayName = s.displayName,
                role = s.role,
                businessName = org?.name.orEmpty(),
                landUnit = org?.defaultLandUnit ?: MeasureUnit.Acre,
                ownLandUnit = org?.ownLandUnit.orEmpty(),
                currencyCode = org?.currencyCode ?: "INR",
                seasonStarts = SeasonCalendar.parse(org?.seasons).starts,
                plan = PlanSettings(PlanSettings.cleanHour(org?.planDayStartHour), org?.driversSeeAll ?: false),
                businessMode = businessMode,
                proofOfWork = org?.proofOfWork == true,
                canEditBusiness = Capability.EditBusiness in s.can,
                bookingLinkOn = org?.bookingLink == true,
                orgId = s.orgId,
                personId = s.personId,
                // A driver may always leave. An owner may only leave if somebody
                // else can still run the place.
                canLeave = s.role != MemberRole.Owner || otherOwners > 0,
                signedInPhone = app.sessionStore.signedInPhone(),
                signedInEmail = app.sessionStore.signedInEmail(),
                soleOwnerOf = app.sessionStore.businessesOwnedAlone().mapNotNull { id ->
                    app.db.orgDao().findOrg(id)?.name
                },
                advanceOwedMinor = app.ledgerRepository.observeAdvanceOutstanding(s.orgId, s.personId).first(),
                pendingUploads = pending,
                lastBackupAt = sync.lastSuccessAt,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    fun setBusinessMode(enabled: Boolean) = app.sessionStore.setBusinessMode(enabled)

    fun setLandUnit(unit: MeasureUnit) = viewModelScope.launch {
        runCatching { app.sessionStore.setLandUnit(unit) }
    }

    /** What is being typed for the own unit's name, ahead of the saved one. */
    val ownLandTyping = MutableStateFlow<String?>(null)
    private var ownLandSave: kotlinx.coroutines.Job? = null

    /**
     * Names the business's own land unit. Saved once the typing pauses, not on every letter: each
     * save goes to every phone in the business.
     */
    fun setOwnLandUnit(name: String) {
        ownLandTyping.value = name
        ownLandSave?.cancel()
        ownLandSave = viewModelScope.launch {
            kotlinx.coroutines.delay(OWN_LAND_SAVE_DELAY_MS)
            runCatching { app.sessionStore.setLandUnit(MeasureUnit.OwnLand, name) }
            ownLandSave = null
        }
    }

    /** A name typed just before leaving the screen is still saved. */
    override fun onCleared() {
        val pending = ownLandTyping.value
        if (ownLandSave != null && pending != null) {
            app.runInBackground { runCatching { app.sessionStore.setLandUnit(MeasureUnit.OwnLand, pending) } }
        }
    }

    /** Set when switching proof of work on needs the paid plan. */
    val needsPlan = MutableStateFlow(false)

    fun setProofOfWork(on: Boolean) = viewModelScope.launch {
        try {
            app.sessionStore.setProofOfWork(on)
        } catch (limit: com.example.fieldtrack.data.plan.PlanLimit) {
            needsPlan.value = true
        } catch (other: Exception) {
        }
    }
}

/** Everything the settings screen can do, grouped so it travels as one argument. */
class SettingsActions(
    val onLanguage: (String?) -> Unit,
    val onTheme: (ThemeChoice) -> Unit,
    val onBiggerText: (Boolean) -> Unit,
    val onLock: (Boolean) -> Unit,
    val onLockAfter: (LockAfter) -> Unit,
    val onNotifications: () -> Unit,
    val onNumbers: () -> Unit,
    val onLandUnit: (MeasureUnit) -> Unit,
    val onOwnLandUnit: (String) -> Unit = {},
    val onMoney: () -> Unit,
    val onSeasons: () -> Unit,
    val onPlan: () -> Unit,
    val onBookingLink: () -> Unit,
    val onPricing: () -> Unit,
    val onProofOfWork: (Boolean) -> Unit,
    val onBusinessMode: (Boolean) -> Unit,
    val onBackup: () -> Unit,
    val onExport: () -> Unit,
    val onSignOut: () -> Unit,
    /** Null for the only owner, who cannot leave their own business. */
    val onLeave: (() -> Unit)?,
    /** Null where there is no way to ask the server. */
    val onDeleteAccount: (() -> Unit)?,
)


/**
 * Every preference in one place, and nothing else.
 *
 * Grouped by whose choice it is: this phone (language, colours, text size, lock,
 * notifications), the business (owner only: units, money, seasons, the plan, proof of
 * work), your records, your account. The simple ones change right here with a switch
 * or a pick; only the ones that need a screen of their own lead anywhere, and only
 * those carry an arrow.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    prefs: PhonePrefs,
    actions: SettingsActions,
    onBack: () -> Unit,
    unsentCount: Int,
    modifier: Modifier = Modifier,
    /** Null when this build cannot offer Google, so there is nothing to show. */
    google: GoogleLink? = null,
    /** Set when the last try could not reach the server, so the person is told rather than left wondering. */
    deleteFailed: Boolean = false,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.more_settings),
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
            // ---- This phone
            FtSectionLabel(stringResource(R.string.settings_phone))
            FtCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (AppLanguage.SUPPORTED.size > 1) {
                        // "" stands for following the phone, so the box always names a choice.
                        FtDropdown(
                            options = listOf("") + AppLanguage.SUPPORTED,
                            selected = prefs.language.orEmpty(),
                            text = { tag -> if (tag.isEmpty()) stringResource(R.string.language_auto) else AppLanguage.nativeName(tag) },
                            onPick = { actions.onLanguage(it.ifEmpty { null }) },
                            label = stringResource(R.string.more_language),
                            searchHint = stringResource(R.string.pick_search),
                        )
                    }
                    FtPick(
                        options = listOf(ThemeChoice.System, ThemeChoice.Light, ThemeChoice.Dark),
                        selected = prefs.theme,
                        text = {
                            stringResource(
                                when (it) {
                                    ThemeChoice.System -> R.string.settings_theme_phone
                                    ThemeChoice.Light -> R.string.display_theme_light
                                    ThemeChoice.Dark -> R.string.display_theme_dark
                                }
                            )
                        },
                        onPick = actions.onTheme,
                        label = stringResource(R.string.display_theme),
                    )
                }
            }
            FtCard(padding = 0.dp) {
                FtSwitchRow(
                    title = stringResource(R.string.display_bigger_text),
                    note = stringResource(R.string.display_bigger_text_note),
                    checked = prefs.biggerText,
                    onChange = actions.onBiggerText,
                )
                FtRowDivider()
                FtSwitchRow(
                    title = stringResource(R.string.security_lock),
                    note = stringResource(
                        if (prefs.lockAvailable) R.string.security_lock_note else R.string.security_no_lock
                    ),
                    checked = prefs.lockOn && prefs.lockAvailable,
                    enabled = prefs.lockAvailable,
                    onChange = actions.onLock,
                )
                if (prefs.lockOn && prefs.lockAvailable) {
                    Box(Modifier.padding(start = 15.dp, end = 15.dp, bottom = 14.dp)) {
                        FtPick(
                            options = LockAfter.entries.toList(),
                            selected = prefs.lockAfter,
                            text = {
                                stringResource(
                                    when (it) {
                                        LockAfter.Right -> R.string.security_after_right
                                        LockAfter.OneMinute -> R.string.security_after_minute
                                        LockAfter.FiveMinutes -> R.string.security_after_five
                                    }
                                )
                            },
                            onPick = actions.onLockAfter,
                            label = stringResource(R.string.security_after),
                        )
                    }
                }
            }
            LinkCard(
                listOf(
                    Link(
                        stringResource(R.string.remind_title), stringResource(R.string.remind_row_note),
                        FieldTrackIcons.Bell, actions.onNotifications,
                    ),
                    Link(
                        stringResource(R.string.settings_numbers), stringResource(R.string.settings_numbers_note),
                        FieldTrackIcons.Display, actions.onNumbers,
                    ),
                )
            )

            // ---- The business: only for somebody who may change it.
            if (state.canEditBusiness) {
                FtSectionLabel(stringResource(R.string.profile_business), Modifier.padding(top = 8.dp))
                FtCard {
                    com.example.fieldtrack.feature.region.LandUnitChoice(
                        selected = state.landUnit,
                        ownName = state.ownLandUnit,
                        onUnit = actions.onLandUnit,
                        onOwnName = actions.onOwnLandUnit,
                        chips = false,
                        label = stringResource(R.string.profile_land_measured_in),
                    )
                    Text(
                        text = stringResource(R.string.edit_unit_note),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                LinkCard(
                    listOf(
                        Link(
                            stringResource(R.string.profile_money),
                            stringResource(
                                R.string.profile_money_summary,
                                com.example.fieldtrack.data.region.Region.nameOf(state.currencyCode),
                                com.example.fieldtrack.designsystem.format.MoneyStyle.symbolOf(state.currencyCode),
                            ),
                            FieldTrackIcons.money(state.currencyCode), actions.onMoney,
                        ),
                        Link(
                            stringResource(R.string.profile_seasons), seasonStartsSummary(state.seasonStarts),
                            FieldTrackIcons.Calendar, actions.onSeasons,
                        ),
                        Link(
                            stringResource(R.string.profile_plan),
                            stringResource(
                                if (state.plan.driversSeeAll) R.string.profile_plan_summary_all else R.string.profile_plan_summary_own,
                                timeOfDay(state.plan.dayStartHour * 60),
                            ),
                            FieldTrackIcons.Clock, actions.onPlan,
                        ),
                        Link(
                            stringResource(R.string.link_title),
                            stringResource(if (state.bookingLinkOn) R.string.settings_link_on else R.string.link_row_note),
                            FieldTrackIcons.WhatsApp, actions.onBookingLink,
                        ),
                        Link(
                            stringResource(R.string.pricing_title), stringResource(R.string.plan_row_note),
                            FieldTrackIcons.Bill, actions.onPricing,
                        ),
                    )
                )
                FtCard(padding = 0.dp) {
                    FtSwitchRow(
                        title = stringResource(R.string.proof_setting),
                        note = stringResource(R.string.proof_setting_note),
                        checked = state.proofOfWork,
                        onChange = actions.onProofOfWork,
                    )
                    FtRowDivider()
                    FtSwitchRow(
                        title = stringResource(R.string.profile_business_mode),
                        note = stringResource(R.string.profile_business_mode_note),
                        checked = state.businessMode,
                        onChange = actions.onBusinessMode,
                    )
                }
            }

            // ---- Records
            FtSectionLabel(stringResource(R.string.settings_records), Modifier.padding(top = 8.dp))
            LinkCard(
                listOf(
                    Link(
                        stringResource(R.string.more_backup),
                        // What is actually true right now, rather than a fixed label: the one
                        // place most people look to learn whether their records exist anywhere
                        // but this phone.
                        when {
                            state.pendingUploads > 0 -> stringResource(
                                R.string.more_backup_waiting,
                                pluralStringResource(R.plurals.sync_changes_waiting, state.pendingUploads, state.pendingUploads),
                            )
                            // Nothing queued is not the same as safely on a server.
                            state.lastBackupAt == null -> stringResource(R.string.sync_never)
                            else -> stringResource(R.string.sync_all_safe)
                        },
                        if (state.pendingUploads == 0 && state.lastBackupAt != null) FieldTrackIcons.Check else FieldTrackIcons.Offline,
                        actions.onBackup,
                    ),
                    Link(
                        stringResource(R.string.export_title), stringResource(R.string.export_row_note),
                        FieldTrackIcons.Export, actions.onExport,
                    ),
                )
            )

            // ---- Account
            FtSectionLabel(stringResource(R.string.account_section), Modifier.padding(top = 8.dp))
            FtCard {
                Text(
                    text = stringResource(
                        R.string.account_signed_in_as,
                        state.signedInPhone?.let { formatPhone(it) }
                            ?: state.signedInEmail
                            ?: state.displayName,
                    ),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
                if (google != null) {
                    // A second way back in, for a lost phone or a number that changed.
                    Text(
                        text = if (google.email != null) {
                            stringResource(R.string.account_google_linked, google.email)
                        } else stringResource(R.string.account_google_offer),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                    if (google.email == null) {
                        FtButton(
                            text = stringResource(R.string.account_google_add),
                            onClick = google.onAdd,
                            tone = FtButtonTone.Secondary,
                            height = 46.dp,
                            modifier = Modifier.padding(top = 9.dp),
                        )
                    }
                    google.note?.let {
                        Text(
                            text = it,
                            style = FieldTrackTheme.type.caption,
                            color = colors.dueInk,
                            modifier = Modifier.padding(top = 7.dp),
                        )
                    }
                }
                // Said before they decide, not after. Somebody signing out of a
                // shared phone should know what is still sitting on it.
                if (unsentCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.account_sign_out_unsent, unsentCount, unsentCount,
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }

            actions.onLeave?.let { leave ->
                var leaving by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                FtButton(
                    text = stringResource(R.string.account_leave),
                    onClick = { leaving = true },
                    tone = FtButtonTone.Secondary,
                    height = 50.dp,
                )
                if (leaving) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { leaving = false },
                        title = { Text(stringResource(R.string.account_leave_confirm, state.businessName)) },
                        text = {
                            Text(
                                buildString {
                                    append(stringResource(R.string.account_leave_body))
                                    // Said to the person leaving, so it is not a surprise at the next payday.
                                    if (state.advanceOwedMinor > 0) {
                                        append("\n\n")
                                        append(stringResource(R.string.account_leave_advance, money(state.advanceOwedMinor)))
                                    }
                                }
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { leaving = false; leave() }) {
                                Text(stringResource(R.string.account_leave_yes), color = colors.dueInk)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { leaving = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
                }
            }

            FtButton(
                text = stringResource(R.string.account_sign_out),
                onClick = actions.onSignOut,
                tone = FtButtonTone.Secondary,
                height = 50.dp,
            )

            actions.onDeleteAccount?.let { delete ->
                var confirming by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                FtButton(
                    text = stringResource(R.string.account_delete),
                    onClick = { confirming = true },
                    tone = FtButtonTone.Danger,
                    height = 46.dp,
                )
                if (deleteFailed) {
                    Text(
                        text = stringResource(R.string.account_delete_failed),
                        style = FieldTrackTheme.type.caption,
                        color = colors.dueInk,
                    )
                }
                if (confirming) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirming = false },
                        title = { Text(stringResource(R.string.account_delete_title)) },
                        text = {
                            Text(
                                buildString {
                                    append(stringResource(R.string.account_delete_body))
                                    if (state.soleOwnerOf.isNotEmpty()) {
                                        append("\n\n")
                                        append(
                                            stringResource(
                                                R.string.account_delete_businesses,
                                                state.soleOwnerOf.joinToString(", "),
                                            )
                                        )
                                    }
                                }
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { confirming = false; delete() }) {
                                Text(stringResource(R.string.account_delete_yes), color = colors.dueInk)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { confirming = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
                }
            }

            Box(Modifier.padding(bottom = dimens.space4))
        }
    }
}

/** A setting that needs a screen of its own: the only kind that carries an arrow. */
private data class Link(
    val title: String,
    val note: String?,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun LinkCard(links: List<Link>) {
    FtCard(padding = 0.dp) {
        links.forEachIndexed { i, link ->
            if (i > 0) FtRowDivider()
            FtListRow(
                title = link.title,
                subtitle = link.note,
                subtitleMaxLines = 2,
                leading = { FtIconTile(link.icon, size = 40.dp) },
                showChevron = true,
                onClick = link.onClick,
            )
        }
    }
}

/** Long enough to finish a word, short enough that leaving the screen rarely beats it. */
private const val OWN_LAND_SAVE_DELAY_MS = 800L
