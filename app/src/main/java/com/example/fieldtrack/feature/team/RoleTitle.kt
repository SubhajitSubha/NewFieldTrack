package com.example.fieldtrack.feature.team

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.CustomRole
import com.example.fieldtrack.data.session.CustomRoles
import com.example.fieldtrack.data.session.Grants
import com.example.fieldtrack.data.session.RolePreset

/**
 * What to call somebody's job, the same wherever it is said: a built-in job by its name, one
 * of the owner's own roles by the name the owner gave it, and switches set by hand as that.
 */
@Composable
fun roleTitle(role: MemberRole, grants: Grants, customRoles: List<CustomRole>): String {
    RolePreset.matching(role, grants)?.let { return stringResource(presetTitle(it)) }
    CustomRoles.matching(customRoles, role, grants)?.let { return it.name }
    return stringResource(R.string.team_preset_by_hand)
}

@StringRes
internal fun presetTitle(preset: RolePreset): Int = when (preset) {
    RolePreset.Driver -> R.string.team_role_driver
    RolePreset.SeniorDriver -> R.string.team_preset_senior
    RolePreset.Manager -> R.string.team_preset_manager
    RolePreset.Owner -> R.string.team_role_owner
    RolePreset.Viewer -> R.string.team_preset_viewer
}

@StringRes
internal fun presetNote(preset: RolePreset): Int = when (preset) {
    RolePreset.Driver -> R.string.team_preset_driver_note
    RolePreset.SeniorDriver -> R.string.team_preset_senior_note
    RolePreset.Manager -> R.string.team_preset_manager_note
    RolePreset.Owner -> R.string.team_preset_owner_note
    RolePreset.Viewer -> R.string.team_preset_viewer_note
}
