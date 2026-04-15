package com.ultron.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ultron_settings")

class SettingsStore(private val context: Context) {
    companion object {
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val VOSK_MODEL_DOWNLOADED = booleanPreferencesKey("vosk_model_downloaded")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val BEEP_ON_RECORD = booleanPreferencesKey("beep_on_record")
    }

    val wakeWordEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[WAKE_WORD_ENABLED] ?: true
    }

    val voskModelDownloaded: Flow<Boolean> = context.dataStore.data.map {
        it[VOSK_MODEL_DOWNLOADED] ?: false
    }

    val autoStartOnBoot: Flow<Boolean> = context.dataStore.data.map {
        it[AUTO_START_ON_BOOT] ?: true
    }

    val beepOnRecord: Flow<Boolean> = context.dataStore.data.map {
        it[BEEP_ON_RECORD] ?: true
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setVoskModelDownloaded(downloaded: Boolean) {
        context.dataStore.edit { it[VOSK_MODEL_DOWNLOADED] = downloaded }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START_ON_BOOT] = enabled }
    }

    suspend fun setBeepOnRecord(enabled: Boolean) {
        context.dataStore.edit { it[BEEP_ON_RECORD] = enabled }
    }
}
