package com.example.fieldtrack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** Above this many choices a list gets a search box; below it, scrolling is quicker than typing. */
private const val SEARCH_FROM = 8

/**
 * One choice out of many, taking one line of the screen until it is opened.
 *
 * The long lists of the app (machine types, units, work types, payment methods) used to be
 * laid out in full, so a form could be three screens tall before its first text box. This
 * shows the current choice on a single line and opens the rest in a sheet that scrolls.
 * [icon] puts the machine's shape next to its name, which is how drivers recognise it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FtDropdown(
    options: List<T>,
    selected: T?,
    text: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    searchHint: String = "",
    sheetTitle: String? = label,
    icon: ((T) -> ImageVector?)? = null,
    /** A small line under an option, for the one that needs a word of explanation. */
    note: (@Composable (T) -> String?)? = null,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val shape = FieldTrackTheme.shapes.medium
    var open by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        if (label != null) FtSectionLabel(label, Modifier.padding(bottom = 9.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.fieldHeight)
                .clip(shape)
                .background(colors.surface)
                .border(width = dimens.border, color = colors.line, shape = shape)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val glyph = selected?.let { icon?.invoke(it) }
            if (glyph != null) {
                Icon(glyph, contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
            }
            Text(
                text = selected?.let { text(it) } ?: placeholder,
                style = FieldTrackTheme.type.body,
                color = if (selected != null) colors.ink else colors.inkTertiary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                FieldTrackIcons.Forward,
                contentDescription = null,
                tint = colors.inkTertiary,
                modifier = Modifier.size(18.dp).rotate(90f),
            )
        }
    }

    if (open) {
        ChoiceSheet(
            title = sheetTitle,
            searchHint = searchHint,
            options = options,
            selected = selected,
            text = text,
            icon = icon,
            note = note,
            onPick = {
                open = false
                onPick(it)
            },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    title: String?,
    searchHint: String,
    options: List<T>,
    selected: T?,
    text: @Composable (T) -> String,
    icon: ((T) -> ImageVector?)?,
    note: (@Composable (T) -> String?)?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val labelled = options.map { it to text(it) }
    val shown = remember(query, labelled) {
        val q = query.trim()
        if (q.isEmpty()) labelled else labelled.filter { it.second.contains(q, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = options.size > SEARCH_FROM),
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
            if (title != null) {
                Text(text = title, style = FieldTrackTheme.type.sectionTitle, color = colors.ink)
            }
            if (options.size > SEARCH_FROM) {
                FtTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = FieldTrackIcons.Search,
                    placeholder = searchHint,
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(shown) { (option, name) ->
                    val isCurrent = option == selected
                    val glyph = icon?.invoke(option)
                    FtListRow(
                        title = name,
                        subtitle = note?.invoke(option),
                        leading = glyph?.let {
                            {
                                Icon(
                                    it,
                                    contentDescription = null,
                                    tint = if (isCurrent) colors.primary else colors.inkSecondary,
                                    modifier = Modifier.padding(end = 14.dp).size(28.dp),
                                )
                            }
                        },
                        trailing = if (isCurrent) {
                            {
                                Icon(
                                    FieldTrackIcons.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        } else null,
                        onClick = { onPick(option) },
                        titleColor = if (isCurrent) colors.primary else colors.ink,
                    )
                    FtRowDivider()
                }
            }
        }
    }
}

/** At most this many choices stay on the screen as pills; more than this opens as a list. */
private const val PILLS_UP_TO = 5

/**
 * The one way the app asks "which of these?": a few choices sit in view as pills, many
 * collapse into a [FtDropdown]. Callers say what the choices are and never decide how
 * they look, so two screens asking the same question cannot answer it differently.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FtPick(
    options: List<T>,
    selected: T?,
    text: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    searchHint: String = "",
    icon: ((T) -> ImageVector?)? = null,
) {
    if (options.size > PILLS_UP_TO) {
        FtDropdown(
            options = options,
            selected = selected,
            text = text,
            onPick = onPick,
            modifier = modifier,
            label = label,
            searchHint = searchHint,
            icon = icon,
        )
        return
    }
    Column(modifier) {
        if (label != null) FtSectionLabel(label, Modifier.padding(bottom = 9.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FtChip(
                    label = text(option),
                    selected = option == selected,
                    onClick = { onPick(option) },
                )
            }
        }
    }
}
