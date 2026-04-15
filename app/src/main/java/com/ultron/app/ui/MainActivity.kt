package com.ultron.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultron.app.ui.screens.HistoryScreen
import com.ultron.app.ui.screens.HomeScreen
import com.ultron.app.ui.theme.UltronTheme
import com.ultron.app.worker.RetryWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        RetryWorker.enqueuePeriodicRetry(this)

        setContent {
            UltronTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                val entries by viewModel.recentEntries.collectAsState(initial = emptyList())
                val pendingCount by viewModel.pendingCount.collectAsState(initial = 0)
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, "홈") },
                                label = { Text("홈") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.History, "기록") },
                                label = { Text("기록") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Settings, "설정") },
                                label = { Text("설정") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> HomeScreen(
                            uiState = uiState,
                            pendingCount = pendingCount,
                            onToggleService = {
                                if (uiState.isListening) viewModel.stopService()
                                else viewModel.startService()
                            },
                            onToggleRecording = { viewModel.toggleRecording() },
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> HistoryScreen(entries, Modifier.padding(innerPadding))
                        2 -> { /* SettingsScreen — Task 8 */ }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
