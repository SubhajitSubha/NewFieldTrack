package com.example.fieldtrack.feature.region

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.fieldtrack.R
import com.example.fieldtrack.data.auth.PhoneNumbers
import com.example.fieldtrack.data.region.Region
import com.example.fieldtrack.designsystem.component.FtTextField

/** The calling code the phone is in, which is where most numbers typed on it will be from. */
@Composable
fun rememberDeviceDial(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember { PhoneNumbers.dialOf(Region.detect(context).country) ?: PhoneNumbers.LEGACY_DIAL }
}

/**
 * A phone number box with its country code in front, which can be tapped to change it.
 *
 * The digits and the code are separate on screen and one string in storage. When the
 * person pastes a number that carries its own code, as a number from a visiting card or
 * a chat does, the code is taken from it and the box shows the rest, so a number is
 * never saved with its code twice.
 */
@Composable
fun PhoneInput(
    dial: String,
    national: String,
    /** Always both together, so a code pasted in with its number can never be split from it. */
    onChange: (dial: String, national: String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    helper: String? = null,
    focused: Boolean = false,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val deviceCountry = rememberDeviceCountry()
    FtTextField(
        label = label,
        value = national,
        onValueChange = { typed ->
            // A pasted international number brings its own code.
            if (typed.trimStart().startsWith("+")) {
                val (pastedDial, rest) = PhoneNumbers.split(typed)
                onChange(pastedDial, PhoneNumbers.nationalDigits(rest, pastedDial))
            } else onChange(dial, PhoneNumbers.nationalDigits(typed, dial))
        },
        placeholder = placeholder,
        helper = helper,
        keyboardType = KeyboardType.Phone,
        prefix = "+$dial \u25BE",
        onPrefixClick = { picking = true },
        focused = focused,
        modifier = modifier,
    )
    if (picking) {
        val options = remember(deviceCountry) {
            PhoneNumbers.countries(deviceCountry).map {
                PickOption("${it.iso}", it.name, "+${it.dial}", "${it.name} +${it.dial} ${it.iso}")
            }
        }
        SearchPickerSheet(
            title = stringResource(R.string.phone_country_title),
            searchHint = stringResource(R.string.phone_country_search),
            options = options,
            currentKey = options.firstOrNull { it.subtitle == "+$dial" }?.key,
            onPick = { iso -> PhoneNumbers.dialOf(iso)?.let { onChange(it, national) }; picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun rememberDeviceCountry(): String? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember { Region.detect(context).country }
}

/**
 * A phone number kept as one string, as it is stored: the number or nothing.
 *
 * Whatever is already stored is shown (an old ten-digit number as +91, a newer one with
 * its own code); a blank one starts from the country this phone is in.
 */
@Composable
fun PhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    helper: String? = null,
    focused: Boolean = false,
) {
    val deviceDial = rememberDeviceDial()
    val (storedDial, storedNational) = remember(value) { PhoneNumbers.split(value) }
    var dial by rememberSaveable { mutableStateOf(if (value.isBlank()) deviceDial else storedDial) }
    val national = if (value.isBlank()) "" else storedNational
    PhoneInput(
        dial = dial,
        national = national,
        onChange = { newDial, digits ->
            dial = newDial
            onValueChange(PhoneNumbers.e164(newDial, digits))
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        helper = helper,
        focused = focused,
    )
}
