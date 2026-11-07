package com.jarvis.app

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun wakeWord(c: Context): String = sp(c).getString("wake", "Jarvis") ?: "Jarvis"
    fun apiKey(c: Context): String = sp(c).getString("apikey", "") ?: ""
    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)

    fun save(c: Context, wake: String, key: String) {
        sp(c).edit()
            .putString("wake", wake.trim().ifEmpty { "Jarvis" })
            .putString("apikey", key.trim())
            .apply()
    }

    fun voice(c: Context): String = sp(c).getString("voice", "") ?: ""
    fun pitch(c: Context): Float = sp(c).getFloat("pitch", 0.85f)
    fun rate(c: Context): Float = sp(c).getFloat("rate", 0.95f)
    fun setVoice(c: Context, v: String) = sp(c).edit().putString("voice", v).apply()
    fun setPitch(c: Context, v: Float) = sp(c).edit().putFloat("pitch", v).apply()
    fun setRate(c: Context, v: Float) = sp(c).edit().putFloat("rate", v).apply()

    fun geminiOn(c: Context): Boolean = sp(c).getBoolean("gemini_voice_on", true)
    fun geminiVoice(c: Context): String = sp(c).getString("gemini_voice", "Charon") ?: "Charon"
    fun setGeminiOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("gemini_voice_on", v).apply()
    fun setGeminiVoice(c: Context, v: String) = sp(c).edit().putString("gemini_voice", v).apply()

    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()
}
