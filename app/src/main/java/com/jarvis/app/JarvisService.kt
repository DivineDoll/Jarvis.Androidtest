package com.jarvis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

class JarvisService : Service(), TextToSpeech.OnInitListener {

    private enum class State { WAKE, COMMAND, BUSY }

    private var state = State.WAKE
    private var running = false
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var actions: ActionExecutor
    private val history = mutableListOf<Pair<String, String>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        actions = ActionExecutor(this)
        tts = TextToSpeech(this, this)
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            main.post { setupRecognizer(); listen() }
        }
        return START_STICKY
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return
        tts?.language = Locale.getDefault()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { resumeWake() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { resumeWake() } }
        })
    }

    // ---------- Reconocimiento de voz ----------

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { stopSelf(); return }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: android.os.Bundle?) {}
                override fun onEvent(t: Int, p: android.os.Bundle?) {}

                override fun onResults(results: android.os.Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    handleText(text)
                }

                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        stopSelf(); return
                    }
                    if (state == State.COMMAND) state = State.WAKE
                    val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1000L else 250L
                    main.postDelayed({ recognizer?.cancel(); listen() }, delay)
                }
            })
        }
    }

    private fun listen() {
        if (!running || state == State.BUSY) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        recognizer?.startListening(i)
    }

    private fun resumeWake() {
        state = State.WAKE
        listen()
    }

    // ---------- Lógica ----------

    private fun handleText(text: String) {
        when (state) {
            State.COMMAND -> if (text.isBlank()) resumeWake() else process(text)
            State.WAKE -> {
                val cmd = extractCommand(text)
                when {
                    cmd == null -> listen()
                    cmd.length > 2 -> process(cmd)
                    else -> {
                        state = State.COMMAND
                        try { ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) {}
                        listen()
                    }
                }
            }
            State.BUSY -> {}
        }
    }

    private fun strip(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Devuelve lo que viene después del nombre, o null si no se dijo el nombre. */
    private fun extractCommand(text: String): String? {
        val wake = Prefs.wakeWord(this)
        val names = if (strip(wake) == "jarvis")
            listOf("jarvis", "yarvis", "jarbis", "harvis", "yarvi", "jarvi")
        else listOf(wake)
        val norm = strip(text)
        for (n in names) {
            val k = strip(n)
            val idx = norm.indexOf(k)
            if (idx >= 0) {
                val from = minOf(idx + k.length, text.length)
                return text.substring(from).trim { it.isWhitespace() || it in ",.!?¡¿" }
            }
        }
        return null
    }

    private fun process(cmd: String) {
        state = State.BUSY
        val key = Prefs.apiKey(this)
        if (key.isBlank()) {
            speak("Falta la clave de API. Abre la app y configúrala.")
            return
        }
        val name = Prefs.wakeWord(this)
        val snapshot = history.toList()
        worker.execute {
            val reply = try {
                GeminiClient.ask(key, name, snapshot, cmd)
            } catch (e: Exception) {
                GeminiClient.Reply("No pude conectarme a internet.", null)
            }
            main.post {
                history.add("user" to cmd)
                history.add("assistant" to reply.speech)
                while (history.size > 12) history.removeAt(0)
                actions.run(reply.action)
                speak(reply.speech)
            }
        }
    }

    private fun applyVoice() {
        val t = tts ?: return
        t.setPitch(Prefs.pitch(this))
        t.setSpeechRate(Prefs.rate(this))
        val name = Prefs.voice(this)
        if (name.isNotBlank()) {
            try { t.voices?.firstOrNull { it.name == name }?.let { t.voice = it } } catch (_: Exception) {}
        }
    }

    private fun speak(text: String) {
        val key = Prefs.apiKey(this)
        if (Prefs.geminiOn(this) && key.isNotBlank()) {
            val voice = Prefs.geminiVoice(this)
            worker.execute {
                val audio = try { GeminiTts.synth(key, text, voice) } catch (_: Exception) { null }
                if (audio != null) {
                    try { GeminiTts.play(audio) } catch (_: Exception) {}
                    main.post { resumeWake() }
                } else {
                    main.post { speakLocal(text) }
                }
            }
        } else {
            speakLocal(text)
        }
    }

    private fun speakLocal(text: String) {
        if (!ttsReady) { resumeWake(); return }
        applyVoice()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    // ---------- Notificación en primer plano ----------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, "jarvis")
            .setContentTitle("${Prefs.wakeWord(this)} está escuchando")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts?.shutdown()
        worker.shutdownNow()
        super.onDestroy()
    }
}
