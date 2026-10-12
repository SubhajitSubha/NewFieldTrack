package com.example.fieldtrack.feature.work

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.initialsOf

/**
 * Choosing who work was for, without leaving the form it belongs to.
 *
 * Filtered on name and village, because a contractor with sixty customers knows
 * "the Kheri one" long before they recall a full name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickCustomerSheet(
    customers: List<CustomerEntity>,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (CustomerEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    val shown = customers.filter {
        query.isBlank() ||
            it.name.contains(query, ignoreCase = true) ||
            it.village.orEmpty().contains(query, ignoreCase = true)
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
                .padding(bottom = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.past_pick_customer_title),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            FtTextField(
                value = query,
                onValueChange = onQuery,
                leadingIcon = FieldTrackIcons.Search,
                placeholder = stringResource(R.string.customers_search),
                focused = true,
            )
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { customer ->
                    FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
                        FtListRow(
                            title = customer.name,
                            subtitle = customer.village,
                            leading = {
                                FtAvatar(
                                    initials = initialsOf(customer.name),
                                    size = 42.dp,
                                    background = colors.primaryWash,
                                    contentColor = colors.primaryPressed,
                                )
                            },
                            onClick = { onPick(customer) },
                        )
                    }
                }
            }
        }
    }
}
