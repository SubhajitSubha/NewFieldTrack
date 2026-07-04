package com.example.fieldtrack.ui.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.data.local.entity.TeamMemberEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManagementScreen(
    onBackClick: () -> Unit,
    onNavigateToMember: (String) -> Unit
) {
    val members = remember {
        listOf(
            TeamMemberEntity(id = "1", orgId = "org1", userId = "u1", role = "OWNER", name = "Rajesh Patel", phone = "9876543210", isOnline = true),
            TeamMemberEntity(id = "2", orgId = "org1", userId = "u2", role = "MANAGER", name = "Suresh Kumar", phone = "9876543211", isOnline = true),
            TeamMemberEntity(id = "3", orgId = "org1", userId = "u3", role = "OPERATOR", name = "Raj Kumar", phone = "9876543212", currentJobId = "job1", currentMachineId = "m1", isOnline = true),
            TeamMemberEntity(id = "4", orgId = "org1", userId = "u4", role = "OPERATOR", name = "Amit Singh", phone = "9876543213", status = "DISABLED", isOnline = false)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Team Management") },
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
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(members) { member ->
                MemberCard(member = member, onClick = { onNavigateToMember(member.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberCard(member: TeamMemberEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (member.status == "DISABLED") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(member.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (member.currentMachineId != null) {
                    Text("Machine Assigned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Badge(containerColor = if (member.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Text(if (member.isOnline) "ONLINE" else "OFFLINE", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (member.status == "DISABLED") {
                    Text("DISABLED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
