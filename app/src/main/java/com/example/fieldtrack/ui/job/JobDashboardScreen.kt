package com.example.fieldtrack.ui.job

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.data.local.entity.JobEntity
import com.example.fieldtrack.data.local.entity.JobEventEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDashboardScreen(
    jobId: String,
    onBackClick: () -> Unit,
    onStartWorkClick: () -> Unit
) {
    // Mock Data
    val job = remember {
        JobEntity(
            id = jobId,
            orgId = "org1",
            customerId = "1",
            fieldId = "f1",
            machineId = "m1",
            operatorId = "o1",
            status = "ASSIGNED",
            workType = "Harvesting",
            priority = "HIGH",
            jobName = "Harvesting - North Field",
            location = "North Village",
            startedAt = null,
            completedAt = null
        )
    }

    val timeline = remember {
        listOf(
            JobEventEntity(id = "e1", orgId = "org1", jobId = jobId, eventType = "CREATED", actorId = "owner1", description = "Job created and assigned"),
            JobEventEntity(id = "e2", orgId = "org1", jobId = jobId, eventType = "ACCEPTED", actorId = "o1", description = "Operator accepted assignment")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Dashboard") },
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
            if (job.status == "ASSIGNED" || job.status == "ACCEPTED" || job.status == "PAUSED") {
                FloatingActionButton(onClick = onStartWorkClick) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Work")
                }
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
                JobOverviewCard(job)
            }
            
            item {
                Text("Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            
            items(timeline) { event ->
                TimelineEventCard(event)
            }
        }
    }
}

@Composable
fun JobOverviewCard(job: JobEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = job.workType, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Badge(containerColor = if (job.priority == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
                    Text(job.priority, modifier = Modifier.padding(4.dp))
                }
            }
            Text("Customer: John Smith")
            Text("Field: North Field")
            Text("Machine: Claas Lexion 760")
            Text("Operator: Raj Kumar")
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Status: ${job.status}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TimelineEventCard(event: JobEventEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(event.eventType, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (event.description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(event.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
