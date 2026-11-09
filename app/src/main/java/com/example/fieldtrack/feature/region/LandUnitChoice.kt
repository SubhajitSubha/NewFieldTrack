package com.example.fieldtrack.feature.region

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtDropdown
import com.example.fieldtrack.designsystem.component.FtPick
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.feature.unitName

/** The land units most businesses measure in, offered as chips where there is room for only a few. */
private val COMMON = listOf(MeasureUnit.Acre, MeasureUnit.Hectare, MeasureUnit.Bigha, MeasureUnit.Kanal, MeasureUnit.Guntha)

/**
 * What the business measures land in, asked the same way wherever it is asked.
 *
 * Every land unit is offered; the commonest as chips when [chips], the rest one tap away. The last
 * of them is the business's own unit, which is named here ("Local unit" until it is); how many of it
 * make an acre is asked the first time a field is measured in it, as for a bigha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandUnitChoice(
    selected: MeasureUnit,
    ownName: String,
    onUnit: (MeasureUnit) -> Unit,
    onOwnName: (String) -> Unit,
    chips: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    @Composable
    fun name(unit: MeasureUnit): String =
        if (unit == MeasureUnit.OwnLand) ownName.trim().ifBlank { unitName(unit) } else unitName(unit)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (chips) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The one chosen from the longer list stays in sight among the chips.
                (COMMON + listOfNotNull(selected.takeIf { it !in COMMON })).forEach { unit ->
                    FtChip(label = name(unit), selected = selected == unit, onClick = { onUnit(unit) })
                }
            }
            FtDropdown(
                options = MeasureUnit.LAND.filter { it !in COMMON },
                selected = null,
                text = { name(it) },
                onPick = onUnit,
                placeholder = stringResource(R.string.land_more),
                searchHint = stringResource(R.string.pick_search),
                sheetTitle = label,
            )
        } else {
            FtPick(
                options = MeasureUnit.LAND,
                selected = selected,
                text = { name(it) },
                onPick = onUnit,
                label = label,
                searchHint = stringResource(R.string.pick_search),
            )
        }
        if (selected == MeasureUnit.OwnLand) {
            FtTextField(
                label = stringResource(R.string.land_own_name),
                value = ownName,
                onValueChange = { onOwnName(it.take(MAX_NAME)) },
                placeholder = stringResource(R.string.unit_own),
                helper = stringResource(R.string.land_own_note),
            )
        }
    }
}

/** As the business keeps it. */
private const val MAX_NAME = 24
