package com.example.fieldtrack.ui.field

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.data.local.entity.FieldEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldDetailScreen(
    fieldId: String,
    onBackClick: () -> Unit
) {
    // Mock Data
    val field = remember {
        FieldEntity(
            id = fieldId,
            orgId = "org1",
            customerId = "1",
            name = "North Field",
            village = "North Village",
            area = 20.0,
            cropName = "Wheat",
            totalJobs = 5,
            totalCost = 15000.0,
            totalLoads = 40,
            totalMachineHours = 12.5
        )
    }

    // Mock Timeline History
    val history = remember {
        listOf(
            FieldHistoryItem("2026-06-25", "Harvesting", "Claas Lexion", "4 hrs", "12 Loads", "₹4000"),
            FieldHistoryItem("2026-05-10", "Spraying", "Tractor 6155R", "2 hrs", "-", "₹2000"),
            FieldHistoryItem("2026-03-15", "Ploughing", "Tractor 6155R", "6.5 hrs", "-", "₹9000")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(field.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: Start Job */ }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start Work")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FieldStatsCard(field)
            }

            item {
                Text(
                    text = "Operational Timeline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(history) { item ->
                HistoryTimelineCard(item)
            }
        }
    }
}

@Composable
fun FieldStatsCard(field: FieldEntity) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Crop: ${field.cropName ?: "N/A"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${field.area ?: 0.0} ${field.areaUnit ?: "Acres"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItemField("Total Jobs", field.totalJobs.toString())
                StatItemField("Machine Hrs", "${field.totalMachineHours}")
                StatItemField("Revenue", "₹${field.totalCost}")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItemField("Top Operator", "Raj Kumar")
                StatItemField("Top Machine", "Claas Lexion")
            }
        }
    }
}

@Composable
fun StatItemField(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class FieldHistoryItem(
    val date: String,
    val type: String,
    val machine: String,
    val duration: String,
    val loads: String,
    val cost: String
)

@Composable
fun HistoryTimelineCard(item: FieldHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.type,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Machine: ${item.machine}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Time: ${item.duration}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Loads: ${item.loads}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Cost: ${item.cost}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
