package com.ultron.app.data.di

import com.ultron.app.data.remote.TelegramApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideTelegramApi(client: OkHttpClient): TelegramApi {
        return Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelegramApi::class.java)
    }
}
