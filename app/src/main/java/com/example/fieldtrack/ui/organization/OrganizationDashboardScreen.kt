package com.example.fieldtrack.ui.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationDashboardScreen(
    onBackClick: () -> Unit,
    onNavigateToTeam: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organization", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* TODO Invite Member */ },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Invite") },
                text = { Text("Invite Member") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("AgriCorp Solutions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Owner: Rajesh Patel", style = MaterialTheme.typography.bodyMedium)
                        Text("Phone: +91 9876543210", style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Members", fontWeight = FontWeight.Bold)
                            Text("5")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Machines", fontWeight = FontWeight.Bold)
                            Text("3")
                        }
                    }
                }
            }

            item {
                Text("Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                OutlinedButton(
                    onClick = onNavigateToTeam,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Team & Roles Management")
                }
            }

            item {
                Text("Activity Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }

            item {
                AuditLogCard("Raj Kumar (Operator)", "Started Harvesting on North Field", "10 mins ago")
            }
            item {
                AuditLogCard("Suresh (Manager)", "Assigned Claas Lexion 760", "2 hrs ago")
            }
        }
    }
}

@Composable
fun AuditLogCard(actor: String, action: String, time: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(action, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(actor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
