package com.example.fieldtrack.feature.region

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.example.fieldtrack.R
import com.example.fieldtrack.data.region.Region

/**
 * Choosing the currency a business keeps its books in.
 *
 * The one the phone guessed is first, then the ones most farmers look for, then every
 * other currency the phone knows by name. Searchable by name or by code, because
 * nobody should scroll a hundred and fifty currencies to find their own.
 */
@Composable
fun CurrencyPickerSheet(
    current: String,
    guessed: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(guessed) {
        Region.currencies(guessed).map {
            PickOption(it.code, it.name, "${it.symbol} · ${it.code}", "${it.name} ${it.code}")
        }
    }
    SearchPickerSheet(
        title = stringResource(R.string.currency_pick_title),
        searchHint = stringResource(R.string.currency_search_hint),
        options = options,
        currentKey = current,
        onPick = onPick,
        onDismiss = onDismiss,
    )
}

/** Choosing the time zone a business counts its days in; the phone's own is the first line. */
@Composable
fun TimeZonePickerSheet(
    currentId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val phoneKey = ""
    val phoneTitle = stringResource(R.string.zone_phone)
    val options = remember(phoneTitle) {
        val phone = java.time.ZoneId.systemDefault()
        listOf(
            PickOption(phoneKey, phoneTitle, "${phone.id} · ${Region.offsetLabel(phone)}", phoneTitle),
        ) + Region.timeZones().map {
            PickOption(it.id, it.city, "${it.id} · ${it.offset}", "${it.city} ${it.id}")
        }
    }
    SearchPickerSheet(
        title = stringResource(R.string.zone_pick_title),
        searchHint = stringResource(R.string.zone_search_hint),
        options = options,
        currentKey = currentId ?: phoneKey,
        onPick = { onPick(it.ifEmpty { null }) },
        onDismiss = onDismiss,
    )
}
