package com.example.iam

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.webkit.JavascriptInterface
import java.util.ArrayDeque
import java.util.Locale

class TTSInterface(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private val TAG = "TTSInterface"

    // Cola para textos que lleguen antes de que TTS esté listo
    private val pendingQueue = ArrayDeque<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var utteranceCounter = 0

    init {
        Log.d(TAG, "Initializing TextToSpeech")
        // Use applicationContext to avoid holding a reference to the Activity context
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Use Locale.forLanguageTag to avoid deprecated Locale constructor
            val spanishLocale = Locale.forLanguageTag("es-ES")
            val result = tts?.setLanguage(spanishLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "The selected language is not supported!")
                // As a fallback, try device's default language
                val defaultResult = tts?.setLanguage(Locale.getDefault())
                if (defaultResult == TextToSpeech.LANG_MISSING_DATA || defaultResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Default language is also not supported!")
                } else {
                    isTtsInitialized = true
                    Log.d(TAG, "TextToSpeech Initialized successfully with default locale.")
                }
            } else {
                isTtsInitialized = true
                Log.d(TAG, "TextToSpeech Initialized successfully with es-ES.")
            }

            // Añadir listener para logging de eventos de reproducción
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Utterance started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Utterance done: $utteranceId")
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Utterance error: $utteranceId")
                }
            })

            // Vaciar la cola de textos pendientes
            mainHandler.post {
                synchronized(pendingQueue) {
                    while (pendingQueue.isNotEmpty()) {
                        val t = pendingQueue.removeFirst()
                        Log.d(TAG, "Flushing queued text: '$t'")
                        tts?.speak(t, TextToSpeech.QUEUE_ADD, null, nextUtteranceId())
                    }
                }
            }
        } else {
            Log.e(TAG, "TextToSpeech Initialization Failed! Status: $status")
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun speak(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "Speak called with blank text.")
            return
        }

        if (!isTtsInitialized) {
            Log.d(TAG, "TTS not initialized yet - enqueuing text: '$text'")
            synchronized(pendingQueue) {
                pendingQueue.addLast(text)
            }
            return
        }

        Log.d(TAG, "Queuing speech for text: '$text'")
        // Speak immediately, flushing previous speech
        mainHandler.post {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, nextUtteranceId())
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun stop() {
        Log.d(TAG, "stop() called from JS")
        // limpiar cola pendiente y detener reproducción en curso
        synchronized(pendingQueue) {
            pendingQueue.clear()
        }
        mainHandler.post {
            tts?.stop()
        }
    }

    fun shutdown() {
        Log.d(TAG, "shutdown() called")
        synchronized(pendingQueue) {
            pendingQueue.clear()
        }
        mainHandler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsInitialized = false
        }
    }

    private fun nextUtteranceId(): String {
        utteranceCounter += 1
        return "webview_tts_" + utteranceCounter
    }
}
