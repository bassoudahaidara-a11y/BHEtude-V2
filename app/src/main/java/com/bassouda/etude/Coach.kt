package com.bassouda.etude

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Le « petit coin » : un coach qui parle, disponible dans toutes les parties. */
class Coach(private val a: MainActivity) {
    private val hist = ArrayList<Pair<String, String>>()
    private val sb = StringBuilder()
    private var voice = true
    private lateinit var fab: Button
    private lateinit var panel: LinearLayout
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    fun attach(frame: FrameLayout) {
        fab = Button(a)
        fab.text = "🤖"
        fab.textSize = 26f
        fab.setPadding(0, 0, 0, 0)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(a.CYAN)
        fab.background = g
        fab.setOnClickListener { open() }
        val lp = FrameLayout.LayoutParams(a.dp(60), a.dp(60), Gravity.BOTTOM or Gravity.END)
        lp.setMargins(0, 0, a.dp(14), a.dp(14))
        frame.addView(fab, lp)

        panel = LinearLayout(a)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = a.shape(0xFF0E1A3C.toInt())
        panel.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8))
        panel.visibility = View.GONE
        val h = (a.resources.displayMetrics.heightPixels * 0.55f).toInt()
        frame.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM))

        val head = LinearLayout(a)
        head.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(a)
        title.text = "🤖 Ton coach"
        title.setTextColor(a.CYAN)
        title.textSize = 18f
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val vb = a.btn("🔊", a.CARD) { }
        vb.setOnClickListener { voice = !voice; vb.text = if (voice) "🔊" else "🔇"; if (!voice) a.stopSpeak() }
        head.addView(vb, a.w1())
        head.addView(a.btn("✖", a.CARD) { close() }, a.w1())
        panel.addView(head)

        scroll = ScrollView(a)
        log = a.out()
        scroll.addView(log)
        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val chips = LinearLayout(a)
        for (c in listOf("Explique simplement", "Donne un exemple", "Comment réviser ?")) {
            val b = a.btn(c, a.CARD) { send(c) }
            b.textSize = 12f
            chips.addView(b, a.w1())
        }
        panel.addView(chips)

        val bar = LinearLayout(a)
        input = a.edit("Pose ta question…", 1)
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(a.btn("🎤") { a.dictate(input) }, a.w1())
        bar.addView(a.btn("➤") { send(input.text.toString()) }, a.w1())
        panel.addView(bar)

        a.onDictated = { t -> if (t === input) send(input.text.toString()) }
    }

    private fun open() {
        panel.visibility = View.VISIBLE
        fab.visibility = View.GONE
        if (sb.isEmpty()) add("🤖 Salut Bassouda ! Je suis ton coach. Pose-moi une question, ou touche 🎤 et parle.", true)
    }

    private fun close() {
        panel.visibility = View.GONE
        fab.visibility = View.VISIBLE
        a.stopSpeak()
    }

    private fun add(line: String, say: Boolean = false) {
        sb.append(line).append("\n\n")
        log.text = sb.toString()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        if (say && voice) a.speak(line.removePrefix("🤖 "))
    }

    private fun sys(): String {
        val names = listOf("Leçon", "Compris (ma compréhension)", "Évaluer", "Messages")
        return a.SYS + " Tu es le coach, dans un petit coin de l'application. Réponds en 2 à 5 phrases courtes, comme à l'oral, puis propose une action utile. " +
            "L'application a 4 onglets : Leçon (importer PDF, image ou vidéo, résumé, explications avec exemples), Compris, Évaluer, Messages. " +
            "Onglet actuel : " + names[a.curTab] + ". Leçon de l'élève (peut être vide) :\n" + a.lessonBox.text.toString().take(4000)
    }

    private fun send(t0: String) {
        val t = t0.trim()
        if (t.isEmpty() || !a.hasKey()) return
        input.setText("")
        hist.add(Pair("user", t))
        add("🧑 " + t)
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys()))
        for (p in hist.takeLast(10)) msgs.put(JSONObject().put("role", p.first).put("content", p.second))
        a.bg({ Groq.chat(msgs) }) { r ->
            if (r.startsWith("Erreur")) add(r)
            else { hist.add(Pair("assistant", r)); add("🤖 " + r, true) }
        }
    }
}
