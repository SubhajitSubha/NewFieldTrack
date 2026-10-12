package com.example.fieldtrack.feature.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Correcting a customer.
 *
 * Shares [AddCustomerState] with the add flow so the form is one screen rather than
 * two that drift apart. The opening-balance field is absent when editing: an opening
 * balance is a dated khata line, not a property of the customer, and letting someone
 * retype it here would silently double-count what they are owed.
 */
class EditCustomerViewModel(
    private val app: FieldFlowApplication,
    private val customerId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AddCustomerState())
    val state: StateFlow<AddCustomerState> = _state.asStateFlow()

    private val _allowed = MutableStateFlow(true)
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            _allowed.value = Capability.EditCustomers in s.can
            val customer = app.directoryRepository.findCustomer(s.orgId, customerId)
                ?: return@launch
            _state.update {
                it.copy(
                    name = customer.name,
                    phone = customer.phone.orEmpty(),
                    village = customer.village.orEmpty(),
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() }) }
    fun setVillage(v: String) = _state.update { it.copy(village = v) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: run {
            _state.update { it.copy(saving = false) }
            return@launch
        }
        runCatching {
            app.directoryRepository.updateCustomer(
                orgId = s.orgId,
                can = s.can,
                customerId = customerId,
                name = current.name,
                phone = current.phone,
                village = current.village,
            )
        }.onFailure { _allowed.value = false }
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}
