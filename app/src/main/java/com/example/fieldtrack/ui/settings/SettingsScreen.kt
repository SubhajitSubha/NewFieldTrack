package com.example.fieldtrack.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class SettingItem(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToOrganizations: () -> Unit,
    onNavigateToTeam: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val items = listOf(
        SettingItem("Profile", Icons.Default.Person, onNavigateToProfile),
        SettingItem("Organizations", Icons.Default.Business, onNavigateToOrganizations),
        SettingItem("Team", Icons.Default.Group, onNavigateToTeam),
        SettingItem("Devices", Icons.Default.Phone, onNavigateToDevices),
        SettingItem("Sync", Icons.Default.Sync, {}),
        SettingItem("Notifications", Icons.Default.Notifications, {}),
        SettingItem("Language", Icons.Default.Language, {}),
        SettingItem("Support", Icons.Default.Help, {}),
        SettingItem("About", Icons.Default.Info, {}),
        SettingItem("Logout", Icons.Default.ExitToApp, onLogoutClick)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(items) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    leadingContent = { Icon(item.icon, contentDescription = null) },
                    modifier = Modifier.clickable { item.onClick() }
                )
                Divider()
            }
        }
    }
}
