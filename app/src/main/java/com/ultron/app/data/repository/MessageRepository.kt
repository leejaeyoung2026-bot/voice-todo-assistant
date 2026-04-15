package com.ultron.app.data.repository

import com.ultron.app.data.local.SendStatus
import com.ultron.app.data.local.VoiceEntry
import com.ultron.app.data.local.VoiceEntryDao
import com.ultron.app.data.remote.SendMessageRequest
import com.ultron.app.data.remote.TelegramApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val telegramApi: TelegramApi,
    private val voiceEntryDao: VoiceEntryDao
) {
    suspend fun sendToTelegram(entry: VoiceEntry, botToken: String, chatId: String): Boolean {
        val prefix = entry.categoryHint?.let { "[${it.name}] " } ?: ""
        val request = SendMessageRequest(
            chat_id = chatId,
            text = "$prefix${entry.transcript}"
        )
        return try {
            val response = telegramApi.sendMessage(botToken, request)
            if (response.isSuccessful) {
                voiceEntryDao.updateStatus(entry.id, SendStatus.SENT, System.currentTimeMillis())
                true
            } else {
                voiceEntryDao.updateStatus(entry.id, SendStatus.FAILED)
                false
            }
        } catch (e: Exception) {
            voiceEntryDao.updateStatus(entry.id, SendStatus.FAILED)
            false
        }
    }

    suspend fun retrySendPending(botToken: String, chatId: String): Int {
        val pending = voiceEntryDao.getPendingEntries()
        var sentCount = 0
        for (entry in pending) {
            if (sendToTelegram(entry, botToken, chatId)) sentCount++
        }
        return sentCount
    }
}
