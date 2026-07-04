package com.example.fieldtrack.ui.machine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetAssetDashboardScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fleet & Asset Management") }
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
                        Text(text = "Machine Master Profile", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Track lifecycle, insurance, and documents", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Maintenance Engine", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Schedule services and track maintenance history", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Fuel Management", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Track fuel entries and calculate consumption rates", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Breakdown Management", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Log machine failures and track downtime/repair costs", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Asset Financials", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Calculate ROI, cost per hour, and net profit per machine", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Inventory & Vendors", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Manage spare parts inventory and vendor relationships", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
