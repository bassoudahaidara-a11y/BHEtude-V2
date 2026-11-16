package com.bassouda.etude

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity() {
    val BG = 0xFF08122B.toInt()
    val CARD = 0xFF12224A.toInt()
    val ACC = 0xFF2F80ED.toInt()
    val CYAN = 0xFF00D4FF.toInt()
    val SYS = "Tu es BH Étude, le professeur personnel de Bassouda. Réponds toujours en français simple, clair et encourageant. " +
        "Texte brut : jamais d'astérisques, de dièses, de tableaux ni de LaTeX. Écris les maths comme dans un cahier (x², √, ×, ÷, −), une étape par ligne."

    private lateinit var prefs: SharedPreferences
    lateinit var lessonBox: EditText
    lateinit var lessonOut: TextView
    private lateinit var videoView: VideoView
    private lateinit var understandBox: EditText
    private lateinit var understandOut: TextView
    private lateinit var quizLog: TextView
    private lateinit var quizAnswer: EditText
    private lateinit var speakCheck: CheckBox
    private val tabs = ArrayList<View>()
    private var tts: TextToSpeech? = null
    private var ttsOk = false
    private var recognizer: SpeechRecognizer? = null
    private var pendingTarget: EditText? = null
    private var videoUri: Uri? = null
    private var quiz = JSONArray()
    private val logSb = StringBuilder()
    var curTab = 0
    var onDictated: ((EditText) -> Unit)? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("bh", MODE_PRIVATE)
        Groq.key = prefs.getString("key", "") ?: ""
        Db.base = prefs.getString("db", "") ?: ""
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) { tts?.language = Locale.FRENCH; tts?.setSpeechRate(0.95f); ttsOk = true } }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)
        root.setOnApplyWindowInsetsListener { v, ins ->
            val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            ins
        }
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(10), dp(16), dp(6))
        val title = TextView(this)
        title.text = "BH Étude"
        title.textSize = 24f
        title.setTextColor(CYAN)
        title.typeface = Typeface.DEFAULT_BOLD
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(btn("Clé", CARD) { askKey() })
        root.addView(head)

        val frame = FrameLayout(this)
        tabs.add(lessonTab()); tabs.add(understandTab()); tabs.add(quizTab()); tabs.add(msgTab())
        for (t in tabs) frame.addView(t, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        lessonBox.setText(prefs.getString("lesson", "") ?: "")
        Coach(this).attach(frame)
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this)
        val names = listOf("📖\nLeçon", "✍️\nCompris", "🎯\nÉvaluer", "💬\nMessages")
        for (i in names.indices) { val tb = btn(names[i], CARD) { show(i) }; tb.textSize = 12f; tb.minHeight = 0; tb.setPadding(0, dp(6), 0, dp(6)); bar.addView(tb, w1()) }
        root.addView(bar)
        setContentView(root)
        show(0)
        if (Groq.key.isBlank()) askKey()
    }

    // ---------- helpers UI ----------
    fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    fun shape(c: Int): GradientDrawable { val g = GradientDrawable(); g.setColor(c); g.cornerRadius = dp(14).toFloat(); return g }
    fun btn(text: String, color: Int = ACC, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text; b.isAllCaps = false; b.setTextColor(Color.WHITE); b.textSize = 14f
        b.background = shape(color)
        b.setOnClickListener { onClick() }
        return b
    }
    fun w1(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        p.setMargins(dp(3), dp(4), dp(3), dp(4))
        return p
    }
    fun row(): LinearLayout = LinearLayout(this)
    fun page(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(14), dp(6), dp(14), dp(20))
        return l
    }
    fun wrap(l: LinearLayout): View { val s = ScrollView(this); s.addView(l); return s }
    fun label(t: String): TextView {
        val v = TextView(this)
        v.text = t; v.textSize = 18f; v.setTextColor(CYAN); v.typeface = Typeface.DEFAULT_BOLD
        v.setPadding(0, dp(6), 0, dp(8))
        return v
    }
    fun edit(hint: String, lines: Int): EditText {
        val e = EditText(this)
        e.hint = hint; e.setHintTextColor(0xFF8899BB.toInt()); e.setTextColor(Color.WHITE)
        e.minLines = lines; e.gravity = Gravity.TOP
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        e.background = shape(CARD)
        e.setPadding(dp(12), dp(12), dp(12), dp(12))
        return e
    }
    fun out(): TextView {
        val t = TextView(this)
        t.setTextColor(Color.WHITE); t.textSize = 16f; t.setTextIsSelectable(true)
        t.background = shape(CARD)
        t.setPadding(dp(12), dp(12), dp(12), dp(12))
        t.setLineSpacing(0f, 1.15f)
        return t
    }
    fun show(i: Int) { curTab = i; for (k in tabs.indices) tabs[k].visibility = if (k == i) View.VISIBLE else View.GONE }

    fun askKey() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val k = EditText(this)
        k.hint = "Clé Groq (gsk_…)"
        k.setSingleLine(true)
        k.setText(Groq.key)
        val u = EditText(this)
        u.hint = "Adresse Firebase (messagerie)"
        u.setSingleLine(true)
        u.setText(Db.base)
        box.addView(k)
        box.addView(u)
        AlertDialog.Builder(this).setTitle("Réglages").setView(box)
            .setPositiveButton("Enregistrer et tester") { _, _ ->
                Groq.key = k.text.toString().trim()
                Db.base = Db.clean(u.text.toString())
                prefs.edit().putString("key", Groq.key).putString("db", Db.base).apply()
                Toast.makeText(this, "Test de la clé en cours…", Toast.LENGTH_SHORT).show()
                bg({ if (Groq.key.isEmpty()) "Clé Groq vide." else Groq.test() }) { r -> Toast.makeText(this, r, Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton("Plus tard", null).show()
    }
    fun hasKey(): Boolean { if (Groq.key.isBlank()) { askKey(); return false }; return true }

    fun bg(work: () -> String, done: (String) -> Unit) {
        Thread {
            val r = try { work() } catch (e: Exception) { "Erreur : " + (e.message ?: "inconnue") }
            runOnUiThread { done(r) }
        }.start()
    }

    fun speak(text: String) {
        if (ttsOk && text.isNotBlank()) tts?.speak(text.take(3900), TextToSpeech.QUEUE_FLUSH, null, "bh")
    }

    fun stopSpeak() { tts?.stop() }

    // ---------- Onglet 1 : leçon ----------
    private fun lessonTab(): View {
        val l = page()
        l.addView(label("Ma leçon"))
        lessonBox = edit("Écris ou colle ton cours ici, ou importe un PDF, une image, une vidéo…", 6)
        l.addView(lessonBox)
        val r1 = row()
        r1.addView(btn("📄 PDF") { pick("application/pdf", 10) }, w1())
        r1.addView(btn("🖼 Image") { pick("image/*", 11) }, w1())
        r1.addView(btn("🎬 Vidéo") { pick("video/*", 12) }, w1())
        l.addView(r1)
        videoView = VideoView(this)
        videoView.visibility = View.GONE
        l.addView(videoView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        l.addView(btn("📸 Expliquer cet instant de la vidéo", 0xFF1B8A5A.toInt()) { explainFrame() })
        val r2 = row()
        r2.addView(btn("Résumé") { ai("Fais un résumé clair et structuré de cette leçon.") }, w1())
        r2.addView(btn("Explique + exemples") { ai("Explique cette leçon comme un bon professeur, étape par étape, avec au moins trois exemples concrets et simples.") }, w1())
        l.addView(r2)
        val r3 = row()
        r3.addView(btn("Fiche de révision") { ai("Fais une fiche de révision : les points essentiels, les formules ou définitions à retenir, et un mini-exemple pour chacun.") }, w1())
        r3.addView(btn("🔊 Lire") { speak(lessonOut.text.toString()) }, w1())
        l.addView(r3)
        lessonOut = out()
        l.addView(lessonOut)
        return wrap(l)
    }

    private fun ai(instr: String) {
        val src = lessonBox.text.toString().trim()
        if (src.isEmpty()) { lessonOut.text = "Ajoute d'abord une leçon (texte, PDF, image ou vidéo)."; return }
        if (!hasKey()) return
        lessonOut.text = "Je réfléchis…"
        bg({ Groq.ask(SYS, instr + "\n\nLEÇON :\n" + src.take(12000)) }) { lessonOut.text = it }
    }

    private fun pick(mime: String, code: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = mime
        startActivityForResult(i, code)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (res != RESULT_OK || uri == null) return
        when (req) { 10 -> importPdf(uri); 11 -> importImage(uri); 12 -> importVideo(uri) }
    }

    private fun gotText(r: String, what: String) {
        if (r.startsWith("Erreur")) lessonOut.text = r
        else { lessonBox.setText(r); lessonOut.text = what + " importé. Choisis maintenant Résumé ou Explique + exemples." }
    }

    private fun importPdf(uri: Uri) {
        if (!hasKey()) return
        lessonOut.text = "Lecture du PDF (12 premières pages au maximum)…"
        bg({
            val pfd = contentResolver.openFileDescriptor(uri, "r") ?: throw Exception("PDF illisible")
            val sb = StringBuilder()
            pfd.use { f ->
                val rd = PdfRenderer(f)
                val n = minOf(rd.pageCount, 12)
                var p = 0
                while (p < n) {
                    val cur = p + 1
                    runOnUiThread { lessonOut.text = "Lecture du PDF : page " + cur + " sur " + n + "…" }
                    val bmps = ArrayList<Bitmap>()
                    for (k in p until minOf(p + 2, n)) {
                        val pg = rd.openPage(k)
                        val h = (1000f * pg.height / pg.width).toInt()
                        val bm = Bitmap.createBitmap(1000, h, Bitmap.Config.ARGB_8888)
                        bm.eraseColor(Color.WHITE)
                        pg.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pg.close()
                        bmps.add(bm)
                    }
                    sb.append(Groq.askImages("Tu transcris des pages de cours.", "Recopie fidèlement tout le texte de ces pages (formules comprises), sans rien ajouter.", bmps)).append("\n\n")
                    p += 2
                }
                rd.close()
            }
            sb.toString()
        }) { gotText(it, "PDF") }
    }

    private fun importImage(uri: Uri) {
        if (!hasKey()) return
        lessonOut.text = "Lecture de l'image…"
        bg({
            val opt = BitmapFactory.Options()
            opt.inSampleSize = 2
            val bm = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opt) } ?: throw Exception("Image illisible")
            Groq.askImages("Tu transcris une page de cahier ou de livre.", "Recopie fidèlement tout le texte de cette image (formules comprises). S'il n'y a pas de texte, décris-la pour un élève.", listOf(bm))
        }) { gotText(it, "Image") }
    }

    private fun importVideo(uri: Uri) {
        videoUri = uri
        videoView.visibility = View.VISIBLE
        videoView.setVideoURI(uri)
        videoView.setMediaController(MediaController(this))
        videoView.seekTo(100)
        lessonOut.text = "Vidéo chargée. Touche-la pour lancer ou mettre en pause, arrête-toi sur l'instant voulu, puis touche « Expliquer cet instant »."
    }

    private fun explainFrame() {
        val u = videoUri
        if (u == null) { lessonOut.text = "Importe d'abord une vidéo."; return }
        if (!hasKey()) return
        val pos = videoView.currentPosition * 1000L
        lessonOut.text = "J'observe la vidéo…"
        bg({
            val md = MediaMetadataRetriever()
            md.setDataSource(this, u)
            val bm = md.getFrameAtTime(pos, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            md.release()
            if (bm == null) throw Exception("Image de la vidéo introuvable")
            Groq.askImages(SYS, "Voici une image d'une vidéo de cours. Explique précisément ce qu'elle montre, comme un professeur, avec des exemples.", listOf(bm))
        }) { lessonOut.text = it }
    }

    // ---------- Onglet 2 : ma compréhension ----------
    private fun understandTab(): View {
        val l = page()
        l.addView(label("Ce que j'ai compris"))
        understandBox = edit("Écris ici ce que tu as compris, ou touche le micro et parle…", 6)
        l.addView(understandBox)
        val r = row()
        r.addView(btn("🎤 Parler") { dictate(understandBox) }, w1())
        r.addView(btn("Vérifier") { checkUnderstanding() }, w1())
        l.addView(r)
        understandOut = out()
        l.addView(understandOut)
        return wrap(l)
    }

    private fun checkUnderstanding() {
        val mine = understandBox.text.toString().trim()
        if (mine.isEmpty()) { understandOut.text = "Écris ou dis d'abord ce que tu as compris."; return }
        if (!hasKey()) return
        val src = lessonBox.text.toString().trim().ifEmpty { "(aucune leçon fournie : juge avec tes connaissances)" }
        understandOut.text = "Je vérifie…"
        val q = "LEÇON :\n" + src.take(10000) + "\n\nCE QUE L'ÉLÈVE A COMPRIS :\n" + mine +
            "\n\nRéponds en 4 parties : 1) Ce qui est juste. 2) Ce qui est faux ou ce qui manque. 3) La bonne explication corrigée. 4) Une note sur 10. Sois encourageant."
        bg({ Groq.ask(SYS, q) }) { understandOut.text = it }
    }

    // ---------- Onglet 3 : évaluation ----------
    private fun quizTab(): View {
        val l = page()
        l.addView(label("Évaluation"))
        speakCheck = CheckBox(this)
        speakCheck.text = "🔊 Lire les questions à voix haute"
        speakCheck.setTextColor(Color.WHITE)
        l.addView(speakCheck)
        l.addView(btn("Commencer l'évaluation") { startQuiz() })
        quizLog = out()
        l.addView(quizLog)
        quizAnswer = edit("Ta réponse (écris ou parle)…", 3)
        l.addView(quizAnswer)
        val r = row()
        r.addView(btn("🎤 Parler") { dictate(quizAnswer) }, w1())
        r.addView(btn("Envoyer") { sendAnswer() }, w1())
        l.addView(r)
        return wrap(l)
    }

    private fun m(role: String, content: String): JSONObject = JSONObject().put("role", role).put("content", content)

    private fun startQuiz() {
        val src = lessonBox.text.toString().trim()
        if (src.isEmpty()) { quizLog.text = "Ajoute d'abord une leçon dans l'onglet Leçon."; return }
        if (!hasKey()) return
        quiz = JSONArray()
        logSb.setLength(0)
        quiz.put(m("system", SYS + " Tu interroges l'élève sur la leçon fournie. Pose UNE seule question à la fois et attends sa réponse. " +
            "Après chaque réponse, dis si c'est juste, corrige en une ou deux phrases, puis pose la question suivante. " +
            "Après 5 questions, donne une note sur 20 et des conseils. LEÇON :\n" + src.take(10000)))
        quiz.put(m("user", "Je suis prêt, pose-moi la première question."))
        quizLog.text = "Je prépare la première question…"
        turn()
    }

    private fun sendAnswer() {
        val a = quizAnswer.text.toString().trim()
        if (a.isEmpty() || quiz.length() == 0) return
        quiz.put(m("user", a))
        logSb.append("✍️ ").append(a).append("\n\n")
        quizLog.text = logSb.toString() + "…"
        quizAnswer.setText("")
        turn()
    }

    private fun turn() {
        bg({ Groq.chat(quiz) }) { r ->
            if (r.startsWith("Erreur")) {
                quiz.remove(quiz.length() - 1)
                quizLog.text = logSb.toString() + r
            } else {
                quiz.put(m("assistant", r))
                logSb.append("🧑‍🏫 ").append(r).append("\n\n")
                quizLog.text = logSb.toString()
                if (speakCheck.isChecked) speak(r)
            }
        }
    }

    // ---------- Onglet 4 : messagerie ----------
    private fun msgTab(): View = MsgTab(this).build()

    // ---------- Voix ----------
    fun dictate(t: EditText) {
        pendingTarget = t
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        startListening()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening()
    }

    private fun startListening() {
        val t = pendingTarget ?: return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "La reconnaissance vocale n'est pas disponible sur ce téléphone.", Toast.LENGTH_LONG).show()
            return
        }
        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { Toast.makeText(this@MainActivity, "Je t'écoute…", Toast.LENGTH_SHORT).show() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) { Toast.makeText(this@MainActivity, "Je n'ai pas entendu, réessaie.", Toast.LENGTH_SHORT).show() }
            override fun onResults(r: Bundle?) {
                val s = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                val old = t.text.toString()
                t.setText(if (old.isEmpty()) s else old + " " + s)
                t.setSelection(t.text.length)
                onDictated?.invoke(t)
            }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(a: Int, b: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        sr.startListening(i)
    }

    override fun onPause() {
        prefs.edit().putString("lesson", lessonBox.text.toString()).apply()
        super.onPause()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
