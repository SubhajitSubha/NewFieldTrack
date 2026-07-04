cat << 'INNER' > app/src/main/java/com/example/fieldtrack/ui/job/NewJobScreen.kt
package com.example.fieldtrack.ui.job

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewJobScreen(
    onBackClick: () -> Unit,
    onSaveJob: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    
    var selectedCustomer by remember { mutableStateOf("") }
    var selectedField by remember { mutableStateOf("") }
    var selectedMachine by remember { mutableStateOf("") }
    var selectedWorkType by remember { mutableStateOf("") }
    var suggestedBilling by remember { mutableStateOf("") }

    val customers = listOf("John Smith", "Alice Cooper", "Rajesh Kumar")
    val fields = listOf("North Field", "East Field", "Home Farm")
    val machines = listOf("Harvester - Claas Lexion", "Tractor - 6155R", "Rotavator")
    val machineCapabilities = mapOf(
        "Harvester - Claas Lexion" to listOf("Harvesting"),
        "Tractor - 6155R" to listOf("Ploughing", "Trailer", "Seed Drill"),
        "Rotavator" to listOf("Rotavation", "Puddling")
    )
    val billingRules = mapOf(
        "Harvesting" to "Per Acre",
        "Ploughing" to "Per Hour",
        "Trailer" to "Per Trip",
        "Seed Drill" to "Per Acre",
        "Rotavation" to "Per Hour",
        "Puddling" to "Per Hour"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start New Work") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (step >= 5) {
                ExtendedFloatingActionButton(
                    onClick = onSaveJob,
                    icon = { Icon(Icons.Default.Check, "Start Work") },
                    text = { Text("Start Work") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step 1: Customer
            ConversationalStep(
                question = "Who are you working for?",
                answer = selectedCustomer,
                isActive = step == 1,
                options = customers,
                onSelect = { selectedCustomer = it; step = 2 }
            )

            // Step 2: Field
            AnimatedVisibility(visible = step >= 2) {
                ConversationalStep(
                    question = "Which field?",
                    answer = selectedField,
                    isActive = step == 2,
                    options = fields,
                    onSelect = { selectedField = it; step = 3 }
                )
            }

            // Step 3: Machine
            AnimatedVisibility(visible = step >= 3) {
                ConversationalStep(
                    question = "Which machine?",
                    answer = selectedMachine,
                    isActive = step == 3,
                    options = machines,
                    onSelect = { selectedMachine = it; step = 4 }
                )
            }

            // Step 4: Work Type
            AnimatedVisibility(visible = step >= 4) {
                val availableWorkTypes = machineCapabilities[selectedMachine] ?: emptyList()
                ConversationalStep(
                    question = "What type of work?",
                    answer = selectedWorkType,
                    isActive = step == 4,
                    options = availableWorkTypes,
                    onSelect = { 
                        selectedWorkType = it; 
                        suggestedBilling = billingRules[it] ?: "Custom"
                        step = 5 
                    }
                )
            }

            // Step 5: Billing confirmation
            AnimatedVisibility(visible = step >= 5) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Suggested Billing", style = MaterialTheme.typography.labelMedium)
                        Text(suggestedBilling, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Based on work type and machine.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationalStep(
    question: String,
    answer: String,
    isActive: Boolean,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        
        if (answer.isNotEmpty() && !isActive) {
            Text(answer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Divider(modifier = Modifier.padding(top = 8.dp))
        } else if (isActive) {
            options.forEach { option ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelect(option) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(option, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
INNER
