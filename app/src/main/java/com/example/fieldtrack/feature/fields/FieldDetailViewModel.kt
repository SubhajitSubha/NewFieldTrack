package com.example.fieldtrack.feature.fields

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One job done on this plot. */
data class FieldJobRow(
    val id: String,
    val workType: String,
    val workedAtMillis: Long,
    val quantity: Double,
    val unit: MeasureUnit,
    val amountRupees: Long,
)

data class FieldDetailState(
    val loaded: Boolean = false,
    val exists: Boolean = false,
    val name: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val village: String? = null,
    val area: Double = 0.0,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val crop: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val earnedRupees: Long = 0,
    val jobs: List<FieldJobRow> = emptyList(),
    val canSeeMoney: Boolean = false,
    val canEdit: Boolean = false,
) {
    val hasPin: Boolean get() = latitude != null && longitude != null
}

/**
 * One plot: how big, what is on it, and what it has brought in.
 *
 * The earnings figure is the reason a contractor opens this. It only means
 * anything if jobs have named the field, which is why starting work from here
 * carries the field through rather than leaving it to be chosen again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FieldDetailViewModel(
    private val app: FieldFlowApplication,
    private val fieldId: String,
) : ViewModel() {

    val state: StateFlow<FieldDetailState> = app.sessionStore.session.flatMapLatest { s ->
        app.directoryRepository.observeField(s.orgId, fieldId).flatMapLatest { field ->
            if (field == null) {
                return@flatMapLatest kotlinx.coroutines.flow.flowOf(
                    FieldDetailState(loaded = true, exists = false)
                )
            }
            combine(
                app.workRepository.observeForCustomer(s.orgId, field.customerId),
                app.directoryRepository.observeCustomers(s.orgId)
                    .map { all -> all.firstOrNull { it.id == field.customerId } },
            ) { jobs, customer ->
                val here = jobs.filter { it.fieldId == fieldId }
                val billed = here.filter { it.status == JobStatus.Completed }
                FieldDetailState(
                    loaded = true,
                    exists = true,
                    name = field.name,
                    customerId = field.customerId,
                    customerName = customer?.name.orEmpty(),
                    village = customer?.village,
                    area = field.area,
                    unit = field.unit,
                    crop = field.crop,
                    latitude = field.latitude,
                    longitude = field.longitude,
                    // Completed only. A job still running has earned nothing
                    // yet, and counting it would show a field as having paid
                    // for work that is still happening.
                    earnedRupees = billed.sumOf { it.amountRupees },
                    jobs = here.sortedByDescending { it.workedAt }.map { job ->
                        FieldJobRow(
                            id = job.id,
                            workType = job.workType,
                            workedAtMillis = job.workedAt,
                            quantity = job.quantity,
                            unit = job.unit,
                            amountRupees = job.amountRupees,
                        )
                    },
                    canSeeMoney = s.canSeeMoney,
                    canEdit = Capability.EditCustomers in s.can,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FieldDetailState())
}
