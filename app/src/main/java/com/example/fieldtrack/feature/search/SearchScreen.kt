package com.example.fieldtrack.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.workLabel

/** Search across farmers, fields, machines and jobs, with one box. */
@Composable
fun SearchScreen(
    state: SearchState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onCustomer: (String) -> Unit,
    onField: (customerId: String) -> Unit,
    onMachine: (String) -> Unit,
    onJob: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper).imePadding()) {
        FtTopBar(
            title = stringResource(R.string.search_title),
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
            FtTextField(
                value = state.query,
                onValueChange = onQuery,
                leadingIcon = FieldTrackIcons.Search,
                placeholder = stringResource(R.string.search_hint),
                focused = true,
            )
            val r = state.results
            when {
                !state.searched -> Text(
                    text = stringResource(R.string.search_prompt),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkTertiary,
                )
                r.isEmpty -> Text(
                    text = stringResource(R.string.search_none),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            if (r.customers.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.search_farmers))
                FtCard(padding = 0.dp) {
                    r.customers.forEachIndexed { i, c ->
                        if (i > 0) FtRowDivider()
                        FtListRow(
                            title = c.name,
                            subtitle = listOfNotNull(c.village, c.phone).joinToString(" · ").ifBlank { null },
                            showChevron = true,
                            onClick = { onCustomer(c.id) },
                        )
                    }
                }
            }
            if (r.fields.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.search_fields))
                FtCard(padding = 0.dp) {
                    r.fields.forEachIndexed { i, f ->
                        if (i > 0) FtRowDivider()
                        FtListRow(
                            title = f.name,
                            subtitle = r.customerNames[f.customerId],
                            showChevron = true,
                            onClick = { onField(f.customerId) },
                        )
                    }
                }
            }
            if (r.machines.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.search_machines))
                FtCard(padding = 0.dp) {
                    r.machines.forEachIndexed { i, m ->
                        if (i > 0) FtRowDivider()
                        FtListRow(
                            title = m.name,
                            subtitle = listOfNotNull(m.typeLabel, m.plate).joinToString(" · ").ifBlank { null },
                            showChevron = true,
                            onClick = { onMachine(m.id) },
                        )
                    }
                }
            }
            if (r.jobs.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.search_jobs))
                FtCard(padding = 0.dp) {
                    r.jobs.forEachIndexed { i, j ->
                        if (i > 0) FtRowDivider()
                        FtListRow(
                            title = workLabel(j.workType),
                            subtitle = listOfNotNull(r.customerNames[j.customerId], dayMonthYear(j.workedAt)).joinToString(" · "),
                            showChevron = true,
                            onClick = { onJob(j.id) },
                        )
                    }
                }
            }
            Box(Modifier.height(dimens.space4))
        }
    }
}
