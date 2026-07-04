cat << 'INNER' > app/src/main/java/com/example/fieldtrack/ui/customer/CustomerDetailScreen.kt
package com.example.fieldtrack.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.data.local.entity.CustomerEntity
import com.example.fieldtrack.data.local.entity.FieldEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
    onBackClick: () -> Unit,
    onNavigateToNewField: (String) -> Unit,
    onNavigateToFieldDetail: (String) -> Unit,
    onNavigateToNewPayment: (String) -> Unit,
    onNavigateToPaymentHistory: (String) -> Unit
) {
    val customer = remember {
        CustomerEntity(
            id = customerId,
            orgId = "org1",
            name = "John Smith",
            phone = "555-0101",
            address = "123 Farm Rd",
            notes = "Prefers morning contact",
            village = "North Village",
            outstandingBalance = 5000.0,
            totalArea = 45.5,
            totalJobs = 12,
            totalPaid = 15000.0
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToNewField(customerId) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Field")
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
                CustomerStatsCard(customer)
            }
            item {
                CustomerQuickActions(
                    customer = customer,
                    onNavigateToNewPayment = { onNavigateToNewPayment(customerId) },
                    onNavigateToPaymentHistory = { onNavigateToPaymentHistory(customerId) }
                )
            }
            
            item {
                Text(
                    text = "Timeline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            
            // Mock Timeline
            item { Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                TimelineWorkItem("Rotavation - North Field", "₹2500", "Paid ₹1000 • Pending ₹1500")
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            item { Text("Yesterday", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                TimelineWorkItem("Trailer - South Field", "8 Trips", "Completed")
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            item { Text("Last Week", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                TimelineWorkItem("Harvest - East Field", "₹12000", "Completed")
            }
        }
    }
}

@Composable
fun CustomerStatsCard(customer: CustomerEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Outstanding Balance", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "₹${customer.outstandingBalance}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (customer.outstandingBalance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Total Value", "₹${customer.totalWorkValue}")
                StatItem("Total Paid", "₹${customer.totalPaid}")
                StatItem("Total Jobs", customer.totalJobs.toString())
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CustomerQuickActions(
    customer: CustomerEntity,
    onNavigateToNewPayment: () -> Unit,
    onNavigateToPaymentHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { /* TODO Start Work */ }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Work")
            }
            FilledTonalButton(onClick = onNavigateToNewPayment, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pay")
            }
        }
    }
}

@Composable
fun TimelineWorkItem(title: String, value: String, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
INNER
