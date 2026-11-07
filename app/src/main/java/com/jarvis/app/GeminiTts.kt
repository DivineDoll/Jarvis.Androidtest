package com.jarvis.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiTts {
    val VOICES = listOf("Charon", "Orus", "Fenrir", "Puck", "Kore", "Aoede", "Leda", "Zephyr")

    class Audio(val pcm: ByteArray, val rate: Int)

    /** Intenta primero el modelo con capa gratis y luego uno de respaldo. */
    fun synth(apiKey: String, text: String, voice: String): Audio? {
        val attempts = listOf(
            "gemini-3.1-flash-tts-preview" to false,
            "gemini-3.8-flash-lite-tts" to true
        )
        for ((model, newFormat) in attempts) {
            try {
                val a = request(apiKey, model, newFormat, text, voice)
                if (a != null) return a
            } catch (_: Exception) { }
        }
        return null
    }

    private fun request(apiKey: String, model: String, newFormat: Boolean, text: String, voice: String): Audio? {
        val voiceConfig = if (newFormat)
            JSONObject().put("voice", voice)
        else
            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))

        val contents = JSONArray().put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))
        )
        val body = JSONObject()
            .put("contents", contents)
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", voiceConfig)))

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 45000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        if (code !in 200..299) { conn.disconnect(); return null }
        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        for (i in 0 until parts.length()) {
            val inline = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
            val data = inline.optString("data")
            if (data.isBlank()) continue
            val rate = Regex("rate=(\\d+)").find(inline.optString("mimeType"))
                ?.groupValues?.get(1)?.toIntOrNull() ?: 24000
            return Audio(Base64.decode(data, Base64.DEFAULT), rate)
        }
        return null
    }

    /** Reproduce el audio y bloquea hasta que termina (llamar fuera del hilo principal). */
    fun play(a: Audio) {
        if (a.pcm.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(a.rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(a.pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(a.pcm, 0, a.pcm.size)
            track.play()
            val frames = a.pcm.size / 2
            val maxMs = frames * 1000L / a.rate + 2000
            val start = System.currentTimeMillis()
            while (track.playbackHeadPosition < frames &&
                System.currentTimeMillis() - start < maxMs) {
                Thread.sleep(50)
            }
        } finally {
            track.release()
        }
    }
}
