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
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** One line in a searchable list: what it is called, what sits under it, and what is looked for. */
data class PickOption(val key: String, val title: String, val subtitle: String?, val search: String = title)

/**
 * A long list somebody searches rather than scrolls: currencies, time zones.
 *
 * The current choice is marked, and the options arrive already in the order they should
 * be shown, so the one most likely wanted is at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPickerSheet(
    title: String,
    searchHint: String,
    options: List<PickOption>,
    currentKey: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query, options) {
        val q = query.trim()
        if (q.isEmpty()) options else options.filter { it.search.contains(q, ignoreCase = true) }
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
            Text(text = title, style = FieldTrackTheme.type.sectionTitle, color = colors.ink)
            FtTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = FieldTrackIcons.Search,
                placeholder = searchHint,
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(shown, key = { it.key }) { option ->
                    FtListRow(
                        title = option.title,
                        subtitle = option.subtitle,
                        showChevron = option.key == currentKey,
                        onClick = { onPick(option.key) },
                        titleColor = if (option.key == currentKey) colors.primary else colors.ink,
                    )
                    FtRowDivider()
                }
            }
        }
    }
}
