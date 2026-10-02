package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Speech-to-text and text-to-speech using the phone's own engines (the normal system voice). */
class Voice(private val ctx: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending = 0
    private var onSpeechIdle: (() -> Unit)? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { finishOne() }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { finishOne() }
            })
        }
    }

    val busy get() = pending > 0

    private fun finishOne() { pending = (pending - 1).coerceAtLeast(0); if (pending == 0) onSpeechIdle?.invoke() }

    fun listen(lang: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onFail: () -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onFail(); return }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) { onFail() }
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) onFail() else onFinal(t)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "fa") "fa-IR" else Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        r.startListening(i)
    }

    fun stopListening() { recognizer?.stopListening() }

    /** Queue one chunk of speech. [idle] fires when everything queued has been spoken. */
    fun speak(text: String, lang: String, idle: () -> Unit) {
        if (!ttsReady || text.isBlank()) { idle(); return }
        onSpeechIdle = idle
        tts?.language = if (lang == "fa") Locale.forLanguageTag("fa-IR") else Locale.getDefault()
        pending++
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "j${System.nanoTime()}")
    }

    fun stopSpeaking() { tts?.stop(); pending = 0 }

    fun release() { recognizer?.destroy(); tts?.shutdown() }
}
