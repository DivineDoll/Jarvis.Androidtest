package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.app.AlertDialog
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.SeekBar
import java.util.Locale
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var wake: EditText
    private lateinit var key: EditText
    private var tts: TextToSpeech? = null
    private var ttsOk = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status -> ttsOk = status == TextToSpeech.SUCCESS }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            gravity = Gravity.TOP
        }

        root.addView(TextView(this).apply { text = "JARVIS"; textSize = 32f })
        status = TextView(this).apply { textSize = 16f; setPadding(0, dp(8), 0, dp(24)) }
        root.addView(status)

        root.addView(TextView(this).apply { text = "Nombre del asistente (palabra de activación)" })
        wake = EditText(this).apply { setText(Prefs.wakeWord(this@MainActivity)); setSingleLine() }
        root.addView(wake)

        root.addView(TextView(this).apply { text = "Clave de API de Google Gemini (aistudio.google.com)"; setPadding(0, dp(16), 0, 0) })
        key = EditText(this).apply {
            setText(Prefs.apiKey(this@MainActivity))
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(key)

        root.addView(Button(this).apply {
            text = "Guardar"
            setOnClickListener {
                Prefs.save(this@MainActivity, wake.text.toString(), key.text.toString())
                Toast.makeText(this@MainActivity, "Guardado", Toast.LENGTH_SHORT).show()
                if (Prefs.enabled(this@MainActivity)) startJarvis()
            }
        })

        root.addView(Button(this).apply {
            text = "Permitir abrir apps en segundo plano"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else {
                    Toast.makeText(this@MainActivity, "Ya está permitido", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(TextView(this).apply { text = "Voz"; textSize = 18f; setPadding(0, dp(24), 0, 0) })
        root.addView(Button(this).apply {
            text = "Elegir voz"
            setOnClickListener { pickVoice() }
        })
        val gBtn = Button(this)
        fun gLabel() {
            gBtn.text = if (Prefs.geminiOn(this@MainActivity)) "Voz de Gemini: activada" else "Voz de Gemini: desactivada"
        }
        gLabel()
        gBtn.setOnClickListener {
            Prefs.setGeminiOn(this@MainActivity, !Prefs.geminiOn(this@MainActivity))
            gLabel()
        }
        root.addView(gBtn)
        root.addView(Button(this).apply {
            text = "Elegir voz de Gemini"
            setOnClickListener { pickGeminiVoice() }
        })
        root.addView(TextView(this).apply { text = "Tono y velocidad (solo voz del teléfono)"; setPadding(0, dp(12), 0, 0) })
        root.addView(TextView(this).apply { text = "Tono (grave a agudo)" })
        root.addView(SeekBar(this).apply {
            max = 100
            progress = ((Prefs.pitch(this@MainActivity) - 0.5f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    Prefs.setPitch(this@MainActivity, 0.5f + p / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        root.addView(TextView(this).apply { text = "Velocidad (lenta a rápida)" })
        root.addView(SeekBar(this).apply {
            max = 100
            progress = ((Prefs.rate(this@MainActivity) - 0.5f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    Prefs.setRate(this@MainActivity, 0.5f + p / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        root.addView(Button(this).apply {
            text = "Probar voz"
            setOnClickListener { testVoice() }
        })

        toggle = Button(this).apply {
            setOnClickListener {
                if (Prefs.enabled(this@MainActivity)) stopJarvis() else startJarvis()
            }
        }
        root.addView(toggle)

        setContentView(root)
        requestPerms()
        refresh()
    }

    private fun testVoice(local: Boolean = false) {
        Prefs.save(this, wake.text.toString(), key.text.toString())
        if (!local && Prefs.geminiOn(this) && Prefs.apiKey(this).isNotBlank()) {
            val k = Prefs.apiKey(this)
            val v = Prefs.geminiVoice(this)
            val phrase = "Hola, soy ${Prefs.wakeWord(this)}. Así sueno."
            Thread {
                val audio = try { GeminiTts.synth(k, phrase, v) } catch (_: Exception) { null }
                if (audio != null) {
                    try { GeminiTts.play(audio) } catch (_: Exception) {}
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "No pude usar la voz de Gemini (revisa la clave o el límite)", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
            return
        }
        val t = tts ?: return
        t.setPitch(Prefs.pitch(this))
        t.setSpeechRate(Prefs.rate(this))
        val name = Prefs.voice(this)
        if (name.isNotBlank()) {
            try { t.voices?.firstOrNull { it.name == name }?.let { t.voice = it } } catch (_: Exception) {}
        }
        t.speak("Hola, soy ${Prefs.wakeWord(this)}. Así sueno.", TextToSpeech.QUEUE_FLUSH, null, "test")
    }

    private fun pickVoice() {
        val t = tts
        if (t == null || !ttsOk) {
            Toast.makeText(this, "El motor de voz del teléfono aún no está listo, intenta de nuevo", Toast.LENGTH_LONG).show()
            return
        }
        val lang = Locale.getDefault().language
        val all = try { (t.voices ?: emptySet()).toList() } catch (_: Exception) { emptyList() }
        val sameLang = all.filter { it.locale.language == lang }
        // si no hay voces de tu idioma, muestra todas las disponibles
        val list = (if (sameLang.isNotEmpty()) sameLang else all).sortedBy { it.name }
        if (list.isEmpty()) {
            Toast.makeText(this, "Tu teléfono no tiene voces instaladas. Instálalas en Ajustes, Salida de texto a voz", Toast.LENGTH_LONG).show()
            return
        }
        val labels = list.mapIndexed { i, v -> "Voz ${i + 1} - ${v.locale.displayName} (${v.name})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Elige una voz")
            .setItems(labels) { _, i ->
                Prefs.setVoice(this, list[i].name)
                testVoice(true)
            }
            .show()
    }

    private fun pickGeminiVoice() {
        AlertDialog.Builder(this)
            .setTitle("Voz de Gemini")
            .setItems(GeminiTts.VOICES.toTypedArray()) { _, i ->
                Prefs.setGeminiVoice(this, GeminiTts.VOICES[i])
                testVoice()
            }
            .show()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun requestPerms() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    private fun startJarvis() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPerms(); return
        }
        Prefs.save(this, wake.text.toString(), key.text.toString())
        Prefs.setEnabled(this, true)
        val i = Intent(this, JarvisService::class.java)
        stopService(i) // reinicia para aplicar el nuevo nombre
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        refresh()
    }

    private fun stopJarvis() {
        Prefs.setEnabled(this, false)
        stopService(Intent(this, JarvisService::class.java))
        refresh()
    }

    private fun refresh() {
        val on = Prefs.enabled(this)
        val name = Prefs.wakeWord(this)
        status.text = if (on) "Escuchando. Di \"$name\" y pídeme algo." else "Apagado."
        toggle.text = if (on) "Detener" else "Activar"
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        refresh()
    }
}
