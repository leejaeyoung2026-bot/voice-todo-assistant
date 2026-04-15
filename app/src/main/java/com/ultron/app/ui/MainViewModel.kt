package com.ultron.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import com.ultron.app.data.local.VoiceEntryDao
import com.ultron.app.service.UltronListenerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class UiState(
    val isListening: Boolean = false,
    val isRecording: Boolean = false,
    val budsConnected: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val voiceEntryDao: VoiceEntryDao
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val recentEntries = voiceEntryDao.getRecentEntries(50)
    val pendingCount = voiceEntryDao.getPendingCount()

    private var service: UltronListenerService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as UltronListenerService.LocalBinder).getService()
            _uiState.update { it.copy(isListening = true) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _uiState.update { it.copy(isListening = false) }
        }
    }

    fun startService() {
        val intent = Intent(app, UltronListenerService::class.java)
        app.startForegroundService(intent)
        app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun stopService() {
        try { app.unbindService(connection) } catch (_: Exception) {}
        app.stopService(Intent(app, UltronListenerService::class.java))
        service = null
        _uiState.update { it.copy(isListening = false, isRecording = false) }
    }

    fun toggleRecording() {
        service?.toggleRecording()
    }
}
