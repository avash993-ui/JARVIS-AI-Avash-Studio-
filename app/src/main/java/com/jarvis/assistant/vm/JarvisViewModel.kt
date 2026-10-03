package com.jarvis.assistant.vm

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.data.*
import com.jarvis.assistant.engine.Actions
import com.jarvis.assistant.engine.LlmEngine
import com.jarvis.assistant.engine.Quality
import com.jarvis.assistant.engine.Persona
import com.jarvis.assistant.search.CloudAi
import com.jarvis.assistant.search.WebSearch
import com.jarvis.assistant.ui.strings
import com.jarvis.assistant.voice.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Phase { Idle, Listening, Thinking, Speaking }
enum class Screen { Models, Chat }
data class Ask(val text: String, val voice: Boolean)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val hw = Hardware.read(app)
    private val dl = Downloader(File(app.filesDir, "models"))
    private val voice = Voice(app)
    private val main = Handler(Looper.getMainLooper())

    // settings mirrors
    var lang by mutableStateOf(prefs.lang); private set
    var humor by mutableIntStateOf(prefs.humor); private set
    var speakTyped by mutableStateOf(prefs.speakTyped); private set
    var webOn by mutableStateOf(prefs.webOn); private set
    var cloudMode by mutableStateOf(prefs.cloudMode); private set
    var hfFirst by mutableStateOf(prefs.sourceOrder.startsWith("hf")); private set
    val s get() = strings(lang)

    // models
    var screen by mutableStateOf(Screen.Models); private set
    var installed by mutableIntStateOf(prefs.installed); private set
    var engineReady by mutableStateOf(false); private set
    val sizes = mutableStateMapOf<Int, Long>()
    val progress = mutableStateMapOf<Int, Float>()
    var downloading by mutableIntStateOf(0); private set
    var modelError by mutableStateOf<String?>(null); private set
    val recommended = Hardware.recommend(hw)
    private var dlJob: Job? = null

    // chat
    val msgs = mutableStateListOf<Msg>()
    val history = mutableStateListOf<Convo>()
    private var convo = Convo(System.currentTimeMillis(), "", mutableListOf())
    var phase by mutableStateOf(Phase.Idle); private set
    var overlay by mutableStateOf(false); private set
    var overlayText by mutableStateOf(""); private set
    var webNote by mutableStateOf(false); private set
    var pendingAsk by mutableStateOf<Ask?>(null); private set
    private var job: Job? = null
    private var generating = false

    init {
        history.addAll(prefs.loadHistory())
        val ok = prefs.installed > 0 && dl.fileFor(Catalog.byN(prefs.installed)).exists()
        if (ok) {
            screen = Screen.Chat
            viewModelScope.launch {
                val loaded = withContext(Dispatchers.Default) { LlmEngine.load(dl.fileFor(Catalog.byN(prefs.installed)).absolutePath) }
                engineReady = loaded
                if (!loaded) screen = Screen.Models
            }
        }
        refreshSizes()
    }

    private fun order() = prefs.sourceOrder.split(",").map { if (it == "hf") SourceKind.HF else SourceKind.OLLAMA }

    fun refreshSizes() {
        viewModelScope.launch {
            Catalog.levels.map { l ->
                async(Dispatchers.IO) { dl.resolve(l, order()).firstOrNull()?.let { sizes[l.n] = it.size } }
            }.awaitAll()
        }
    }

    // ---------- model install ----------
    fun install(n: Int) {
        if (downloading != 0) return
        val l = Catalog.byN(n)
        downloading = n; modelError = null; progress[n] = 0f
        dlJob = viewModelScope.launch {
            try {
                val cands = dl.resolve(l, order())
                if (cands.isEmpty()) throw IllegalStateException("no source reachable")
                var ok = false
                var lastErr: Throwable? = null
                for (c in cands) {
                    try {
                        val f = dl.download(l, c) { progress[n] = it }
                        val loaded = withContext(Dispatchers.Default) { LlmEngine.load(f.absolutePath) }
                        if (loaded) { ok = true; break }
                        dl.delete(l); lastErr = IllegalStateException("model could not be loaded (${c.kind})")
                    } catch (e: CancellationException) { throw e } catch (e: Throwable) { lastErr = e }
                }
                if (!ok) throw lastErr ?: IllegalStateException("failed")
                prefs.installed = n; installed = n; engineReady = true; screen = Screen.Chat
            } catch (ignored: CancellationException) {
            } catch (e: Throwable) { modelError = e.message ?: "error" }
            finally { downloading = 0 }
        }
    }

    fun cancelDownload() { dlJob?.cancel() }

    fun useInstalled(n: Int) {
        viewModelScope.launch {
            val f = dl.fileFor(Catalog.byN(n))
            val loaded = withContext(Dispatchers.Default) { LlmEngine.load(f.absolutePath) }
            if (loaded) { prefs.installed = n; installed = n; engineReady = true; screen = Screen.Chat }
            else modelError = "model could not be loaded"
        }
    }

    fun isDownloaded(n: Int) = dl.fileFor(Catalog.byN(n)).exists()
    fun removeModel(n: Int) {
        if (installed == n) { LlmEngine.unload(); engineReady = false; installed = 0; prefs.installed = 0 }
        dl.delete(Catalog.byN(n))
    }
    fun openModels() { stopAll(); screen = Screen.Models }
    fun backToChat() { if (engineReady) screen = Screen.Chat }

    // ---------- settings ----------
    @JvmName("applyLang") fun setLang(v: String) { lang = v; prefs.lang = v }
    @JvmName("applyHumor") fun setHumor(v: Int) { humor = v; prefs.humor = v }
    @JvmName("applySpeakTyped") fun setSpeakTyped(v: Boolean) { speakTyped = v; prefs.speakTyped = v }
    @JvmName("applyWebOn") fun setWebOn(v: Boolean) { webOn = v; prefs.webOn = v }
    @JvmName("applyCloudMode") fun setCloudMode(v: String) { cloudMode = v; prefs.cloudMode = v }
    @JvmName("applyHfFirst") fun setHfFirst(v: Boolean) { hfFirst = v; prefs.sourceOrder = if (v) "hf,ollama" else "ollama,hf"; refreshSizes() }

    // ---------- chat ----------
    val busy get() = job?.isActive == true || pendingAsk != null

    fun send(text: String, viaVoice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        if (viaVoice) { overlay = true; overlayText = t; phase = Phase.Thinking }
        val hard = Persona.isHard(t, installed)
        if (hard && cloudMode == "ask") { pendingAsk = Ask(t, viaVoice); return }
        run(t, viaVoice, cloud = hard && cloudMode == "auto")
    }

    fun answerAsk(useCloud: Boolean) {
        val a = pendingAsk ?: return
        pendingAsk = null
        run(a.text, a.voice, useCloud)
    }

    fun askCloudAgain(index: Int) {
        if (busy) return
        val q = msgs.take(index).lastOrNull { it.role == "user" }?.text ?: return
        run(q, false, cloud = true, addUser = false)
    }

    private fun run(text: String, viaVoice: Boolean, cloud: Boolean, addUser: Boolean = true) {
        if (addUser) msgs.add(Msg("user", text))
        val idx = msgs.size
        msgs.add(Msg("assistant", "", cloud = cloud))
        if (viaVoice) { overlay = true; overlayText = "" }
        phase = Phase.Thinking
        val speak = viaVoice || speakTyped
        val sys = Persona.system(lang, humor)
        val context = msgs.subList(0, idx).dropLast(if (addUser) 1 else 0).takeLast(6)
            .joinToString("\n") { (if (it.role == "user") "User: " else "Jarvis: ") + it.text }
        generating = true

        job = viewModelScope.launch {
            var usedWeb = false
            var userForModel = text
            if (webOn && Persona.needsWeb(text)) {
                webNote = true
                val hits = WebSearch.search(text, prefs.braveKey)
                if (hits.isNotEmpty()) {
                    usedWeb = true
                    userForModel = "Use these web search results to answer, and mention briefly that you searched the web.\n" +
                        WebSearch.asContext(hits) + "\n\nQuestion: " + text
                }
                webNote = false
            }
            fun show(t: String) {
                msgs[idx] = msgs[idx].copy(text = t, web = usedWeb)
                if (viaVoice) overlayText = t
            }
            var spoken = 0

            val holdSpeech = cloudMode != "off"
            suspend fun cloudAnswer(fallbackText: String?) {
                val q = if (context.isBlank()) userForModel else "$context\nUser: $userForModel"
                val r = CloudAi.ask(sys, q, prefs.cloudUrl, prefs.cloudKey, prefs.cloudModel)
                r.onSuccess { ans ->
                    msgs[idx] = msgs[idx].copy(cloud = true)
                    phase = Phase.Speaking; show(ans)
                    if (speak) say(ans)
                }.onFailure { show(fallbackText ?: this@JarvisViewModel.s.cloudFail) }
            }

            if (cloud) {
                cloudAnswer(null)
            } else if (!engineReady) {
                show(s.noModel)
            } else {
                var acc = ""
                val err = withContext(Dispatchers.Default) {
                    LlmEngine.generate(sys, context, userForModel) { tok ->
                        acc += tok
                        val snap = Actions.strip(acc)
                        main.post {
                            if (phase != Phase.Speaking) phase = Phase.Speaking
                            show(snap)
                            if (speak && !holdSpeech) {
                                val cut = lastBoundary(snap, spoken)
                                if (cut > spoken) { say(snap.substring(spoken, cut)); spoken = cut }
                            }
                        }
                    }
                }
                // queued token posts run before this point (same main-looper queue, FIFO)
                val local = Actions.strip(acc).trim()
                val needCloud = err == null && !acc.contains("ACTION:") && cloudMode != "off" && Quality.bad(text, local, lang)
                if (needCloud) cloudAnswer(local.ifBlank { null })
                else if (err != null) show(this@JarvisViewModel.s.error + err)
                else if (acc.isBlank()) show(this@JarvisViewModel.s.empty)
                else {
                    val clean = Actions.strip(acc).trim()
                    val did = Actions.run(getApplication<Application>(), acc, lang)
                    if (clean.isEmpty()) {
                        val msg = did ?: this@JarvisViewModel.s.empty
                        show(msg); if (speak) say(msg)
                    } else {
                        if (did != null) show(clean)
                        if (speak && spoken < clean.length) say(clean.substring(minOf(spoken, clean.length)))
                    }
                }
            }
            generating = false
            persist()
            if (!(speak && voice.busy)) finishTurn()
        }
    }

    private fun lastBoundary(t: String, from: Int): Int {
        var cut = -1
        for (i in from until t.length) if (t[i] in ".!?؟۔\n") cut = i + 1
        return cut
    }

    private fun say(chunk: String) {
        voice.speak(chunk, lang) { if (!generating) main.post { finishTurn() } }
    }

    private fun finishTurn() {
        phase = Phase.Idle
        viewModelScope.launch { delay(1200); if (phase == Phase.Idle) overlay = false }
    }

    fun startListening() {
        if (busy) return
        voice.stopSpeaking()
        overlay = true; overlayText = ""; phase = Phase.Listening
        voice.listen(lang,
            onPartial = { overlayText = it },
            onFinal = { send(it, true) },
            onFail = { overlayText = s.micFail; phase = Phase.Idle; viewModelScope.launch { delay(1500); if (phase == Phase.Idle) overlay = false } })
    }

    fun stopAll() {
        job?.cancel(); LlmEngine.stop(); voice.stopSpeaking(); voice.stopListening()
        generating = false; pendingAsk = null; webNote = false
        phase = Phase.Idle; overlay = false
    }

    // ---------- history ----------
    private fun persist() {
        convo.msgs.clear(); convo.msgs.addAll(msgs)
        if (convo.title.isBlank()) convo.title = msgs.firstOrNull { it.role == "user" }?.text?.take(32) ?: ""
        if (history.none { it.id == convo.id }) history.add(convo)
        prefs.saveHistory(history)
    }

    fun newChat() { stopAll(); convo = Convo(System.currentTimeMillis(), "", mutableListOf()); msgs.clear() }
    fun openChat(c: Convo) { stopAll(); convo = c; msgs.clear(); msgs.addAll(c.msgs) }
    fun deleteChat(c: Convo) {
        history.removeAll { it.id == c.id }
        prefs.saveHistory(history)
        if (c.id == convo.id) newChat()
    }

    override fun onCleared() { voice.release(); super.onCleared() }
}
