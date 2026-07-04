package com.example.fieldtrack.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDashboardScreen(
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Dashboard", fontWeight = FontWeight.Bold) },
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Revenue Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RevenueCard("Today", "₹12,500", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    RevenueCard("Weekly", "₹85,000", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RevenueCard("Monthly", "₹3,40,000", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    RevenueCard("Pending", "₹1,15,000", MaterialTheme.colorScheme.error, Modifier.weight(1f))
                }
            }

            item {
                Text("Collection Trend (Cash vs UPI vs Bank)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cash")
                            Text("45%", fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { 0.45f }, modifier = Modifier.fillMaxWidth())
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("UPI")
                            Text("35%", fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { 0.35f }, modifier = Modifier.fillMaxWidth())
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bank Transfer")
                            Text("20%", fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { 0.20f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            
            item {
                Text("Highest Outstanding Customers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }
            
            item {
                OutstandingCustomerCard("Rajesh Kumar", "₹45,000")
            }
            item {
                OutstandingCustomerCard("Amit Singh", "₹22,500")
            }
            
            item {
                Text("Most Valuable Customers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }
            
            item {
                OutstandingCustomerCard("Suresh Patel", "₹1,20,000", isOutstanding = false)
            }
        }
    }
}

@Composable
fun RevenueCard(title: String, amount: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun OutstandingCustomerCard(name: String, amount: String, isOutstanding: Boolean = true) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOutstanding) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}
