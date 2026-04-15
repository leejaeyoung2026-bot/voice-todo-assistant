package com.ultron.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [VoiceEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class UltronDatabase : RoomDatabase() {
    abstract fun voiceEntryDao(): VoiceEntryDao
}
