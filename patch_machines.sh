cat << 'INNER' > app/src/main/java/com/example/fieldtrack/ui/machine/MachinesScreen.kt
package com.example.fieldtrack.ui.machine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.data.local.entity.MachineEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinesScreen(
    onBackClick: () -> Unit,
    onNavigateToNewMachine: () -> Unit
) {
    // Mock data for now
    val machines = remember {
        listOf(
            MachineEntity(id = "1", orgId = "org1", name = "Tractor 6155R", type = "Tractor", registrationNumber = "AB12 CDE"),
            MachineEntity(id = "2", orgId = "org1", name = "Claas Lexion 760", type = "Harvester", registrationNumber = "XY98 ZWQ")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Machines") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewMachine) {
                Icon(Icons.Default.Add, contentDescription = "Add Machine")
            }
        }
    ) { innerPadding ->
        if (machines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("No Machines", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your machines will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateToNewMachine) {
                        Text("Add your first machine")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(machines) { machine ->
                    MachineCard(machine = machine)
                }
            }
        }
    }
}

@Composable
fun MachineCard(machine: MachineEntity) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = machine.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Type: ${machine.type}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!machine.registrationNumber.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reg: ${machine.registrationNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Recent Timeline", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("• Today: Ploughing (North Field) - 4 hrs", style = MaterialTheme.typography.bodySmall)
            Text("• Yesterday: Maintenance (Oil Change)", style = MaterialTheme.typography.bodySmall)
            Text("• 3 Days Ago: Fuel (50L)", style = MaterialTheme.typography.bodySmall)
        }
    }
}
INNER
