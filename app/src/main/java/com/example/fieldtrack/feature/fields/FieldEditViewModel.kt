package com.example.fieldtrack.feature.fields

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MeasureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the pin control is doing right now. */
enum class PinState { None, Getting, Dropped, Refused, Nowhere }

data class FieldEditState(
    val loaded: Boolean = false,
    /** Null when adding; set when correcting one that exists. */
    val fieldId: String? = null,
    val name: String = "",
    val areaText: String = "",
    val unit: MeasureUnit = MeasureUnit.Acre,
    val crop: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pin: PinState = PinState.None,
    val saving: Boolean = false,
) {
    /**
     * The size as a number, or null when what was typed is not one.
     *
     * Null rather than nought, because nought would make the save button look
     * available and then be refused by the repository with nothing on screen
     * explaining it.
     */
    val area: Double? get() = areaText.trim().toDoubleOrNull()?.takeIf { it > 0 }

    val canSave: Boolean get() = !saving && name.isNotBlank() && area != null

    val hasPin: Boolean get() = latitude != null && longitude != null
}

/**
 * Writing down a plot, or correcting one.
 *
 * The same screen both ways: the fields are identical, and a separate "edit"
 * screen would be the same form twice with two sets of bugs.
 */
class FieldEditViewModel(
    private val app: FieldFlowApplication,
    private val customerId: String,
    private val fieldId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(FieldEditState())
    val state: StateFlow<FieldEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val existing = fieldId?.let {
                app.directoryRepository.observeField(s.orgId, it).first()
            }
            _state.update {
                it.copy(
                    loaded = true,
                    fieldId = existing?.id,
                    name = existing?.name.orEmpty(),
                    // Trailing ".0" on a whole number of acres is noise in a
                    // box somebody is about to type into.
                    areaText = existing?.area?.let { a ->
                        if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()
                    }.orEmpty(),
                    unit = existing?.unit ?: s.landUnit,
                    crop = existing?.crop.orEmpty(),
                    latitude = existing?.latitude,
                    longitude = existing?.longitude,
                    pin = if (existing?.latitude != null) PinState.Dropped else PinState.None,
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setArea(value: String) = _state.update {
        // One decimal point, digits either side. Half an acre is ordinary; two
        // decimal points is a typo that would otherwise reach toDoubleOrNull.
        val cleaned = value.filter { c -> c.isDigit() || c == '.' }
        val dot = cleaned.indexOf('.')
        it.copy(
            areaText = if (dot < 0) cleaned
            else cleaned.substring(0, dot + 1) + cleaned.substring(dot + 1).replace(".", "")
        )
    }

    fun setUnit(value: MeasureUnit) = _state.update { it.copy(unit = value) }

    fun setCrop(value: String) = _state.update { it.copy(crop = value) }

    /**
     * Records where this phone is, now.
     *
     * [PinState] carries the reason it failed rather than silently doing
     * nothing. Somebody standing in a field who taps and sees no change has no
     * way to tell a refused permission from a missing satellite.
     */
    fun dropPin(context: Context) = viewModelScope.launch {
        _state.update { it.copy(pin = PinState.Getting) }
        when (val result = currentPin(context)) {
            is PinResult.Found -> _state.update {
                it.copy(
                    latitude = result.latitude,
                    longitude = result.longitude,
                    pin = PinState.Dropped,
                )
            }
            PinResult.NotAllowed -> _state.update { it.copy(pin = PinState.Refused) }
            PinResult.Nowhere -> _state.update { it.copy(pin = PinState.Nowhere) }
        }
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        val area = current.area ?: return@launch
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: run {
            _state.update { it.copy(saving = false) }
            return@launch
        }
        runCatching {
            val existingId = current.fieldId
            if (existingId == null) {
                app.directoryRepository.addField(
                    orgId = s.orgId,
                    can = s.can,
                    customerId = customerId,
                    name = current.name,
                    area = area,
                    unit = current.unit,
                    crop = current.crop,
                    latitude = current.latitude,
                    longitude = current.longitude,
                )
            } else {
                app.directoryRepository.updateField(
                    orgId = s.orgId,
                    can = s.can,
                    fieldId = existingId,
                    name = current.name,
                    area = area,
                    unit = current.unit,
                    crop = current.crop,
                    latitude = current.latitude,
                    longitude = current.longitude,
                )
            }
        }.onFailure {
            _state.update { st -> st.copy(saving = false) }
            return@launch
        }
        _state.update { it.copy(saving = false) }
        onSaved()
    }

    fun remove(onRemoved: () -> Unit) = viewModelScope.launch {
        val existingId = _state.value.fieldId ?: return@launch
        val s = app.sessionStore.load() ?: return@launch
        runCatching {
            app.directoryRepository.removeField(s.orgId, s.can, existingId)
        }.onSuccess { onRemoved() }
    }
}
