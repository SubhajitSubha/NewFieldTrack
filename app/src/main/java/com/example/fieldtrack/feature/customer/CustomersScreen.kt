package com.example.fieldtrack.feature.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtMoney
import com.example.fieldtrack.designsystem.component.FtMoneySize
import com.example.fieldtrack.designsystem.component.FtMoneyTone
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.initialsOf

@Composable
fun CustomersScreen(
    state: CustomersState,
    /** Null when this is a bottom-bar tab, which has nothing to go back to. */
    onBack: (() -> Unit)?,
    onQuery: (String) -> Unit,
    onToggleOwing: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.customers_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
            action = {
                FtIconButton(
                    icon = FieldTrackIcons.Add,
                    contentDescription = stringResource(R.string.customers_add_cd),
                    onClick = onAdd,
                    tint = colors.onPrimary,
                    background = colors.primary,
                )
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = dimens.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FtEmphasisCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            FtSectionLabel(
                                stringResource(R.string.customers_total_owed),
                                color = colors.onDarkMuted,
                            )
                            FtMoney(
                                state.totalOwedRupees,
                                size = FtMoneySize.Hero,
                                onDark = true,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.count_customers, state.owingCount, state.owingCount
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.onDarkMuted,
                        )
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(Modifier.weight(1f)) {
                        FtTextField(
                            value = state.query,
                            onValueChange = onQuery,
                            leadingIcon = FieldTrackIcons.Search,
                            placeholder = stringResource(R.string.customers_search),
                        )
                    }
                    FtChip(
                        label = stringResource(R.string.customers_filter_owes),
                        selected = state.onlyOwing,
                        onClick = onToggleOwing,
                    )
                }
            }

            if (state.loaded && state.rows.isEmpty()) {
                item {
                    FtCard {
                        Text(
                            text = stringResource(
                                if (state.onlyOwing) R.string.customers_none_owing
                                else R.string.customers_empty_title
                            ),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        if (!state.onlyOwing) {
                            Text(
                                text = stringResource(R.string.customers_empty_body),
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.inkTertiary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            items(state.rows, key = { it.id }) { row ->
                FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
                    FtListRow(
                        title = row.name,
                        subtitle = row.village,
                        leading = {
                            FtAvatar(
                                initials = initialsOf(row.name),
                                background = if (row.owes) colors.dueWash else colors.surfaceMuted,
                                contentColor = if (row.owes) colors.dueInk else colors.inkSecondary,
                            )
                        },
                        trailing = {
                            if (row.owes) {
                                FtMoney(row.balanceRupees, size = FtMoneySize.Row, tone = FtMoneyTone.Due)
                            } else {
                                Text(
                                    text = stringResource(R.string.status_settled),
                                    style = FieldTrackTheme.type.label,
                                    color = colors.primaryPressed,
                                )
                            }
                        },
                        onClick = { onOpen(row.id) },
                    )
                }
            }

            item { Box(Modifier.height(dimens.space4)) }
        }
    }
}
