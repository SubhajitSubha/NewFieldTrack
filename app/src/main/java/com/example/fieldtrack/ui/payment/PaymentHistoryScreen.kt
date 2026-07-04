package com.example.fieldtrack.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(
    customerId: String,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Ledger") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Immutable Transaction Log",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                LedgerEntry("Received ₹1000", "Cash by Rahul", "2 Jul 2026")
            }
            item {
                LedgerEntry("Received ₹500", "UPI Reference XXXXX", "1 Jul 2026")
            }
            item {
                LedgerEntry("Billed ₹2500", "Rotavation - North Field", "1 Jul 2026", isCredit = false)
            }
            item {
                LedgerEntry("Received ₹2000", "Bank Transfer", "28 Jun 2026")
            }
        }
    }
}

@Composable
fun LedgerEntry(amountText: String, detailText: String, dateText: String, isCredit: Boolean = true) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCredit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(amountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isCredit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detailText, style = MaterialTheme.typography.bodyMedium)
            Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
