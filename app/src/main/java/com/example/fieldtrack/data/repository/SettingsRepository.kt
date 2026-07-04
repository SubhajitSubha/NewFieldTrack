package com.example.fieldtrack.data.repository

import android.content.Context
import com.example.fieldtrack.data.local.entity.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(val context: Context? = null) {
    val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings())
}
