# Ultron Android App — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 갤럭시 버즈3 프로 웨이크워드("울트론") + 토글 STT로 음성을 전사하고, Telegram을 통해 Mac Mini OpenClaw로 전송하여 Notion/Google Calendar에 자동 라우팅하는 Android 네이티브 앱 구현.

**Architecture:** Kotlin + Jetpack Compose 단일 앱. Vosk로 웨이크워드 감지, Android SpeechRecognizer로 토글 STT, Retrofit으로 Telegram Bot API 전송. Room DB로 오프라인 큐, WorkManager로 재전송. Foreground Service로 상시 대기.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Vosk, SpeechRecognizer, BluetoothHeadset API, Retrofit/OkHttp, Room, WorkManager, Hilt

---

## Task 0: 프로젝트 스캐폴딩

**Files:**
- Create: `app/build.gradle.kts`
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/ultron/app/UltronApplication.kt`

**Step 1: Android 프로젝트 초기화**

```bash
# voice-todo-assistant 루트에서
mkdir -p app/src/main/java/com/ultron/app
mkdir -p app/src/main/res/values
mkdir -p app/src/test/java/com/ultron/app
mkdir -p app/src/androidTest/java/com/ultron/app
```

**Step 2: root build.gradle.kts 작성**

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
```

**Step 3: app/build.gradle.kts 작성**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

android {
    namespace = "com.ultron.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ultron.app"
        minSdk = 29  // Android 10 (S23 Ultra 기본)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"${project.findProperty("TELEGRAM_BOT_TOKEN") ?: ""}\"")
        buildConfigField("String", "TELEGRAM_CHAT_ID", "\"${project.findProperty("TELEGRAM_CHAT_ID") ?: ""}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Vosk (웨이크워드)
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

**Step 4: settings.gradle.kts 작성**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
        maven("https://alphacephei.com/maven/")
    }
}
rootProject.name = "Ultron"
include(":app")
```

**Step 5: AndroidManifest.xml 작성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".UltronApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="Ultron"
        android:supportsRtl="true"
        android:theme="@style/Theme.Ultron">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Ultron">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.UltronListenerService"
            android:foregroundServiceType="microphone"
            android:exported="false" />

        <receiver
            android:name=".receiver.BootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

**Step 6: UltronApplication.kt 작성**

```kotlin
package com.ultron.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UltronApplication : Application()
```

**Step 7: 커밋**

```bash
git add -A
git commit -m "feat: Ultron Android 프로젝트 스캐폴딩

Kotlin + Jetpack Compose + Hilt + Room + Vosk + Retrofit 의존성 구성.
Foreground Service 퍼미션 및 매니페스트 설정."
```

---

## Task 1: Room DB — 오프라인 큐 + 히스토리

**Files:**
- Create: `app/src/main/java/com/ultron/app/data/local/UltronDatabase.kt`
- Create: `app/src/main/java/com/ultron/app/data/local/VoiceEntry.kt`
- Create: `app/src/main/java/com/ultron/app/data/local/VoiceEntryDao.kt`
- Create: `app/src/main/java/com/ultron/app/data/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/ultron/app/data/local/VoiceEntryTest.kt`

**Step 1: 데이터 엔티티 작성**

```kotlin
// VoiceEntry.kt
package com.ultron.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Category {
    TODO,       // 할일
    SCHEDULE,   // 일정
    ELN,        // 실험로그
    CHAT        // 대화/조언
}

enum class SendStatus {
    PENDING,    // 전송 대기
    SENT,       // 전송 완료
    FAILED      // 전송 실패
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
```

**Step 2: DAO 작성**

```kotlin
// VoiceEntryDao.kt
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
```

**Step 3: Database 클래스 작성**

```kotlin
// UltronDatabase.kt
package com.ultron.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VoiceEntry::class], version = 1, exportSchema = false)
abstract class UltronDatabase : RoomDatabase() {
    abstract fun voiceEntryDao(): VoiceEntryDao
}
```

**Step 4: Hilt DI 모듈 작성**

```kotlin
// DatabaseModule.kt
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
```

**Step 5: 유닛 테스트 작성**

```kotlin
// VoiceEntryTest.kt
package com.ultron.app.data.local

import org.junit.Assert.*
import org.junit.Test

class VoiceEntryTest {
    @Test
    fun `새 VoiceEntry 기본 상태는 PENDING`() {
        val entry = VoiceEntry(transcript = "내일 회의 잡아줘")
        assertEquals(SendStatus.PENDING, entry.sendStatus)
        assertNull(entry.categoryHint)
        assertNull(entry.sentAt)
        assertEquals(0, entry.retryCount)
    }

    @Test
    fun `카테고리 힌트 지정 가능`() {
        val entry = VoiceEntry(
            transcript = "시약 A 10ml 투입",
            categoryHint = Category.ELN
        )
        assertEquals(Category.ELN, entry.categoryHint)
    }
}
```

**Step 6: 테스트 실행 확인**

```bash
./gradlew test --tests "com.ultron.app.data.local.VoiceEntryTest"
```

**Step 7: 커밋**

```bash
git add app/src/main/java/com/ultron/app/data/ app/src/test/
git commit -m "feat: Room DB 엔티티, DAO, DI 모듈 구현

VoiceEntry(전사 텍스트, 카테고리, 전송 상태) + 오프라인 큐 쿼리."
```

---

## Task 2: Telegram Bot API 클라이언트

**Files:**
- Create: `app/src/main/java/com/ultron/app/data/remote/TelegramApi.kt`
- Create: `app/src/main/java/com/ultron/app/data/remote/TelegramModels.kt`
- Create: `app/src/main/java/com/ultron/app/data/di/NetworkModule.kt`
- Create: `app/src/main/java/com/ultron/app/data/repository/MessageRepository.kt`
- Test: `app/src/test/java/com/ultron/app/data/repository/MessageRepositoryTest.kt`

**Step 1: Telegram API 모델 작성**

```kotlin
// TelegramModels.kt
package com.ultron.app.data.remote

data class TelegramResponse(
    val ok: Boolean,
    val result: TelegramMessage?
)

data class TelegramMessage(
    val message_id: Int,
    val text: String?
)

data class SendMessageRequest(
    val chat_id: String,
    val text: String,
    val parse_mode: String = "Markdown"
)
```

**Step 2: Retrofit 인터페이스 작성**

```kotlin
// TelegramApi.kt
package com.ultron.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface TelegramApi {
    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Body request: SendMessageRequest
    ): Response<TelegramResponse>
}
```

**Step 3: Network DI 모듈 작성**

```kotlin
// NetworkModule.kt
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
```

**Step 4: MessageRepository 작성**

```kotlin
// MessageRepository.kt
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
```

**Step 5: 유닛 테스트 작성**

```kotlin
// MessageRepositoryTest.kt
package com.ultron.app.data.repository

import com.ultron.app.data.local.*
import com.ultron.app.data.remote.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class MessageRepositoryTest {
    private lateinit var telegramApi: TelegramApi
    private lateinit var dao: VoiceEntryDao
    private lateinit var repo: MessageRepository

    @Before
    fun setup() {
        telegramApi = mockk()
        dao = mockk(relaxed = true)
        repo = MessageRepository(telegramApi, dao)
    }

    @Test
    fun `전송 성공 시 상태 SENT 업데이트`() = runTest {
        val entry = VoiceEntry(id = 1, transcript = "테스트")
        coEvery { telegramApi.sendMessage(any(), any()) } returns
            Response.success(TelegramResponse(ok = true, result = null))

        val result = repo.sendToTelegram(entry, "token", "chatId")

        assertTrue(result)
        coVerify { dao.updateStatus(1, SendStatus.SENT, any()) }
    }

    @Test
    fun `전송 실패 시 상태 FAILED 업데이트`() = runTest {
        val entry = VoiceEntry(id = 1, transcript = "테스트")
        coEvery { telegramApi.sendMessage(any(), any()) } throws RuntimeException("네트워크 오류")

        val result = repo.sendToTelegram(entry, "token", "chatId")

        assertFalse(result)
        coVerify { dao.updateStatus(1, SendStatus.FAILED, null) }
    }

    @Test
    fun `카테고리 힌트가 메시지에 포함됨`() = runTest {
        val entry = VoiceEntry(id = 1, transcript = "시약 투입", categoryHint = Category.ELN)
        val slot = slot<SendMessageRequest>()
        coEvery { telegramApi.sendMessage(any(), capture(slot)) } returns
            Response.success(TelegramResponse(ok = true, result = null))

        repo.sendToTelegram(entry, "token", "chatId")

        assertTrue(slot.captured.text.startsWith("[ELN]"))
    }
}
```

**Step 6: 테스트 실행**

```bash
./gradlew test --tests "com.ultron.app.data.repository.MessageRepositoryTest"
```

**Step 7: 커밋**

```bash
git add app/src/main/java/com/ultron/app/data/remote/ app/src/main/java/com/ultron/app/data/repository/ app/src/main/java/com/ultron/app/data/di/NetworkModule.kt app/src/test/
git commit -m "feat: Telegram Bot API 클라이언트 + MessageRepository 구현

Retrofit 기반 sendMessage, 오프라인 큐 재전송, 카테고리 힌트 태그 포함."
```

---

## Task 3: 키워드 프리필터 (카테고리 분류)

**Files:**
- Create: `app/src/main/java/com/ultron/app/domain/CategoryClassifier.kt`
- Test: `app/src/test/java/com/ultron/app/domain/CategoryClassifierTest.kt`

**Step 1: 테스트 먼저 작성**

```kotlin
// CategoryClassifierTest.kt
package com.ultron.app.domain

import com.ultron.app.data.local.Category
import org.junit.Assert.*
import org.junit.Test

class CategoryClassifierTest {
    private val classifier = CategoryClassifier()

    @Test
    fun `일정 키워드 감지 - 미팅`() {
        val result = classifier.classify("내일 3시에 팀 미팅 잡아줘")
        assertEquals(Category.SCHEDULE, result.category)
        assertTrue(result.confidence > 0.7)
    }

    @Test
    fun `할일 키워드 감지 - 해줘`() {
        val result = classifier.classify("보고서 초안 작성해줘")
        assertEquals(Category.TODO, result.category)
        assertTrue(result.confidence > 0.5)
    }

    @Test
    fun `실험로그 키워드 감지 - 시약`() {
        val result = classifier.classify("시약 A 10ml 투입 완료, 반응 온도 37도")
        assertEquals(Category.ELN, result.category)
        assertTrue(result.confidence > 0.7)
    }

    @Test
    fun `패턴 없으면 CHAT`() {
        val result = classifier.classify("오늘 날씨 어떻게 생각해?")
        assertEquals(Category.CHAT, result.category)
        assertTrue(result.confidence < 0.5)
    }

    @Test
    fun `복합 문장은 첫 번째 강한 힌트 우선`() {
        val result = classifier.classify("내일 회의 있고 보고서도 써야 해")
        assertEquals(Category.SCHEDULE, result.category)
    }
}
```

**Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "com.ultron.app.domain.CategoryClassifierTest"
# Expected: FAIL (class not found)
```

**Step 3: CategoryClassifier 구현**

```kotlin
// CategoryClassifier.kt
package com.ultron.app.domain

import com.ultron.app.data.local.Category

data class ClassificationResult(
    val category: Category,
    val confidence: Float
)

class CategoryClassifier {
    private data class Pattern(val keywords: List<String>, val category: Category, val weight: Float)

    private val patterns = listOf(
        Pattern(listOf("미팅", "회의", "약속", "예약", "시에", "시부터"), Category.SCHEDULE, 0.8f),
        Pattern(listOf("실험", "배치", "시약", "반응", "수율", "순도", "샘플", "분석"), Category.ELN, 0.8f),
        Pattern(listOf("해야", "해줘", "잊지 마", "사야", "할 일", "처리", "확인해"), Category.TODO, 0.6f),
        Pattern(listOf("어떻게 생각", "조언", "알려줘", "뭐야", "왜"), Category.CHAT, 0.3f),
    )

    // 날짜/시간 패턴 (일정 강화)
    private val dateTimeRegex = Regex(
        "(내일|모레|다음 ?주|오늘|월요일|화요일|수요일|목요일|금요일|토요일|일요일|" +
        "\\d{1,2}월|\\d{1,2}일|\\d{1,2}시|오전|오후)"
    )

    fun classify(text: String): ClassificationResult {
        val scores = mutableMapOf<Category, Float>()

        for (pattern in patterns) {
            val matchCount = pattern.keywords.count { text.contains(it) }
            if (matchCount > 0) {
                val score = pattern.weight * (matchCount.toFloat() / pattern.keywords.size + 0.5f)
                scores[pattern.category] = (scores[pattern.category] ?: 0f) + score
            }
        }

        // 날짜/시간 패턴이 있으면 일정 점수 부스트
        if (dateTimeRegex.containsMatchIn(text)) {
            scores[Category.SCHEDULE] = (scores[Category.SCHEDULE] ?: 0f) + 0.5f
        }

        val best = scores.maxByOrNull { it.value }
        return if (best != null && best.value > 0.3f) {
            ClassificationResult(best.key, best.value.coerceAtMost(1.0f))
        } else {
            ClassificationResult(Category.CHAT, 0.2f)
        }
    }
}
```

**Step 4: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "com.ultron.app.domain.CategoryClassifierTest"
# Expected: ALL PASS
```

**Step 5: 커밋**

```bash
git add app/src/main/java/com/ultron/app/domain/ app/src/test/java/com/ultron/app/domain/
git commit -m "feat: 키워드 기반 카테고리 프리필터 구현

할일/일정/실험로그/대화 분류. 날짜 패턴 부스트. TDD."
```

---

## Task 4: Foreground Service + Vosk 웨이크워드

**Files:**
- Create: `app/src/main/java/com/ultron/app/service/UltronListenerService.kt`
- Create: `app/src/main/java/com/ultron/app/service/WakeWordDetector.kt`
- Create: `app/src/main/java/com/ultron/app/service/NotificationHelper.kt`

**Step 1: NotificationHelper 작성**

```kotlin
// NotificationHelper.kt
package com.ultron.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ultron.app.R

object NotificationHelper {
    const val CHANNEL_ID = "ultron_listener"
    const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ultron 음성 대기",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "울트론 웨이크워드 감지 중"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildListeningNotification(context: Context, status: String = "대기 중"): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Ultron")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
```

**Step 2: WakeWordDetector 작성**

```kotlin
// WakeWordDetector.kt
package com.ultron.app.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

class WakeWordDetector(private val context: Context) {
    private var model: Model? = null
    private var speechService: SpeechService? = null

    private val _wakeWordDetected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wakeWordDetected: SharedFlow<Unit> = _wakeWordDetected

    private val wakeWord = "울트론"

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "vosk-model-small-ko")
        if (modelDir.exists()) {
            model = Model(modelDir.absolutePath)
        }
    }

    fun startListening() {
        val currentModel = model ?: return
        val recognizer = Recognizer(currentModel, 16000.0f)

        speechService = SpeechService(recognizer, 16000.0f).apply {
            startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let {
                        if (it.contains(wakeWord, ignoreCase = true)) {
                            _wakeWordDetected.tryEmit(Unit)
                        }
                    }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        if (it.contains(wakeWord, ignoreCase = true)) {
                            _wakeWordDetected.tryEmit(Unit)
                        }
                    }
                }

                override fun onFinalResult(hypothesis: String?) {}
                override fun onError(exception: Exception?) {}
                override fun onTimeout() {}
            })
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
    }

    fun release() {
        stopListening()
        model?.close()
        model = null
    }
}
```

**Step 3: UltronListenerService 작성**

```kotlin
// UltronListenerService.kt
package com.ultron.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.ultron.app.data.local.VoiceEntry
import com.ultron.app.data.local.VoiceEntryDao
import com.ultron.app.data.repository.MessageRepository
import com.ultron.app.domain.CategoryClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class UltronListenerService : Service() {
    @Inject lateinit var voiceEntryDao: VoiceEntryDao
    @Inject lateinit var messageRepository: MessageRepository

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val classifier = CategoryClassifier()
    private var wakeWordDetector: WakeWordDetector? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false

    // STT 결과 축적 (토글 모드용)
    private val transcriptBuffer = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        startForeground(NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.buildListeningNotification(this))

        scope.launch {
            wakeWordDetector = WakeWordDetector(this@UltronListenerService).apply {
                initialize()
                startListening()
                wakeWordDetected.collectLatest {
                    if (!isRecording) startSTT()
                }
            }
        }
    }

    fun startSTT() {
        if (isRecording) return
        isRecording = true
        transcriptBuffer.clear()

        updateNotification("🔴 녹음 중...")

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { text ->
                        transcriptBuffer.append(text).append(" ")

                        // "끝" 키워드 감지 → 종료
                        if (text.trim().endsWith("끝") || text.trim() == "끝") {
                            val finalText = transcriptBuffer.toString()
                                .replace("끝$".toRegex(), "").trim()
                            finishRecording(finalText)
                        } else {
                            // 연속 인식 모드: 다시 시작
                            startRecognizerIntent()
                        }
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {}

                override fun onError(error: Int) {
                    // 타임아웃 등 → 연속 모드에서 다시 시작
                    if (isRecording && error == SpeechRecognizer.ERROR_NO_MATCH) {
                        startRecognizerIntent()
                    }
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }

        startRecognizerIntent()
    }

    private fun startRecognizerIntent() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 30000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 30000L)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopSTT() {
        if (!isRecording) return
        val finalText = transcriptBuffer.toString().trim()
        if (finalText.isNotEmpty()) {
            finishRecording(finalText)
        } else {
            isRecording = false
            updateNotification("대기 중")
        }
    }

    private fun finishRecording(text: String) {
        isRecording = false
        speechRecognizer?.stopListening()
        updateNotification("대기 중")

        scope.launch {
            val classification = classifier.classify(text)
            val entry = VoiceEntry(
                transcript = text,
                categoryHint = classification.category
            )
            voiceEntryDao.insert(entry)

            // 즉시 전송 시도 (온라인이면)
            try {
                val botToken = getBotToken()
                val chatId = getChatId()
                if (botToken.isNotEmpty() && chatId.isNotEmpty()) {
                    messageRepository.sendToTelegram(
                        entry.copy(id = voiceEntryDao.getPendingEntries().last().id),
                        botToken, chatId
                    )
                }
            } catch (_: Exception) {
                // 오프라인 → 큐에 남아있음, WorkManager가 나중에 처리
            }
        }
    }

    private fun getBotToken(): String = com.ultron.app.BuildConfig.TELEGRAM_BOT_TOKEN
    private fun getChatId(): String = com.ultron.app.BuildConfig.TELEGRAM_CHAT_ID

    private fun updateNotification(status: String) {
        val notification = NotificationHelper.buildListeningNotification(this, status)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        wakeWordDetector?.release()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
```

**Step 4: 커밋**

```bash
git add app/src/main/java/com/ultron/app/service/
git commit -m "feat: Foreground Service + Vosk 웨이크워드 + 토글 STT

웨이크워드 '울트론' 감지 → 토글 STT 시작.
종료: '끝' 키워드 / 수동 버튼 / 버즈 터치.
연속 인식 모드로 타임아웃 없이 녹음."
```

---

## Task 5: Bluetooth Buds 3 Pro 연동

**Files:**
- Create: `app/src/main/java/com/ultron/app/bluetooth/BudsController.kt`

**Step 1: BudsController 작성**

```kotlin
// BudsController.kt
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

    // 미디어 버튼 이벤트 수신 (버즈 터치 → 녹음 토글)
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

        val filter = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
        filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        context.registerReceiver(mediaButtonReceiver, filter)
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
        try { context.unregisterReceiver(mediaButtonReceiver) } catch (_: Exception) {}
        stopScoAudio()
        bluetoothHeadset?.let {
            BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HEADSET, it)
        }
    }
}
```

**Step 2: 커밋**

```bash
git add app/src/main/java/com/ultron/app/bluetooth/
git commit -m "feat: Galaxy Buds 3 Pro Bluetooth SCO 연동

미디어 버튼 이벤트로 녹음 토글. SCO 오디오 채널 제어."
```

---

## Task 6: WorkManager 오프라인 큐 재전송

**Files:**
- Create: `app/src/main/java/com/ultron/app/worker/RetryWorker.kt`
- Create: `app/src/main/java/com/ultron/app/receiver/BootReceiver.kt`

**Step 1: RetryWorker 작성**

```kotlin
// RetryWorker.kt
package com.ultron.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ultron.app.BuildConfig
import com.ultron.app.data.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sent = messageRepository.retrySendPending(
            BuildConfig.TELEGRAM_BOT_TOKEN,
            BuildConfig.TELEGRAM_CHAT_ID
        )
        return if (sent >= 0) Result.success() else Result.retry()
    }

    companion object {
        fun enqueuePeriodicRetry(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RetryWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ultron_retry",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

**Step 2: BootReceiver 작성**

```kotlin
// BootReceiver.kt
package com.ultron.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ultron.app.service.UltronListenerService
import com.ultron.app.worker.RetryWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Foreground Service 재시작
            val serviceIntent = Intent(context, UltronListenerService::class.java)
            context.startForegroundService(serviceIntent)

            // 오프라인 큐 재전송 스케줄
            RetryWorker.enqueuePeriodicRetry(context)
        }
    }
}
```

**Step 3: 커밋**

```bash
git add app/src/main/java/com/ultron/app/worker/ app/src/main/java/com/ultron/app/receiver/
git commit -m "feat: WorkManager 오프라인 큐 재전송 + 부팅 시 자동 시작

15분 간격 재시도. 네트워크 연결 시에만 전송. 부팅 후 서비스 자동 시작."
```

---

## Task 7: Jetpack Compose UI

**Files:**
- Create: `app/src/main/java/com/ultron/app/ui/MainActivity.kt`
- Create: `app/src/main/java/com/ultron/app/ui/MainViewModel.kt`
- Create: `app/src/main/java/com/ultron/app/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/com/ultron/app/ui/screens/HistoryScreen.kt`
- Create: `app/src/main/java/com/ultron/app/ui/screens/SettingsScreen.kt`
- Create: `app/src/main/java/com/ultron/app/ui/theme/Theme.kt`

**Step 1: Theme 작성**

```kotlin
// Theme.kt
package com.ultron.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun UltronTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```

**Step 2: MainViewModel 작성**

```kotlin
// MainViewModel.kt
package com.ultron.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.app.data.local.VoiceEntryDao
import com.ultron.app.service.UltronListenerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiState(
    val isListening: Boolean = false,
    val isRecording: Boolean = false,
    val pendingCount: Int = 0,
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

    fun startService() {
        val intent = Intent(app, UltronListenerService::class.java)
        app.startForegroundService(intent)
        _uiState.update { it.copy(isListening = true) }
    }

    fun stopService() {
        val intent = Intent(app, UltronListenerService::class.java)
        app.stopService(intent)
        _uiState.update { it.copy(isListening = false) }
    }

    fun toggleRecording() {
        _uiState.update { it.copy(isRecording = !it.isRecording) }
        // Service에 녹음 시작/종료 명령 전달 (Binder 또는 Broadcast)
    }
}
```

**Step 3: HomeScreen 작성**

```kotlin
// HomeScreen.kt
package com.ultron.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultron.app.ui.UiState

@Composable
fun HomeScreen(
    uiState: UiState,
    pendingCount: Int,
    onToggleService: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 상태 표시
        Text(
            text = if (uiState.isRecording) "녹음 중..." else if (uiState.isListening) "\"울트론\" 대기 중" else "중지됨",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 버즈 연결 상태
        Text(
            text = if (uiState.budsConnected) "Buds 3 Pro 연결됨" else "Buds 미연결",
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.budsConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 녹음 토글 버튼
        val buttonColor by animateColorAsState(
            if (uiState.isRecording) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            label = "button_color"
        )

        FilledIconButton(
            onClick = onToggleRecording,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = buttonColor),
            enabled = uiState.isListening
        ) {
            Icon(
                imageVector = if (uiState.isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "녹음 토글",
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 서비스 시작/중지
        OutlinedButton(onClick = onToggleService) {
            Text(if (uiState.isListening) "서비스 중지" else "서비스 시작")
        }

        // 대기 중인 메시지
        if (pendingCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "전송 대기: ${pendingCount}건",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
```

**Step 4: HistoryScreen 작성**

```kotlin
// HistoryScreen.kt
package com.ultron.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultron.app.data.local.Category
import com.ultron.app.data.local.SendStatus
import com.ultron.app.data.local.VoiceEntry
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(entries: List<VoiceEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            VoiceEntryCard(entry)
        }
    }
}

@Composable
private fun VoiceEntryCard(entry: VoiceEntry) {
    val categoryLabel = when (entry.categoryHint) {
        Category.TODO -> "할일"
        Category.SCHEDULE -> "일정"
        Category.ELN -> "실험"
        Category.CHAT -> "대화"
        null -> "미분류"
    }

    val statusIcon = when (entry.sendStatus) {
        SendStatus.SENT -> "✅"
        SendStatus.PENDING -> "⏳"
        SendStatus.FAILED -> "❌"
    }

    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[$categoryLabel] $statusIcon",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = dateFormat.format(Date(entry.createdAt)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.transcript,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

**Step 5: MainActivity 작성**

```kotlin
// MainActivity.kt
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
    ) { /* 권한 결과 처리 */ }

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
                        1 -> HistoryScreen(entries)
                        2 -> { /* SettingsScreen — Task 8에서 구현 */ }
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
```

**Step 6: HomeScreen에 modifier 파라미터 추가**

HomeScreen 함수 시그니처에 `modifier: Modifier = Modifier` 추가하고 최외부 Column에 적용.

**Step 7: 커밋**

```bash
git add app/src/main/java/com/ultron/app/ui/
git commit -m "feat: Jetpack Compose UI — 홈/기록 화면 구현

Material 3 다이나믹 컬러. 녹음 토글 버튼, 히스토리 카드, 하단 네비게이션."
```

---

## Task 8: 설정 화면 + Vosk 모델 다운로드

**Files:**
- Create: `app/src/main/java/com/ultron/app/ui/screens/SettingsScreen.kt`
- Create: `app/src/main/java/com/ultron/app/data/local/SettingsStore.kt`

**Step 1: SettingsStore 작성 (DataStore)**

```kotlin
// SettingsStore.kt
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
```

**Step 2: SettingsScreen 작성**

```kotlin
// SettingsScreen.kt
package com.ultron.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    wakeWordEnabled: Boolean,
    voskDownloaded: Boolean,
    onWakeWordToggle: (Boolean) -> Unit,
    onDownloadVosk: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        // 웨이크워드 토글
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("웨이크워드 감지", style = MaterialTheme.typography.titleMedium)
                Text("\"울트론\"이라고 말하면 녹음 시작", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = wakeWordEnabled,
                onCheckedChange = onWakeWordToggle,
                enabled = voskDownloaded
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vosk 모델 다운로드
        if (!voskDownloaded) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("한국어 음성 모델 필요", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "웨이크워드 감지를 위해 Vosk 한국어 모델(~50MB)을 다운로드해야 합니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDownloadVosk) {
                        Text("다운로드")
                    }
                }
            }
        } else {
            Text("✅ 한국어 음성 모델 설치됨", color = MaterialTheme.colorScheme.primary)
        }
    }
}
```

**Step 3: 커밋**

```bash
git add app/src/main/java/com/ultron/app/ui/screens/SettingsScreen.kt app/src/main/java/com/ultron/app/data/local/SettingsStore.kt
git commit -m "feat: 설정 화면 + DataStore + Vosk 모델 다운로드 UI"
```

---

## Task 9: GitHub Actions CI/CD (APK 자동 빌드)

**Files:**
- Create: `.github/workflows/build-apk.yml`

**Step 1: 워크플로우 작성**

```yaml
# .github/workflows/build-apk.yml
name: Build APK

on:
  push:
    tags: ['v*']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build Release APK
        run: ./gradlew assembleRelease
        env:
          TELEGRAM_BOT_TOKEN: ${{ secrets.TELEGRAM_BOT_TOKEN }}
          TELEGRAM_CHAT_ID: ${{ secrets.TELEGRAM_CHAT_ID }}

      - name: Upload APK to Release
        uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/*.apk
```

**Step 2: 커밋**

```bash
git add .github/
git commit -m "ci: GitHub Actions APK 자동 빌드

태그 push 시 Release APK 빌드 → GitHub Releases 업로드."
```

---

## Task 10: Notion 🧪 실험 노트 DB 생성 + OpenClaw 프롬프트 설정

**Step 1: Notion 🧪 실험 노트 DB 생성**

Claude Code Notion MCP로 실험 노트 DB 생성 (🏠 홈 대시보드 하위):
- 실험명 (title), 실험번호 (text), 날짜 (date)
- 카테고리 (select: 분석/제형/안정성/기타)
- 시약/재료 (text), 조건 (text), 결과 (text)
- 상태 (select: 진행중/완료/실패/보류)
- 프로젝트 (relation → 📁 프로젝트 DB)
- 메모 (text)

**Step 2: OpenClaw 시스템 프롬프트 업데이트**

Mac Mini OpenClaw에 시스템 프롬프트 설정:

```
당신은 개인 AI 비서 "울트론"입니다. 입력된 한국어 음성 전사 텍스트를 분석하여:

1. **[TODO]** 태그 → Notion ✅ 작업 DB에 추가
   - 작업명, 우선순위(음성에서 추론), 카테고리(회사/개인/가족)
   - 마감일이 언급되면 설정

2. **[SCHEDULE]** 태그 → Notion 📅 일정 DB + Google Calendar에 동시 등록
   - 일정명, 날짜/시간, 종류(병원/개인/회사)

3. **[ELN]** 태그 → Notion 🧪 실험 노트 DB에 추가
   - 실험명, 시약/재료, 조건, 결과 각 필드 파싱
   - 카테고리: 분석/제형/안정성/기타

4. **[CHAT]** 태그 또는 태그 없음 → 질문으로 간주, 답변만 Telegram으로 전송

태그가 없으면 텍스트 내용을 분석하여 자동 분류.
처리 결과를 Telegram으로 요약 답장.
현재 날짜/시간 기준으로 상대적 표현("내일", "다음 주") 해석.
```

**Step 3: 커밋 (문서 업데이트)**

```bash
git commit -m "docs: OpenClaw 시스템 프롬프트 4-카테고리 분류 업데이트"
```

---

## Task 11: 통합 테스트 + 태그 푸시

**Step 1: 로컬 빌드 확인**

```bash
./gradlew assembleDebug
```

**Step 2: S23 Ultra에 디버그 APK 설치**

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Step 3: E2E 테스트 체크리스트**

- [ ] 앱 실행 → 서비스 시작 → 알림바에 "대기 중" 표시
- [ ] "울트론" → 녹음 시작 → 말하기 → "끝" → Telegram 메시지 전송
- [ ] 앱 버튼 터치 → 녹음 시작/종료 → Telegram 전송
- [ ] 버즈3 프로 터치 → 녹음 토글
- [ ] 비행기 모드 → 음성 입력 → 큐 저장 → 비행기 모드 해제 → 자동 전송
- [ ] OpenClaw 할일 분류 → Notion ✅ 작업 DB 등록 확인
- [ ] OpenClaw 일정 분류 → Notion 📅 일정 DB + Google Calendar 등록 확인
- [ ] OpenClaw 실험 분류 → Notion 🧪 실험 노트 DB 등록 확인
- [ ] OpenClaw 대화 → Telegram 답장만 확인

**Step 4: 첫 릴리스 태그**

```bash
git tag v1.0.0
git push origin v1.0.0
# → GitHub Actions가 Release APK 빌드
```

---

## 의존성 그래프

```
Task 0 (스캐폴딩)
  ├── Task 1 (Room DB)
  │     └── Task 2 (Telegram API + Repository)
  │           └── Task 6 (WorkManager 재전송)
  ├── Task 3 (카테고리 분류) ← 독립
  ├── Task 4 (Foreground Service + Vosk) ← Task 1, 2 필요
  ├── Task 5 (Bluetooth 버즈) ← 독립
  ├── Task 7 (Compose UI) ← Task 1, 4, 5 필요
  ├── Task 8 (설정 화면) ← Task 7 필요
  ├── Task 9 (CI/CD) ← 독립
  ├── Task 10 (Notion DB + OpenClaw) ← 독립
  └── Task 11 (통합 테스트) ← 전체 완료 후
```

**병렬 가능 그룹:**
- 그룹 A: Task 1 → Task 2 → Task 6 (데이터 레이어)
- 그룹 B: Task 3 (분류 로직)
- 그룹 C: Task 5 (블루투스)
- 그룹 D: Task 9 (CI/CD)
- 그룹 E: Task 10 (Notion + OpenClaw)
