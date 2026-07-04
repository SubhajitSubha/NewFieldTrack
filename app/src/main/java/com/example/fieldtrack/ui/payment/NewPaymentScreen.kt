package com.example.fieldtrack.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPaymentScreen(
    customerId: String,
    onBackClick: () -> Unit,
    onSavePayment: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isMethodDropdownExpanded by remember { mutableStateOf(false) }
    val paymentMethods = listOf("Cash", "Bank Transfer", "UPI", "Cheque", "Other")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Payment") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = isMethodDropdownExpanded,
                onExpandedChange = { isMethodDropdownExpanded = !isMethodDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = method,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Payment Method") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMethodDropdownExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = isMethodDropdownExpanded,
                    onDismissRequest = { isMethodDropdownExpanded = false }
                ) {
                    paymentMethods.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                method = selectionOption
                                isMethodDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it },
                label = { Text("Reference Number (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSavePayment,
                modifier = Modifier.fillMaxWidth(),
                enabled = amount.isNotBlank()
            ) {
                Text("Save Payment")
            }
        }
    }
}
