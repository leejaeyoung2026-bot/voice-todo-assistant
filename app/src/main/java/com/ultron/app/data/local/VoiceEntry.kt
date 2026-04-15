package com.ultron.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Category {
    TODO,
    SCHEDULE,
    ELN,
    CHAT
}

enum class SendStatus {
    PENDING,
    SENT,
    FAILED
}

@Entity(tableName = "voice_entries")
data class VoiceEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcript: String,
    val categoryHint: Category? = null,
    val sendStatus: SendStatus = SendStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val retryCount: Int = 0
)
