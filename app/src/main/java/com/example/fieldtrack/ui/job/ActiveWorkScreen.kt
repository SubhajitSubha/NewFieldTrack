package com.example.fieldtrack.ui.job

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkScreen(
    onBackClick: () -> Unit,
    onCompleteJob: () -> Unit
) {
    var isPaused by remember { mutableStateOf(false) }
    var loads by remember { mutableStateOf(4) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var currentOperator by remember { mutableStateOf("Raj Kumar") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Work") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showTransferDialog = true }) {
                        Icon(Icons.Default.Sync, contentDescription = "Transfer Operator")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Harvesting", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("John Smith • North Field", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Operator: $currentOperator", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPaused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (isPaused) "PAUSED" else "02:15:43",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { if (loads > 0) loads-- }) {
                    Text("-", fontSize = 24.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$loads", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("Loads", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = { loads++ }) {
                    Text("+", fontSize = 24.sp)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isPaused) {
                    Button(
                        onClick = { isPaused = false },
                        modifier = Modifier.weight(1f).height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RESUME", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { showPauseDialog = true },
                        modifier = Modifier.weight(1f).height(64.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PAUSE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Button(
                    onClick = onCompleteJob,
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FINISH", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPauseDialog) {
        AlertDialog(
            onDismissRequest = { showPauseDialog = false },
            title = { Text("Pause Reason") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val reasons = listOf("Fuel", "Lunch", "Breakdown", "Rain", "Waiting", "Traffic", "Farmer Delay")
                    reasons.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { reason ->
                                OutlinedButton(
                                    onClick = { isPaused = true; showPauseDialog = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(reason)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPauseDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTransferDialog) {
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Transfer Operator") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select new operator to continue this job:")
                    val operators = listOf("Suresh", "Amit", "Ramesh")
                    operators.forEach { op ->
                        OutlinedButton(
                            onClick = { currentOperator = op; showTransferDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(op)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTransferDialog = false }) { Text("Cancel") }
            }
        )
    }
}
