package com.ultron.app.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.ultron.app.BuildConfig
import com.ultron.app.bluetooth.BudsController
import com.ultron.app.data.local.VoiceEntry
import com.ultron.app.data.local.VoiceEntryDao
import com.ultron.app.data.repository.MessageRepository
import com.ultron.app.domain.CategoryClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class UltronListenerService : Service() {
    @Inject lateinit var voiceEntryDao: VoiceEntryDao
    @Inject lateinit var messageRepository: MessageRepository

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val classifier = CategoryClassifier()
    private var wakeWordDetector: WakeWordDetector? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var budsController: BudsController? = null
    private var tts: TextToSpeech? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _budsConnected = MutableStateFlow(false)
    val budsConnected: StateFlow<Boolean> = _budsConnected

    private val transcriptBuffer = StringBuilder()

    inner class LocalBinder : Binder() {
        fun getService(): UltronListenerService = this@UltronListenerService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        startForeground(NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.buildListeningNotification(this))

        // TTS 초기화
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
            }
        }

        // 버즈 컨트롤러 초기화
        budsController = BudsController(this).apply {
            initialize()
            setOnMediaButton { toggleRecording() }
            scope.launch {
                isConnected.collectLatest { connected ->
                    _budsConnected.value = connected
                }
            }
        }

        // 웨이크워드 감지 시작
        scope.launch {
            wakeWordDetector = WakeWordDetector(this@UltronListenerService).apply {
                initialize()
                if (isModelAvailable()) {
                    startListening()
                    wakeWordDetected.collectLatest {
                        if (!_isRecording.value) startSTT()
                    }
                }
            }
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) stopSTT() else startSTT()
    }

    fun startSTT() {
        if (_isRecording.value) return
        _isRecording.value = true
        transcriptBuffer.clear()

        // 버즈 SCO 시작
        budsController?.startScoAudio()
        updateNotification("녹음 중...")

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

                        if (text.trim().endsWith("끝") || text.trim() == "끝") {
                            val finalText = transcriptBuffer.toString()
                                .replace("끝\\s*$".toRegex(), "").trim()
                            finishRecording(finalText)
                        } else {
                            startRecognizerIntent()
                        }
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {}

                override fun onError(error: Int) {
                    if (_isRecording.value && error == SpeechRecognizer.ERROR_NO_MATCH) {
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
        if (!_isRecording.value) return
        val finalText = transcriptBuffer.toString().trim()
        if (finalText.isNotEmpty()) {
            finishRecording(finalText)
        } else {
            _isRecording.value = false
            budsController?.stopScoAudio()
            updateNotification("대기 중")
        }
    }

    private fun finishRecording(text: String) {
        _isRecording.value = false
        speechRecognizer?.stopListening()
        budsController?.stopScoAudio()
        updateNotification("대기 중")

        scope.launch {
            val classification = classifier.classify(text)
            val entry = VoiceEntry(
                transcript = text,
                categoryHint = classification.category
            )
            val id = voiceEntryDao.insert(entry)

            try {
                val botToken = BuildConfig.TELEGRAM_BOT_TOKEN
                val chatId = BuildConfig.TELEGRAM_CHAT_ID
                if (botToken.isNotEmpty() && chatId.isNotEmpty()) {
                    messageRepository.sendToTelegram(entry.copy(id = id), botToken, chatId)
                }
            } catch (_: Exception) {
                // 오프라인 → 큐에 남아있음
            }
        }
    }

    private fun updateNotification(status: String) {
        val notification = NotificationHelper.buildListeningNotification(this, status)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        wakeWordDetector?.release()
        speechRecognizer?.destroy()
        budsController?.release()
        tts?.shutdown()
        super.onDestroy()
    }
}
