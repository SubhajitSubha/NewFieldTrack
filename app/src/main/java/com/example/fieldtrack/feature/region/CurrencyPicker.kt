package com.example.fieldtrack.feature.region

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.region.Region
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Choosing the currency a business keeps its books in.
 *
 * The one the phone guessed is first, then the ones most farmers look for, then every
 * other currency the phone knows by name. Searchable by name or by code, because
 * nobody should scroll a hundred and fifty currencies to find their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    current: String,
    guessed: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(guessed) { Region.currencies(guessed) }
    val shown = remember(query, all) {
        val q = query.trim()
        if (q.isEmpty()) all
        else all.filter { it.name.contains(q, ignoreCase = true) || it.code.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(R.string.currency_pick_title),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            FtTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = FieldTrackIcons.Search,
                placeholder = stringResource(R.string.currency_search_hint),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(shown, key = { it.code }) { option ->
                    FtListRow(
                        title = option.name,
                        subtitle = "${option.symbol} · ${option.code}",
                        showChevron = option.code == current,
                        onClick = { onPick(option.code) },
                        titleColor = if (option.code == current) colors.primary else colors.ink,
                    )
                    FtRowDivider()
                }
            }
        }
    }
}
