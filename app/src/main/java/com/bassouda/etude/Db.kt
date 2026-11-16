package com.bassouda.etude

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Messagerie : base Firebase Realtime Database, accessible par simple adresse web (REST), sans SDK. */
object Db {
    var base = ""
    fun ok(): Boolean = base.isNotEmpty()
    fun clean(u: String): String {
        var s = u.trim().trimEnd('/')
        if (s.isNotEmpty() && !s.startsWith("http")) s = "https://" + s
        return s
    }
    fun pseudo(s: String): String = s.lowercase().replace(Regex("[^a-z0-9_-]"), "")

    private fun req(method: String, path: String, body: String?): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 20000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val s = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 401 || code == 403) throw Exception("accès refusé : dans Firebase, mets les règles de la base en mode test.")
        if (code !in 200..299) throw Exception("serveur de messagerie injoignable (" + code + "), vérifie l'adresse dans Réglages.")
        return s
    }

    fun register(me: String, dev: String): String {
        val s = req("GET", "/users/" + me + ".json", null).trim()
        if (s == "null" || s.isEmpty()) {
            req("PUT", "/users/" + me + ".json", JSONObject().put("id", dev).toString())
            return ""
        }
        return if (JSONObject(s).optString("id") == dev) "" else "ce pseudo est déjà pris, choisis-en un autre."
    }

    fun exists(u: String): Boolean = req("GET", "/users/" + u + ".json", null).trim() != "null"

    private fun chatId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    fun send(me: String, friend: String, text: String) {
        val body = JSONObject().put("from", me).put("text", text).put("t", System.currentTimeMillis()).toString()
        req("POST", "/chats/" + chatId(me, friend) + ".json", body)
    }

    fun fetch(me: String, friend: String): List<Pair<String, String>> {
        val s = req("GET", "/chats/" + chatId(me, friend) + ".json?orderBy=%22%24key%22&limitToLast=60", null).trim()
        if (s == "null" || s.isEmpty()) return emptyList()
        val o = JSONObject(s)
        val keys = ArrayList<String>()
        val ks = o.keys()
        while (ks.hasNext()) keys.add(ks.next())
        keys.sort()
        return keys.map { k -> Pair(o.getJSONObject(k).optString("from"), o.getJSONObject(k).optString("text")) }
    }
}
