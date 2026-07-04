package com.example.fieldtrack.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    onBackClick: () -> Unit,
    onNavigateToFinancial: () -> Unit,
    onNavigateToCustomerAnalytics: () -> Unit,
    onNavigateToMachineAnalytics: () -> Unit,
    onNavigateToOperatorAnalytics: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Executive Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Export */ }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Reports")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Live KPIs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCard("Running Jobs", "5", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    KpiCard("Completed Today", "12", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    KpiCard("Machines Active", "8", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
            }

            item {
                Text("Financial Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }
            
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCard("Today's Revenue", "₹24,500", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    KpiCard("Outstanding", "₹1,15,000", MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }
            
            item {
                Text("Detailed Reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportNavigationCard(
                        title = "Financial Analytics",
                        subtitle = "Revenue, Collections, Cashflow",
                        icon = Icons.Default.AttachMoney,
                        onClick = onNavigateToFinancial
                    )
                    ReportNavigationCard(
                        title = "Customer Analytics",
                        subtitle = "LTV, Demographics, Outstanding",
                        icon = Icons.Default.Group,
                        onClick = onNavigateToCustomerAnalytics
                    )
                    ReportNavigationCard(
                        title = "Machine Analytics",
                        subtitle = "Utilization, Fuel, Downtime",
                        icon = Icons.Default.Agriculture,
                        onClick = onNavigateToMachineAnalytics
                    )
                    ReportNavigationCard(
                        title = "Operator Analytics",
                        subtitle = "Performance, Efficiency, Hours",
                        icon = Icons.Default.Person,
                        onClick = onNavigateToOperatorAnalytics
                    )
                }
            }
        }
    }
}

@Composable
fun KpiCard(title: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportNavigationCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
