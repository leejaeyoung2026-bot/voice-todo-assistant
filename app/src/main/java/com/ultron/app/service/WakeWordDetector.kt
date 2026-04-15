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

    fun isModelAvailable(): Boolean {
        val modelDir = File(context.filesDir, "vosk-model-small-ko")
        return modelDir.exists()
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
