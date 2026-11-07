package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    private const val MODEL = "gemini-flash-latest"

    data class Reply(val speech: String, val action: JSONObject?)

    private fun systemPrompt(name: String) = """
Eres $name, un asistente de voz personal que vive en el teléfono Android del usuario. Hablas en el idioma del usuario, con tono amable, seguro y breve (máximo 2 frases, tu respuesta se lee en voz alta, sin markdown ni emojis).
Responde SIEMPRE y SOLO con un objeto JSON, sin texto adicional, con esta forma:
{"speech":"lo que dices en voz alta","action":{"type":"none"}}
Acciones disponibles (campo "action"):
- {"type":"open_app","name":"nombre de la app"}
- {"type":"alarm","hour":7,"minute":30,"label":"texto"}
- {"type":"timer","seconds":300}
- {"type":"web_search","query":"texto"}
- {"type":"maps","query":"lugar"}
- {"type":"none"} cuando solo conversas o respondes una pregunta.
Usa una acción solo si el usuario la pide claramente.
""".trimIndent()

    private fun part(text: String) = JSONArray().put(JSONObject().put("text", text))

    fun ask(apiKey: String, name: String, history: List<Pair<String, String>>, text: String): Reply {
        val contents = JSONArray()
        history.forEach { (role, content) ->
            val r = if (role == "assistant") "model" else "user"
            contents.put(JSONObject().put("role", r).put("parts", part(content)))
        }
        contents.put(JSONObject().put("role", "user").put("parts", part(text)))

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", part(systemPrompt(name))))
            .put("contents", contents)
            .put("generationConfig", JSONObject()
                .put("maxOutputTokens", 1024)
                .put("responseMimeType", "application/json"))

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()

        if (code !in 200..299) {
            val msg = when (code) {
                400, 403 -> "La clave de API no es válida."
                429 -> "Llegué al límite gratuito, intenta de nuevo en un minuto."
                else -> "Error de conexión, código $code."
            }
            return Reply(msg, null)
        }

        val parts = JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return parse(sb.toString())
    }

    private fun parse(raw: String): Reply {
        val s = raw.indexOf('{')
        val e = raw.lastIndexOf('}')
        if (s >= 0 && e > s) {
            try {
                val o = JSONObject(raw.substring(s, e + 1))
                val speech = o.optString("speech").ifBlank { "Hecho." }
                val action = o.optJSONObject("action")
                val a = if (action == null || action.optString("type") == "none") null else action
                return Reply(speech, a)
            } catch (_: Exception) { }
        }
        return Reply(raw.trim().ifBlank { "No te entendí." }, null)
    }
}
