package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.BuildConfig
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.initialsOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MoreState(
    val displayName: String = "",
    val businessName: String = "",
    val role: MemberRole = MemberRole.Owner,
    /** Customer money: what comes in. */
    val canSeeMoney: Boolean = false,
    /** Its own switch: a colleague's wage is not a customer balance. */
    val canSeePeopleCost: Boolean = false,
    /** Whether this person plans work, and so deals with farmers' requests. */
    val canPlan: Boolean = false,
    val canSeeTeam: Boolean = false,
    val memberCount: Int = 0,
    /** Whether farmers can ask for work through the business's own page. */
    val bookingLinkOn: Boolean = false,
    val newRequests: Int = 0,
) {
    /**
     * Somebody on a team who may not see what people are paid still sees their own pay.
     * Whoever sees everybody's finds their own on their team page instead.
     */
    val hasMyPay: Boolean get() = canSeeTeam && role != MemberRole.Owner && !canSeePeopleCost
    /** Somebody who may see any kind of money has a Money place; anybody else has none. */
    val hasMoney: Boolean get() = canSeeMoney || canSeePeopleCost
    /** Requests are a place only where they can arrive, or some are still waiting. */
    val hasRequests: Boolean get() = canPlan && (bookingLinkOn || newRequests > 0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MoreViewModel(app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<MoreState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.teamRepository.observeTeam(s.orgId),
            app.db.orgDao().observeOrg(s.orgId),
            app.planRepository.observeNewRequestCount(s.orgId),
        ) { team, org, newRequests ->
            MoreState(
                displayName = s.displayName,
                businessName = org?.name?.ifBlank { null } ?: s.businessName,
                role = s.role,
                canSeeMoney = s.canSeeMoney,
                canSeePeopleCost = Capability.SeePeopleCost in s.can,
                canPlan = Capability.PlanWork in s.can,
                // A one-person outfit has no team to manage; the row appears when
                // business mode is switched on, which is where drivers come from.
                canSeeTeam = app.sessionStore.isBusiness(),
                memberCount = team.count { it.active },
                bookingLinkOn = org?.bookingLink == true,
                newRequests = newRequests,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }
}

/**
 * The menu behind the picture at the top of the four main screens.
 *
 * Short on purpose. At the top, who you are (opens your profile) and which business you
 * are working in (one tap to change it). Then the few places that are not tabs: your
 * team, your money, farmers' requests. Then settings and help. Anything that is a tab is
 * not repeated here, and anything a person may not use is left out rather than shown
 * greyed: a driver sees a menu of four lines, an owner one of seven, in the same order.
 */
@Composable
fun MoreScreen(
    state: MoreState,
    onProfile: () -> Unit,
    onChangeBusiness: () -> Unit,
    onTeam: () -> Unit,
    onMoney: () -> Unit,
    onRequests: () -> Unit,
    onSettings: () -> Unit,
    onMyPay: () -> Unit = {},
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    /** In the side menu the screen's own title bar is dropped: the menu already has a header. */
    inDrawer: Boolean = false,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        if (!inDrawer) FtTopBar(title = stringResource(R.string.more_title))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding)
                .padding(top = if (inDrawer) 14.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- You
            FtCard(padding = 0.dp) {
                FtListRow(
                    title = state.displayName,
                    subtitle = stringResource(R.string.menu_your_profile),
                    leading = {
                        FtAvatar(
                            initials = initialsOf(state.displayName),
                            size = 46.dp,
                            background = colors.primary,
                            contentColor = colors.onPrimary,
                        )
                    },
                    showChevron = true,
                    onClick = onProfile,
                )
                FtRowDivider()
                // ---- The business you are working in, and the way to another one. Drawn
                // by hand rather than as a list row so a long name can take two lines
                // instead of being cut short by the word beside it.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onChangeBusiness)
                        .padding(horizontal = dimens.cardPadding, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FtAvatar(
                        initials = initialsOf(state.businessName),
                        size = 46.dp,
                        background = colors.surfaceMuted,
                        contentColor = colors.inkSecondary,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                        Text(
                            text = state.businessName,
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                when (state.role) {
                                    MemberRole.Owner -> R.string.more_owner
                                    MemberRole.Viewer -> R.string.more_viewer
                                    MemberRole.Operator -> R.string.more_operator
                                }
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Row(
                        Modifier
                            .clip(FieldTrackTheme.shapes.chip)
                            .border(dimens.border, colors.primary, FieldTrackTheme.shapes.chip)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            FieldTrackIcons.Swap,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.menu_change_business),
                            style = FieldTrackTheme.type.label,
                            color = colors.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            // ---- Places that are not tabs
            val places = buildList {
                if (state.canSeeTeam) add(
                    MenuItem(
                        stringResource(R.string.more_team), FieldTrackIcons.Team, onTeam,
                        pluralStringResource(R.plurals.count_members, state.memberCount, state.memberCount),
                    )
                )
                if (state.hasMyPay) add(
                    MenuItem(
                        stringResource(R.string.my_pay_title), FieldTrackIcons.Banknote, onMyPay,
                        stringResource(R.string.my_pay_note),
                    )
                )
                if (state.hasMoney) add(
                    MenuItem(
                        stringResource(R.string.menu_money), FieldTrackIcons.money(), onMoney,
                        stringResource(R.string.menu_money_note),
                    )
                )
                if (state.hasRequests) add(
                    MenuItem(
                        stringResource(R.string.requests_title), FieldTrackIcons.Calendar, onRequests,
                        if (state.newRequests > 0) pluralStringResource(
                            R.plurals.menu_requests_new, state.newRequests, state.newRequests,
                        ) else null,
                    )
                )
            }
            MenuCard(places)

            MenuCard(
                listOf(
                    MenuItem(
                        stringResource(R.string.more_settings), FieldTrackIcons.Settings, onSettings,
                        stringResource(R.string.menu_settings_note),
                    ),
                    MenuItem(
                        stringResource(R.string.help_title), FieldTrackIcons.Help, onHelp,
                        stringResource(R.string.help_row_note),
                    ),
                )
            )

            Text(
                text = stringResource(R.string.more_version, BuildConfig.VERSION_NAME),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Box(Modifier.padding(bottom = dimens.space4))
        }
    }
}

/** One line of the menu, as plain data so a change to the numbers always redraws it. */
private data class MenuItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val note: String?,
)

@Composable
private fun MenuCard(items: List<MenuItem>) {
    if (items.isEmpty()) return
    FtCard(padding = 0.dp) {
        items.forEachIndexed { index, item ->
            if (index > 0) FtRowDivider()
            FtListRow(
                title = item.title,
                subtitle = item.note,
                subtitleMaxLines = 2,
                leading = { FtIconTile(item.icon, size = 40.dp) },
                showChevron = true,
                onClick = item.onClick,
            )
        }
    }
}
