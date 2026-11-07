package com.example.fieldtrack.feature.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.repository.TeamError
import com.example.fieldtrack.data.session.CustomRole
import com.example.fieldtrack.data.session.CustomRoles
import com.example.fieldtrack.data.session.Grants
import com.example.fieldtrack.data.session.RolePreset
import com.example.fieldtrack.data.session.grants
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtPick
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One role the owner made, and how many people hold it now. */
data class RoleRow(val role: CustomRole, val holders: Int)

data class RolesState(val loaded: Boolean = false, val roles: List<RoleRow> = emptyList())

/** The roles an owner has made, with a count of who holds each. */
@OptIn(ExperimentalCoroutinesApi::class)
class RolesViewModel(private val app: FieldFlowApplication) : ViewModel() {
    val state: StateFlow<RolesState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.teamRepository.observeCustomRoles(s.orgId),
            app.teamRepository.observeTeam(s.orgId),
        ) { roles, team ->
            RolesState(
                loaded = true,
                roles = roles.map { role ->
                    RoleRow(role, team.count { it.active && it.role == MemberRole.Operator && it.grants() == role.toGrants() })
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RolesState())
}

@Composable
fun RolesScreen(
    state: RolesState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.roles_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.roles_intro),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )
            if (state.loaded && state.roles.isEmpty()) {
                FtCard {
                    Text(
                        text = stringResource(R.string.roles_none),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            } else {
                FtCard(padding = 0.dp) {
                    state.roles.forEachIndexed { index, row ->
                        if (index > 0) FtRowDivider()
                        FtListRow(
                            title = row.role.name,
                            subtitle = pluralStringResource(R.plurals.roles_holders, row.holders, row.holders),
                            leading = { FtIconTile(FieldTrackIcons.Team, size = 40.dp) },
                            showChevron = true,
                            onClick = { onOpen(row.role.id) },
                        )
                    }
                }
            }
        }
        Box(Modifier.padding(dimens.screenPadding)) {
            FtButton(text = stringResource(R.string.roles_make), onClick = onNew)
        }
    }
}

data class RoleEditState(
    val loaded: Boolean = false,
    val existing: Boolean = false,
    val name: String = "",
    val grants: Grants = Grants(),
    val holders: Int = 0,
    val saving: Boolean = false,
    /** Why the last save was refused, as a string resource. */
    val problem: Int? = null,
    val problemOther: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

/** Making or changing one role. [roleId] is null for a new one. */
class RoleEditViewModel(private val app: FieldFlowApplication, private val roleId: String?) : ViewModel() {
    private val _state = MutableStateFlow(RoleEditState())
    val state: StateFlow<RoleEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val role = roleId?.let { id -> app.teamRepository.observeCustomRoles(s.orgId).first().firstOrNull { it.id == id } }
            val team = app.teamRepository.observeTeam(s.orgId).first()
            _state.update {
                it.copy(
                    loaded = true, existing = role != null, name = role?.name.orEmpty(),
                    grants = role?.toGrants() ?: Grants(),
                    holders = role?.let { r -> team.count { m -> m.active && m.role == MemberRole.Operator && m.grants() == r.toGrants() } } ?: 0,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v.take(CustomRoles.MAX_NAME), problem = null) }
    fun startFrom(preset: RolePreset) = _state.update { it.copy(grants = preset.grants, problem = null) }
    fun change(edit: (Grants) -> Grants) = _state.update { it.copy(grants = edit(it.grants), problem = null) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        val s = app.sessionStore.load() ?: return@launch
        _state.update { it.copy(saving = true, problem = null) }
        val result = runCatching {
            app.teamRepository.saveCustomRole(s.orgId, s.can, s.personId, roleId, current.name, current.grants)
        }
        result.onSuccess { onSaved() }.onFailure { e ->
            _state.update {
                it.copy(
                    problem = when (e) {
                        is TeamError.RoleNameTaken -> R.string.roles_name_taken
                        is TeamError.RoleSameAs -> R.string.roles_same_as
                        is TeamError.TooManyRoles -> R.string.roles_too_many
                        else -> R.string.edit_not_allowed
                    },
                    problemOther = (e as? TeamError.RoleSameAs)?.other,
                )
            }
        }
        _state.update { it.copy(saving = false) }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        val id = roleId ?: return@launch
        val s = app.sessionStore.load() ?: return@launch
        runCatching { app.teamRepository.deleteCustomRole(s.orgId, s.can, id) }.onSuccess { onDeleted() }
    }
}

@Composable
fun RoleEditScreen(
    state: RoleEditState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onStartFrom: (RolePreset) -> Unit,
    onChange: ((Grants) -> Grants) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val g = state.grants

    Column(modifier.fillMaxSize().background(colors.paper).imePadding()) {
        FtTopBar(
            title = stringResource(if (state.existing) R.string.roles_edit else R.string.roles_make),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FtTextField(
                label = stringResource(R.string.roles_name),
                value = state.name,
                onValueChange = onName,
                placeholder = stringResource(R.string.roles_name_hint),
                focused = !state.existing,
            )
            FtPick(
                options = listOf(RolePreset.Driver, RolePreset.SeniorDriver, RolePreset.Manager),
                selected = null,
                text = {
                    stringResource(
                        when (it) {
                            RolePreset.Driver -> R.string.team_role_driver
                            RolePreset.SeniorDriver -> R.string.team_preset_senior
                            else -> R.string.team_preset_manager
                        }
                    )
                },
                onPick = onStartFrom,
                label = stringResource(R.string.roles_start_from),
            )
            FtSectionLabel(stringResource(R.string.team_allowed_to))
            Text(
                text = stringResource(R.string.team_allowed_baseline),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )
            FtCard(padding = 0.dp) {
                PermissionRow(
                    title = stringResource(R.string.team_perm_money), note = stringResource(R.string.team_perm_money_note),
                    checked = g.seeMoney, enabled = !g.closeBills,
                    onChange = { v -> onChange { it.copy(seeMoney = v) } },
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.team_perm_customers), note = stringResource(R.string.team_perm_customers_note),
                    checked = g.addCustomers, onChange = { v -> onChange { it.copy(addCustomers = v) } },
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.team_perm_bills), note = stringResource(R.string.team_perm_bills_note),
                    checked = g.closeBills,
                    // Closing a bill means seeing the amount on it, as on a person's own page.
                    onChange = { v -> onChange { it.copy(closeBills = v, seeMoney = if (v) true else it.seeMoney) } },
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.team_perm_plan), note = stringResource(R.string.team_perm_plan_note),
                    checked = g.planWork, onChange = { v -> onChange { it.copy(planWork = v) } },
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.team_perm_machines), note = stringResource(R.string.team_perm_machines_note),
                    checked = g.editMachines, onChange = { v -> onChange { it.copy(editMachines = v) } },
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.team_perm_team), note = stringResource(R.string.team_perm_team_note),
                    checked = g.editTeam, onChange = { v -> onChange { it.copy(editTeam = v) } },
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.team_perm_payout), note = stringResource(R.string.team_perm_payout_note),
                    checked = g.payOut, onChange = { v -> onChange { it.copy(payOut = v) } },
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.team_perm_people_cost), note = stringResource(R.string.team_perm_people_cost_note),
                    checked = g.seePeopleCost, onChange = { v -> onChange { it.copy(seePeopleCost = v) } },
                )
            }
            if (state.existing && state.holders > 0) {
                Text(
                    text = pluralStringResource(R.plurals.roles_change_note, state.holders, state.holders),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            state.problem?.let { res ->
                Text(
                    text = if (state.problemOther != null) stringResource(res, state.problemOther) else stringResource(res),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.dueInk,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
        Column(Modifier.padding(dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FtButton(text = stringResource(R.string.edit_save), onClick = onSave, enabled = state.canSave)
            if (state.existing) {
                FtButton(
                    text = stringResource(R.string.roles_delete),
                    onClick = { confirmDelete = true },
                    tone = FtButtonTone.Secondary,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.roles_delete_title, state.name)) },
            text = { Text(stringResource(R.string.roles_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.roles_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.person_pay_back_no)) } },
        )
    }
}
