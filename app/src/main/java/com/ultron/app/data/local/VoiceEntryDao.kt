package com.ultron.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceEntryDao {
    @Insert
    suspend fun insert(entry: VoiceEntry): Long

    @Update
    suspend fun update(entry: VoiceEntry)

    @Query("SELECT * FROM voice_entries WHERE sendStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingEntries(): List<VoiceEntry>

    @Query("SELECT * FROM voice_entries ORDER BY createdAt DESC")
    fun getAllEntries(): Flow<List<VoiceEntry>>

    @Query("SELECT * FROM voice_entries ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentEntries(limit: Int = 50): Flow<List<VoiceEntry>>

    @Query("UPDATE voice_entries SET sendStatus = :status, sentAt = :sentAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SendStatus, sentAt: Long? = null)

    @Query("SELECT COUNT(*) FROM voice_entries WHERE sendStatus = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}
