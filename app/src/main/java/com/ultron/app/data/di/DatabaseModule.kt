package com.ultron.app.data.di

import android.content.Context
import androidx.room.Room
import com.ultron.app.data.local.UltronDatabase
import com.ultron.app.data.local.VoiceEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UltronDatabase {
        return Room.databaseBuilder(
            context,
            UltronDatabase::class.java,
            "ultron.db"
        ).build()
    }

    @Provides
    fun provideVoiceEntryDao(db: UltronDatabase): VoiceEntryDao = db.voiceEntryDao()
}
