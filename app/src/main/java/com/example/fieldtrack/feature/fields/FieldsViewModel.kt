package com.example.fieldtrack.feature.fields

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One plot on the list. */
data class FieldRow(
    val id: String,
    val name: String,
    val area: Double,
    val unit: MeasureUnit,
    val crop: String?,
    val jobCount: Int,
    val lastWorkedAt: Long?,
    val hasPin: Boolean,
)

data class FieldsState(
    val loaded: Boolean = false,
    val customerName: String = "",
    val village: String? = null,
    val rows: List<FieldRow> = emptyList(),
    val canEdit: Boolean = false,
) {
    /**
     * The customer's land in one figure, or null when it cannot honestly be one.
     *
     * Plots measured in different units do not add up. Converting between bigha
     * and acre would mean picking a conversion, and a bigha is not the same size
     * in two neighbouring districts — so where the units disagree the screen
     * says nothing rather than inventing a total.
     */
    val totalArea: Double?
        get() = rows.map { it.unit }.distinct().singleOrNull()?.let { rows.sumOf { r -> r.area } }

    val totalUnit: MeasureUnit?
        get() = rows.map { it.unit }.distinct().singleOrNull()
}

/**
 * A customer's plots.
 *
 * Fields exist so that "we did the canal-side plot" is a thing the app can
 * record, and so a driver sent out alone can be told which plot. The figures
 * beside each one come from the jobs that named it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FieldsViewModel(
    private val app: FieldFlowApplication,
    private val customerId: String,
) : ViewModel() {

    val state: StateFlow<FieldsState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.directoryRepository.observeFields(s.orgId, customerId),
            app.directoryRepository.observeFieldWork(s.orgId, customerId),
            app.directoryRepository.observeCustomers(s.orgId)
                .map { all -> all.firstOrNull { it.id == customerId } },
        ) { fields, work, customer ->
            val byField = work.associateBy { it.fieldId }
            FieldsState(
                loaded = true,
                customerName = customer?.name.orEmpty(),
                village = customer?.village,
                rows = fields.map { field ->
                    val totals = byField[field.id]
                    FieldRow(
                        id = field.id,
                        name = field.name,
                        area = field.area,
                        unit = field.unit,
                        crop = field.crop,
                        jobCount = totals?.jobCount ?: 0,
                        lastWorkedAt = totals?.lastWorkedAt,
                        hasPin = field.latitude != null && field.longitude != null,
                    )
                },
                canEdit = Capability.EditCustomers in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FieldsState())
}
