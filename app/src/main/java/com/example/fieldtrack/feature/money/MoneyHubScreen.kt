package com.example.fieldtrack.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** Which parts of the money this person may see; each part of the page follows one of these. */
data class MoneyHubAccess(
    /** Customer money: bills, payments received, rates. */
    val customerMoney: Boolean = false,
    /** What people are paid, and machine hire. */
    val peopleCost: Boolean = false,
)

/**
 * Reports and prices, behind one line on the money page.
 *
 * The reports for a season or a bank, and the prices the business charges: each used to
 * be its own line in the menu. A part a person may not see is left out, not greyed.
 */
@Composable
fun MoneyReportsScreen(
    access: MoneyHubAccess,
    onBack: () -> Unit,
    onSeason: () -> Unit,
    onIncome: () -> Unit,
    onAssets: () -> Unit,
    onRates: () -> Unit,
    onPrice: () -> Unit,
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
            title = stringResource(R.string.money_reports_row),
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
            val reports = buildList {
                if (access.peopleCost) add(
                    Hub(stringResource(R.string.season_title), stringResource(R.string.more_season_note), FieldTrackIcons.Report, onSeason)
                )
                if (access.peopleCost && access.customerMoney) add(
                    Hub(stringResource(R.string.income_title), stringResource(R.string.income_row_note), FieldTrackIcons.Bill, onIncome)
                )
                if (access.peopleCost) add(
                    Hub(stringResource(R.string.assets_title), stringResource(R.string.assets_row_note), FieldTrackIcons.Tractor, onAssets)
                )
            }
            if (reports.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.money_hub_reports))
                Links(reports)
            }

            if (access.customerMoney) {
                FtSectionLabel(stringResource(R.string.money_hub_prices), Modifier.padding(top = 8.dp))
                Links(
                    listOf(
                        Hub(stringResource(R.string.more_rates), stringResource(R.string.money_hub_rates_note), FieldTrackIcons.Bill, onRates),
                        Hub(stringResource(R.string.more_price), stringResource(R.string.more_price_note), FieldTrackIcons.money(), onPrice),
                    )
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
    }
}

private data class Hub(val title: String, val note: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun Links(items: List<Hub>) {
    if (items.isEmpty()) return
    FtCard(padding = 0.dp) {
        items.forEachIndexed { i, item ->
            if (i > 0) FtRowDivider()
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
