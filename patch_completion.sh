cat << 'INNER' > app/src/main/java/com/example/fieldtrack/ui/job/CompletionSheetScreen.kt
package com.example.fieldtrack.ui.job

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionSheetScreen(
    onBackClick: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    var areaCompleted by remember { mutableStateOf("25") }
    var additionalNotes by remember { mutableStateOf("") }
    var totalCost by remember { mutableStateOf("12500") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Work Summary") },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Job Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Customer: John Smith")
                    Text("Field: North Field")
                    Text("Work Type: Harvesting")
                    Text("Machine: Claas Lexion 760")
                    Text("Total Time: 02:15:43")
                    Text("Total Loads: 4")
                }
            }
            
            Text("Final Measurements", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            OutlinedTextField(
                value = areaCompleted,
                onValueChange = { areaCompleted = it },
                label = { Text("Area Completed (Acres)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            OutlinedTextField(
                value = totalCost,
                onValueChange = { totalCost = it },
                label = { Text("Total Bill (₹)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            OutlinedTextField(
                value = additionalNotes,
                onValueChange = { additionalNotes = it },
                label = { Text("Notes for Customer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onNavigateToHome,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Save Work Summary")
            }
        }
    }
}
INNER
