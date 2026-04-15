package com.ultron.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BudsController(private val context: Context) {
    private var bluetoothHeadset: BluetoothHeadset? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var onMediaButtonAction: (() -> Unit)? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                _isConnected.value = bluetoothHeadset?.connectedDevices?.isNotEmpty() == true
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
                _isConnected.value = false
            }
        }
    }

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    _isConnected.value = state == BluetoothProfile.STATE_CONNECTED
                }
            }
        }
    }

    private val mediaButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                onMediaButtonAction?.invoke()
            }
        }
    }

    fun initialize() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)

        // BT connection state changes
        val connectionFilter = IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        context.registerReceiver(connectionReceiver, connectionFilter)

        // Media button events (buds touch)
        val mediaFilter = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
        mediaFilter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        context.registerReceiver(mediaButtonReceiver, mediaFilter)
    }

    fun startScoAudio() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }

    fun stopScoAudio() {
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun setOnMediaButton(action: () -> Unit) {
        onMediaButtonAction = action
    }

    fun release() {
        try { context.unregisterReceiver(connectionReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(mediaButtonReceiver) } catch (_: Exception) {}
        stopScoAudio()
        bluetoothHeadset?.let {
            BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HEADSET, it)
        }
    }
}
