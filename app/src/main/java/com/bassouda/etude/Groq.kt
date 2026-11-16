package com.bassouda.etude

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Groq {
    private const val BASE = "https://api.groq.com/openai/v1"
    class RateLimit(m: String) : Exception(m)
    var key = ""
    private var textModel: String? = null
    private var visionModel: String? = null

    private fun http(path: String, body: JSONObject?): String {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer " + key)
        c.connectTimeout = 20000
        c.readTimeout = 120000
        if (body != null) {
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val s = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 401) throw Exception("Clé Groq invalide : touche « Clé » pour la changer.")
        if (code == 429) throw RateLimit("Limite Groq atteinte : attends une minute puis réessaie.")
        if (code !in 200..299) {
            textModel = null
            visionModel = null
            throw Exception("Groq a répondu " + code + " " + s.take(250))
        }
        return s
    }

    private fun pickModels() {
        if (textModel != null) return
        val d = JSONObject(http("/models", null)).getJSONArray("data")
        val ids = ArrayList<String>()
        for (i in 0 until d.length()) ids.add(d.getJSONObject(i).getString("id"))
        val bad = Regex("whisper|guard|tts|orpheus|playai|safeguard|embed|compound", RegexOption.IGNORE_CASE)
        val chat = ids.filter { !bad.containsMatchIn(it) }
        textModel = listOf("openai/gpt-oss-120b", "qwen/qwen3.8-27b", "qwen/qwen3.6-27b", "openai/gpt-oss-20b")
            .firstOrNull { chat.contains(it) } ?: chat.firstOrNull() ?: throw Exception("Aucun modèle disponible sur ton compte Groq")
        visionModel = listOf("qwen/qwen3.6-27b", "qwen/qwen3.8-27b").firstOrNull { chat.contains(it) }
            ?: chat.firstOrNull { it.contains("qwen") && (it.contains("vl") || it.contains("27b")) }
            ?: "qwen/qwen3.6-27b"
    }

    fun test(): String {
        textModel = null
        visionModel = null
        pickModels()
        return "✅ Clé valide. Texte : " + textModel + " • Vision : " + visionModel
    }

    private fun clean(t: String): String =
        t.replace(Regex("(?s)<think>.*?</think>"), "").replace("*", "").replace(Regex("(?m)^#+\\s*"), "").trim()

    fun chat(messages: JSONArray, vision: Boolean = false): String {
        pickModels()
        val m = (if (vision) visionModel else textModel) ?: throw Exception("Modèle introuvable")
        val body = JSONObject().put("model", m).put("messages", messages).put("temperature", 0.4)
        if (m.startsWith("qwen")) body.put("reasoning_effort", "none")
        else if (m.contains("gpt-oss")) body.put("reasoning_effort", "low")
        for (attempt in 0..2) {
            try {
                val r = JSONObject(http("/chat/completions", body))
                return clean(r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", ""))
            } catch (e: RateLimit) {
                if (attempt == 2) throw e
                Thread.sleep(15000)
            }
        }
        throw Exception("Limite Groq atteinte : réessaie dans une minute.")
    }

    private fun msg(role: String, content: Any): JSONObject = JSONObject().put("role", role).put("content", content)

    fun ask(system: String, user: String): String =
        chat(JSONArray().put(msg("system", system)).put(msg("user", user)))

    private fun jpeg(b: Bitmap): String {
        val s = if (b.width > 1024) Bitmap.createScaledBitmap(b, 1024, (b.height * 1024f / b.width).toInt(), true) else b
        val o = ByteArrayOutputStream()
        s.compress(Bitmap.CompressFormat.JPEG, 80, o)
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    fun askImages(system: String, user: String, bmps: List<Bitmap>): String {
        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", user))
        for (b in bmps.take(3)) {
            parts.put(JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + jpeg(b))))
        }
        return chat(JSONArray().put(msg("system", system)).put(msg("user", parts)), true)
    }
}
