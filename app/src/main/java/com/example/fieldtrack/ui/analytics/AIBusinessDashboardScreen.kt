package com.example.fieldtrack.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBusinessDashboardScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Business Intelligence") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Today's AI Summary", style = MaterialTheme.typography.titleMedium)
                        Text("12 jobs completed. Revenue ₹84,000. Fuel ₹9,200.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Smart Alert: Maintenance Prediction", style = MaterialTheme.typography.titleMedium)
                        Text("Machine 4 (Tractor T-05) has high breakdown risk based on recent fuel consumption and hours worked.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Payment Intelligence", style = MaterialTheme.typography.titleMedium)
                        Text("Two customers have growing outstanding balances. Recommend prioritizing follow-up today.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Machine Profitability", style = MaterialTheme.typography.titleMedium)
                        Text("Machine 2 generated the highest ROI this month. Consider increasing utilization.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ask AI (e.g. 'Which machine needs service?')") },
                    trailingIcon = { Text("?") }
                )
            }
        }
    }
}
