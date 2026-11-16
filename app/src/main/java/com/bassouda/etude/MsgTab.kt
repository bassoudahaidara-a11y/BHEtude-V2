package com.bassouda.etude

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MsgTab(private val a: MainActivity) {
    private val prefs = a.getSharedPreferences("bh", Activity.MODE_PRIVATE)
    private val box = LinearLayout(a)
    private val handler = Handler(Looper.getMainLooper())
    private var me = prefs.getString("pseudo", "") ?: ""
    private var friend = ""
    private var busy = false
    private var lastText = ""
    private var shown = ""
    private lateinit var scroll: ScrollView
    private var log: TextView? = null
    private var input: EditText? = null

    fun build(): View {
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(a.dp(14), a.dp(6), a.dp(14), a.dp(20))
        scroll = ScrollView(a)
        scroll.addView(box)
        render()
        tick()
        return scroll
    }

    private fun toast(s: String) { Toast.makeText(a, s, Toast.LENGTH_LONG).show() }
    private fun devId(): String {
        var d = prefs.getString("dev", "") ?: ""
        if (d.isEmpty()) { d = UUID.randomUUID().toString(); prefs.edit().putString("dev", d).apply() }
        return d
    }

    private fun tick() {
        handler.postDelayed({ tick() }, 3000)
        if (a.curTab != 3) return
        if (shown != Db.base + "|" + me) { render(); return }
        poll()
    }

    private fun poll() {
        if (friend.isEmpty() || busy) return
        busy = true
        val who = me
        val fr = friend
        a.bg({
            Db.fetch(who, fr).joinToString("\n\n") { p -> (if (p.first == who) "🟦 Moi : " else "🟩 " + p.first + " : ") + p.second }
        }) { r ->
            busy = false
            if (r != lastText) {
                lastText = r
                log?.text = if (r.isEmpty()) "Aucun message pour l'instant. Écris le premier !" else r
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun render() {
        shown = Db.base + "|" + me
        box.removeAllViews()
        log = null
        input = null
        box.addView(a.label("Messagerie"))
        if (!Db.ok()) {
            val t = a.out()
            t.text = "Pour écrire à un ami, il faut un petit serveur gratuit (Firebase) :\n\n" +
                "1. Va sur console.firebase.google.com et crée un projet.\n" +
                "2. Build > Realtime Database > Créer une base de données > mode test.\n" +
                "3. Copie l'adresse de la base (elle ressemble à https://nom-default-rtdb.firebaseio.com).\n" +
                "4. Touche « Réglages » et colle-la. Ton ami fait pareil avec la MÊME adresse."
            box.addView(t)
            box.addView(a.btn("Réglages") { a.askKey() })
            return
        }
        if (me.isEmpty()) {
            val p = a.edit("Choisis ton pseudo (lettres et chiffres, 3 minimum)", 1)
            box.addView(p)
            box.addView(a.btn("Valider mon pseudo") {
                a.bg({
                    val n = Db.pseudo(p.text.toString())
                    if (n.length < 3) throw Exception("pseudo trop court (3 caractères minimum).")
                    val e = Db.register(n, devId())
                    if (e.isNotEmpty()) throw Exception(e)
                    n
                }) { r ->
                    if (r.startsWith("Erreur")) toast(r)
                    else { me = r; prefs.edit().putString("pseudo", r).apply(); render() }
                }
            })
            return
        }
        val who = TextView(a)
        who.text = "Mon pseudo : @" + me
        who.setTextColor(0xFFFFFFFF.toInt())
        who.setPadding(0, 0, 0, a.dp(6))
        box.addView(who)
        val f = a.edit("Pseudo de ton ami", 1)
        f.setText(friend)
        box.addView(f)
        box.addView(a.btn("Ouvrir la discussion") {
            a.bg({
                val n = Db.pseudo(f.text.toString())
                if (n == me) throw Exception("c'est ton propre pseudo.")
                if (!Db.exists(n)) throw Exception("@" + n + " n'a pas encore choisi son pseudo dans l'application.")
                n
            }) { r ->
                if (r.startsWith("Erreur")) toast(r)
                else { friend = r; lastText = ""; render() }
            }
        })
        if (friend.isEmpty()) return
        box.addView(a.label("Discussion avec @" + friend))
        val lg = a.out()
        lg.text = "Chargement…"
        log = lg
        box.addView(lg)
        val inp = a.edit("Ton message…", 2)
        input = inp
        box.addView(inp)
        val r = a.row()
        r.addView(a.btn("🎤 Parler") { a.dictate(inp) }, a.w1())
        r.addView(a.btn("Envoyer") { send(inp.text.toString()) }, a.w1())
        box.addView(r)
        box.addView(a.btn("📎 Envoyer mon résumé", 0xFF1B8A5A.toInt()) {
            val s = a.lessonOut.text.toString()
            if (s.length < 20) toast("Fais d'abord un résumé dans l'onglet Leçon.") else send("📎 Résumé :\n" + s)
        })
        poll()
    }

    private fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || friend.isEmpty()) return
        val who = me
        val fr = friend
        a.bg({ Db.send(who, fr, t); "ok" }) { r ->
            if (r.startsWith("Erreur")) toast(r)
            else { input?.setText(""); poll() }
        }
    }
}
