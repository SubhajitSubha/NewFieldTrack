package com.example.fieldtrack.ui.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.data.local.entity.TeamMemberEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfileScreen(
    memberId: String,
    onBackClick: () -> Unit
) {
    val member = remember {
        TeamMemberEntity(
            id = memberId, orgId = "org1", userId = "u3", role = "OPERATOR",
            name = "Raj Kumar", phone = "9876543212", currentJobId = "job1", currentMachineId = "m1",
            assignedJobsCount = 5, completedJobsCount = 120, workingHours = 450.5
        )
    }

    var selectedRole by remember { mutableStateOf(member.role) }
    var isExpanded by remember { mutableStateOf(false) }
    val roles = listOf("OWNER", "MANAGER", "SUPERVISOR", "OPERATOR", "HELPER", "ACCOUNTANT", "VIEWER")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Member Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
                        Text(member.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(member.phone ?: "No Phone", style = MaterialTheme.typography.bodyMedium)
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text("Performance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Completed Jobs")
                            Text(member.completedJobsCount.toString(), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Working Hours")
                            Text("${member.workingHours} hrs", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("Role & Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = !isExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRole,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false }
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    selectedRole = role
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { /* TODO Disable Member */ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disable Member")
                }
            }
            
            item {
                OutlinedButton(
                    onClick = { /* TODO Reset Device */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Device Login")
                }
            }
        }
    }
}
