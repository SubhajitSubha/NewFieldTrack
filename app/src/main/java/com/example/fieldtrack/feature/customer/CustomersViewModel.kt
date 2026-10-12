package com.example.fieldtrack.feature.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomerRow(
    val id: String,
    val name: String,
    val village: String?,
    val balanceRupees: Long,
) {
    val owes: Boolean get() = balanceRupees > 0
}

data class CustomersState(
    val query: String = "",
    val onlyOwing: Boolean = false,
    val totalOwedRupees: Long = 0,
    val owingCount: Int = 0,
    val rows: List<CustomerRow> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _filters = MutableStateFlow(CustomersState())
    val filters: StateFlow<CustomersState> = _filters.asStateFlow()

    val state: StateFlow<CustomersState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            _filters.map { it.query }.distinctUntilChanged()
                .flatMapLatest { app.directoryRepository.searchCustomers(s.orgId, it) },
            app.ledgerRepository.observeBalances(s.orgId),
            _filters,
        ) { customers, balances, filters ->
            val balanceById = balances.associate { it.customerId to it.balanceRupees }
            val rows = customers
                .map { c ->
                    CustomerRow(
                        id = c.id,
                        name = c.name,
                        village = c.village,
                        balanceRupees = balanceById[c.id] ?: 0L,
                    )
                }
                // Whoever owes the most, first. A contractor opening this screen is
                // almost always deciding who to chase, not browsing an address book.
                .sortedWith(compareByDescending<CustomerRow> { it.balanceRupees }.thenBy { it.name })
                .filter { !filters.onlyOwing || it.owes }

            val owing = balanceById.values.filter { it > 0 }
            CustomersState(
                query = filters.query,
                onlyOwing = filters.onlyOwing,
                totalOwedRupees = owing.sum(),
                owingCount = owing.size,
                rows = rows,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomersState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    fun setQuery(value: String) = _filters.update { it.copy(query = value) }

    fun toggleOnlyOwing() = _filters.update { it.copy(onlyOwing = !it.onlyOwing) }
}
