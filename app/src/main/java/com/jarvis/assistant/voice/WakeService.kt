package com.jarvis.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.data.Prefs

/**
 * Always-on listener (foreground service). Saves battery by: preferring offline recognition, pausing while the app
 * is busy, pausing when the screen is off (optional), and backing off after errors. On the name it opens the app.
 */
class WakeService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var running = false
    private var screenOn = true
    private var fails = 0
    private var preferOffline = true
    private lateinit var prefs: Prefs

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            screenOn = i.action == Intent.ACTION_SCREEN_ON
            if (screenOn) schedule(300) else rec?.cancel()
        }
    }
    private val tick = Runnable {
        if (!running) return@Runnable
        if (Wake.busy || (prefs.wakeScreenOnly && !screenOn)) { schedule(1000); return@Runnable }
        listen()
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") { prefs.wakeOn = false; stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        val fa = prefs.lang == "fa"
        val name = prefs.assistantName.ifBlank { if (fa) "جارویس" else "Jarvis" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("wake", "Listening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, "wake").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (fa) "$name گوش می‌ده" else "$name is listening")
            .setContentText(if (fa) "اسمم رو صدا بزن" else "Say my name")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, if (fa) "خاموش" else "Stop", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(7, n)
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        registerReceiver(screenRx, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        running = true
        schedule(300)
    }

    private fun schedule(ms: Long) { h.removeCallbacks(tick); h.postDelayed(tick, ms) }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { stopSelf(); return }
        if (rec == null) rec = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (prefs.lang == "fa") "fa-IR" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        try { rec?.startListening(i) } catch (e: Throwable) { rec?.destroy(); rec = null; schedule(3000) }
    }

    private fun heard(b: Bundle?): Boolean {
        val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        val name = prefs.assistantName
        if (l.none { Wake.matches(it, name) }) return false
        rec?.cancel()
        Wake.busy = true
        h.postDelayed({ Wake.busy = false }, 30_000)   // safety: never stay silent forever
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("wake", true))
        } catch (e: Throwable) { Wake.busy = false }
        schedule(2000)
        return true
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) { heard(b) }
        override fun onResults(b: Bundle?) { fails = 0; if (!heard(b)) schedule(150) }
        override fun onError(e: Int) {
            // silence / nothing recognised is NORMAL while waiting for the name: restart right away, no backoff
            if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) { schedule(100); return }
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { stopSelf(); return }
            // 12/13 = language not supported / unavailable offline -> fall back to online recognition
            if (e == 12 || e == 13) preferOffline = false
            fails++
            if (e == SpeechRecognizer.ERROR_CLIENT || e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) { rec?.destroy(); rec = null }
            schedule((300L + fails * 400L).coerceAtMost(4000L))
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        rec?.destroy(); rec = null
        try { unregisterReceiver(screenRx) } catch (e: Throwable) {}
        super.onDestroy()
    }
}
