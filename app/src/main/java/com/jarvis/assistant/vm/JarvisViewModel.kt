package com.jarvis.assistant.vm

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jarvis.assistant.voice.Wake
import com.jarvis.assistant.voice.WakeService
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.data.Contact
import com.jarvis.assistant.data.Contacts
import com.jarvis.assistant.data.Convo
import com.jarvis.assistant.data.Msg
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.data.Providers
import com.jarvis.assistant.engine.Actions
import com.jarvis.assistant.engine.Cmd
import com.jarvis.assistant.engine.Intents
import com.jarvis.assistant.engine.Persona
import com.jarvis.assistant.search.Api
import com.jarvis.assistant.search.WebSearch
import com.jarvis.assistant.ui.strings
import com.jarvis.assistant.voice.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.speech.tts.Voice as TtsVoice

enum class Phase { Idle, Listening, Thinking, Speaking }
enum class Screen { Setup, Chat, Tutorial, Contacts }

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val voice = Voice(app)
    private val main = Handler(Looper.getMainLooper())
    private val ctx get() = getApplication<Application>()

    init {
        voice.onReady { voices -> main.post { ttsVoices = voices } }
        voice.setVoice(prefs.ttsVoice)
    }

    // ---------- settings ----------
    var lang by mutableStateOf(prefs.lang); private set
    var humor by mutableIntStateOf(prefs.humor); private set
    var speakTyped by mutableStateOf(prefs.speakTyped); private set
    var ttsVoice by mutableStateOf(prefs.ttsVoice); private set
    var ttsVoices by mutableStateOf<List<TtsVoice>>(emptyList()); private set
    var webOn by mutableStateOf(prefs.webOn); private set
    var assistantNameState by mutableStateOf(prefs.assistantName); private set
    var devOk by mutableStateOf(prefs.devOk); private set
    val assistantName get() = assistantNameState.ifBlank { if (lang == "fa") "جارویس" else "Jarvis" }
    val s get() = strings(lang).copy(appName = assistantName)
    fun tr(fa: String, en: String) = if (lang == "fa") fa else en

    @JvmName("applyLang") fun setLang(v: String) { lang = v; prefs.lang = v }
    @JvmName("applyHumor") fun setHumor(v: Int) { humor = v; prefs.humor = v }
    @JvmName("applySpeakTyped") fun setSpeakTyped(v: Boolean) { speakTyped = v; prefs.speakTyped = v }
    fun changeTtsVoice(name: String) { ttsVoice = name.ifBlank { "system-default" }; prefs.ttsVoice = ttsVoice; voice.setVoice(ttsVoice) }
    @JvmName("applyWebOn") fun setWebOn(v: Boolean) { webOn = v; prefs.webOn = v }
    fun setAssistantName(v: String) { assistantNameState = v.take(20); prefs.assistantName = v.take(20) }

    // ---------- developer mode ----------
    fun unlockDev(pin: String): Boolean {
        val clean = pin.trim()
        if (clean.isEmpty()) return false

        // Strong verifier: PBKDF2-HMAC-SHA256 with a per-install random salt.
        val saltKey = "devPinSalt"
        val hashKey = "devPinVerifier"
        val storedSalt = prefs.getSecureString(saltKey)
        val storedVerifier = prefs.getSecureString(hashKey)
        if (storedSalt != null && storedVerifier != null) {
            val ok = verifyPbkdf2(clean, storedSalt.hexToBytes(), storedVerifier.hexToBytes())
            if (ok) { devOk = true; prefs.devOk = true; return true }
            return false
        }

        // One-time migration from the old fixed SHA-256 verifier.
        val legacy = sha256("avash-jarvis:" + clean)
        if (legacy == LEGACY_DEV_HASH) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val verifier = pbkdf2(clean, salt)
            prefs.putSecureString(saltKey, salt.toHex())
            prefs.putSecureString(hashKey, verifier.toHex())
            devOk = true; prefs.devOk = true; return true
        }
        return false
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 180_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun verifyPbkdf2(pin: String, salt: ByteArray, expected: ByteArray): Boolean {
        val actual = pbkdf2(pin, salt)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    fun lockDev() { devOk = false; prefs.devOk = false }

    // ---------- memory + shortcuts ----------
    val memory = mutableStateListOf<String>().also { it.addAll(prefs.loadList("memory")) }
    val aliases = mutableStateMapOf<String, String>().also { it.putAll(prefs.loadMap("aliases")) }
    private val choices = prefs.loadMap("choices")

    fun memoryAdd(t: String): Boolean {
        val x = t.trim().take(200)
        if (x.isEmpty()) return false
        if (memory.size >= (if (devOk) 50 else 5)) return false
        memory.add(x); prefs.saveList("memory", memory); return true
    }
    fun memoryRemove(i: Int) { if (i in memory.indices) { memory.removeAt(i); prefs.saveList("memory", memory) } }
    fun memoryClear() { memory.clear(); prefs.saveList("memory", memory) }

    /** Spoken shortcut -> phone number. Demo allows 3, developer unlimited. */
    fun addAlias(spoken: String, number: String): Boolean {
        val k = Contacts.norm(spoken)
        if (k.isEmpty() || number.isBlank()) return false
        if (!aliases.containsKey(k) && aliases.size >= (if (devOk) 500 else 3)) return false
        aliases[k] = number; prefs.saveMap("aliases", aliases); return true
    }
    fun removeAlias(k: String) { aliases.remove(k); prefs.saveMap("aliases", aliases) }

    // ---------- navigation ----------
    val configured: Boolean get() {
        val p = Providers.byId(prefs.provider)
        return prefs.apiBase.isNotBlank() && prefs.apiModel.isNotBlank() && (prefs.apiKey.isNotBlank() || p?.needsKey == false)
    }
    var screen by mutableStateOf(Screen.Setup); private set
    var tutorialProvider by mutableStateOf("llm7"); private set
    fun openSetup() { stopAll(); screen = Screen.Setup }
    fun openTutorial(id: String) { tutorialProvider = id; screen = Screen.Tutorial }
    fun openContacts() { stopAll(); screen = Screen.Contacts }
    fun back() {
        screen = when (screen) {
            Screen.Tutorial -> Screen.Setup
            Screen.Setup -> if (configured) Screen.Chat else Screen.Setup
            else -> Screen.Chat
        }
    }
    fun saveApi(provider: String, base: String, key: String, model: String) {
        prefs.provider = provider; prefs.apiBase = base; prefs.apiKey = key; prefs.apiModel = model
        screen = Screen.Chat
    }
    private fun cfg() = Api.Cfg(prefs.apiBase, prefs.apiKey, prefs.apiModel)

    // ---------- always-listening (say the name) ----------
    var wakeOn by mutableStateOf(prefs.wakeOn); private set
    var wakeScreenOnly by mutableStateOf(prefs.wakeScreenOnly); private set
    private var wakeRetry = false
    fun updateWakeScreenOnly(v: Boolean) { wakeScreenOnly = v; prefs.wakeScreenOnly = v }
    private fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun setWake(on: Boolean) {
        wakeOn = on; prefs.wakeOn = on
        if (!on) { ctx.stopService(Intent(ctx, WakeService::class.java)); return }
        val need = mutableListOf<String>()
        if (!has(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) need.add(Manifest.permission.POST_NOTIFICATIONS)
        // Wake Mode only needs microphone + foreground-service permissions.
        // Do not force the user into the overlay settings just to enable Wake Mode.
        if (need.isNotEmpty()) { wakeRetry = true; permRequest = need } else startWake()
    }
    fun startWake() {
        if (!wakeOn || !has(Manifest.permission.RECORD_AUDIO)) return
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, WakeService::class.java))
        } catch (e: Throwable) {
            // Keep the toggle state, but surface the failure in logs instead of silently hiding it.
            android.util.Log.e("JARVIS-Wake", "Unable to start WakeService", e)
        }
    }
    /** Called when the background listener heard the name and opened the app. */
    fun onWake() {
        if (!configured) return
        screen = Screen.Chat
        wakeConversation = true
        followupJob?.cancel()
        // The background WakeService has just released the microphone. Keep the summon UI small.
        main.postDelayed({ startListening(wakeMode = true, waitSilently = true) }, 650)
    }

    // ---------- permissions ----------
    var permRequest by mutableStateOf<List<String>>(emptyList()); private set
    private var retryName: String? = null
    fun clearPermRequest() { permRequest = emptyList() }
    fun onPermResult() {
        if (wakeRetry) { wakeRetry = false; startWake() }
        val n = retryName ?: return
        retryName = null
        if (Contacts.hasRead(ctx)) {
            val ans = callByName(n)
            msgs.add(Msg("assistant", ans)); persist()
            if (speakTyped) say(ans)
        }
    }

    // ---------- chat state ----------
    val msgs = mutableStateListOf<Msg>()
    val history = mutableStateListOf<Convo>()
    private var convo = Convo(System.currentTimeMillis(), "", mutableListOf())
    var phase by mutableStateOf(Phase.Idle); private set
    var overlay by mutableStateOf(false); private set
    var overlayText by mutableStateOf(""); private set
    var webNote by mutableStateOf(false); private set
    private var job: Job? = null
    private var generating = false
    private var pendingCall: Pair<String, List<Contact>>? = null
    var wakeConversation by mutableStateOf(false); private set
    private var manualHasSpoken = false
    private var followupJob: Job? = null
    private var followupDeadline = 0L
    val busy get() = job?.isActive == true

    init {
        history.addAll(prefs.loadHistory())
        screen = if (configured) Screen.Chat else Screen.Setup
        if (wakeOn) startWake()
    }

    private var cache: List<Contact>? = null
    private var cacheAt = 0L
    private fun contacts(): List<Contact> {
        val now = System.currentTimeMillis()
        if (cache == null || now - cacheAt > 60_000) { cache = Contacts.load(ctx); cacheAt = now }
        return cache!!
    }

    // ---------- calling ----------
    fun dial(number: String, label: String): String {
        val n = number.filter { it.isDigit() || it == '+' }
        val canCall = devOk && Contacts.hasCall(ctx)
        if (devOk && !canCall) permRequest = listOf(Manifest.permission.CALL_PHONE)
        val i = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            if (canCall) tr("دارم به $label زنگ می‌زنم.", "Calling $label.")
            else tr("شماره‌گیر برای $label باز شد.", "Opened the dialer for $label.")
        } catch (e: Throwable) { tr("نتونستم تماس بگیرم.", "I couldn't place the call.") }
    }

    fun callNumber(number: String, label: String) { dial(number, label) }

    private fun callByName(raw: String): String {
        val q = Contacts.norm(raw)
        aliases[q]?.let { return dial(it, raw) }
        if (!Contacts.hasRead(ctx)) {
            retryName = raw
            permRequest = listOf(Manifest.permission.READ_CONTACTS)
            return tr("برای پیدا کردن «$raw» اجازه‌ی دسترسی به مخاطبین رو بده.", "Please allow contacts access so I can find \"$raw\".")
        }
        val list = contacts()
        if (devOk) choices[q]?.let { n -> if (list.any { it.number == n }) return dial(n, raw) }
        val found = Contacts.find(list, raw, fuzzy = devOk)
        return when {
            found.isEmpty() -> tr("«$raw» رو تو مخاطبینت پیدا نکردم.", "I couldn't find \"$raw\" in your contacts.")
            found.size == 1 -> dial(found[0].number, found[0].name)
            else -> {
                val top = found.take(3)
                pendingCall = q to top
                tr("کدوم یکی؟ ", "Which one? ") + top.mapIndexed { i, c -> "${i + 1}) ${c.name}" }.joinToString("  ")
            }
        }
    }

    private val ordinals = mapOf("اولی" to 0, "اول" to 0, "دومی" to 1, "دوم" to 1, "سومی" to 2, "سوم" to 2, "first" to 0, "second" to 1, "third" to 2)

    private fun resolvePending(t: String, viaVoice: Boolean): Boolean {
        val p = pendingCall ?: return false
        pendingCall = null
        val d = Contacts.norm(t)
        val idx = d.toIntOrNull()?.minus(1) ?: ordinals[d]
        val pick = if (idx != null && idx in p.second.indices) p.second[idx]
        else p.second.firstOrNull { d.isNotEmpty() && Contacts.norm(it.name).contains(d) }
        if (pick == null) return false
        if (devOk) { choices[p.first] = pick.number; prefs.saveMap("choices", choices) }
        local(t, viaVoice, dial(pick.number, pick.name))
        return true
    }

    // ---------- sending ----------
    fun send(text: String, viaVoice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        Wake.busy = true
        if (viaVoice) { overlay = true; overlayText = t; phase = Phase.Thinking }
        if (resolvePending(t, viaVoice)) return
        when (val c = Intents.parse(t)) {
            is Cmd.Call -> local(t, viaVoice, callByName(c.name))
            is Cmd.ShowContacts -> { local(t, viaVoice, tr("اینم مخاطبینت.", "Here are your contacts.")); screen = Screen.Contacts }
            is Cmd.Remember -> local(t, viaVoice, if (memoryAdd(c.text)) tr("یادم موند ✓", "Got it, I'll remember ✓") else tr("حافظه پره (نسخه‌ی دمو ۵ مورد).", "Memory is full (demo keeps 5 items)."))
            is Cmd.Forget -> { memoryClear(); local(t, viaVoice, tr("حافظه پاک شد.", "Memory cleared.")) }
            is Cmd.Search -> run(t, viaVoice, forceWeb = true, webQuery = c.q)
            is Cmd.None -> run(t, viaVoice)
        }
    }

    /** Answer produced on the phone (no AI call). */
    private fun local(userText: String, viaVoice: Boolean, answer: String) {
        msgs.add(Msg("user", userText)); msgs.add(Msg("assistant", answer))
        if (viaVoice) { overlay = true; overlayText = answer }
        persist()
        if (viaVoice || speakTyped) { phase = Phase.Speaking; say(answer) } else finishTurn()
    }

    private val actRe = Regex("""ACTION:\s*([A-Za-z_]+)\s*(?:\|\s*([^\n]*))?""")

    private fun run(text: String, viaVoice: Boolean, forceWeb: Boolean = false, webQuery: String? = null) {
        if (!configured) {
            local(text, viaVoice, tr("اول باید یه هوش مصنوعی (API) وصل کنی.", "Please connect an AI service (API) first."))
            screen = Screen.Setup; return
        }
        msgs.add(Msg("user", text))
        val idx = msgs.size
        msgs.add(Msg("assistant", ""))
        if (viaVoice) { overlay = true; overlayText = "" }
        phase = Phase.Thinking
        val speak = viaVoice || speakTyped
        val hist = msgs.subList(0, idx - 1).takeLast(6).filter { it.text.isNotBlank() }
            .map { (if (it.role == "user") "user" else "assistant") to it.text.take(600) }
        generating = true

        job = viewModelScope.launch {
            var usedWeb = false
            var userForModel = text
            if (webOn && (forceWeb || (devOk && Persona.needsWeb(text)))) {
                webNote = true
                val hits = WebSearch.search(webQuery ?: text, prefs.braveKey)
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
            val messages = ArrayList<Pair<String, String>>()
            messages.add("system" to Persona.system(assistantName, lang, humor, memory.toList()))
            messages.addAll(hist)
            messages.add("user" to userForModel)
            val r = Api.chat(cfg(), messages, if (devOk) 600 else 380)
            generating = false
            r.onSuccess { raw ->
                val clean = Actions.strip(raw).trim()
                var actMsg: String? = null
                val m = actRe.find(raw)
                if (m != null) {
                    val name = m.groupValues[1].lowercase()
                    val arg = m.groupValues[2].trim()
                    actMsg = when (name) {
                        "call_contact" -> callByName(arg)
                        "show_contacts" -> { screen = Screen.Contacts; null }
                        "remember" -> { memoryAdd(arg); null }
                        else -> Actions.run(ctx, raw, lang)
                    }
                }
                val finalText = listOfNotNull(clean.ifBlank { null }, actMsg.takeIf { clean.isBlank() || pendingCall != null })
                    .joinToString("\n").ifBlank { this@JarvisViewModel.s.empty }
                phase = Phase.Speaking
                show(finalText)
                persist()
                if (speak) say(finalText) else finishTurn()
            }.onFailure { e ->
                show(this@JarvisViewModel.s.error + (e.message ?: ""))
                persist(); finishTurn()
            }
        }
    }

    private fun say(chunk: String) {
        Wake.busy = true   // keep the lease fresh while speaking
        voice.speak(chunk, lang) { if (!generating) main.post { finishTurn() } }
    }

    private fun finishTurn() {
        phase = Phase.Idle
        if (wakeConversation) {
            // After the spoken answer, give the user exactly 3 seconds for a follow-up.
            main.post { listenForWakeFollowup() }
            return
        }
        viewModelScope.launch { delay(1200); if (phase == Phase.Idle) Wake.busy = false }
    }

    private fun listenForWakeFollowup(deadline: Long? = null) {
        if (!wakeConversation || busy) return
        followupJob?.cancel()
        followupDeadline = deadline ?: (System.currentTimeMillis() + 3_000L)
        Wake.busy = true
        voice.stopSpeaking()
        overlay = true; overlayText = ""; phase = Phase.Listening
        voice.listen(lang,
            onPartial = { overlayText = it },
            onFinal = {
                followupJob?.cancel()
                if (wakeConversation) send(it, true)
            },
            onFail = { _ ->
                if (wakeConversation && System.currentTimeMillis() < followupDeadline) {
                    main.postDelayed({ if (wakeConversation && phase == Phase.Listening) listenForWakeRetry() }, 120)
                } else {
                    returnToWakeMode()
                }
            })
        followupJob = viewModelScope.launch {
            delay(3_000L)
            if (wakeConversation && phase == Phase.Listening && System.currentTimeMillis() >= followupDeadline) {
                returnToWakeMode()
            }
        }
    }

    private fun listenForWakeRetry() {
        if (wakeConversation && phase == Phase.Listening && System.currentTimeMillis() < followupDeadline) {
            voice.stopListening()
            listenForWakeFollowup(followupDeadline)
        }
    }

    private fun returnToWakeMode() {
        followupJob?.cancel()
        voice.stopListening()
        phase = Phase.Idle
        overlay = false
        overlayText = ""
        wakeConversation = false
        Wake.busy = false
    }

    fun startListening(wakeMode: Boolean = false, waitSilently: Boolean = false) {
        if (busy && !wakeMode) return
        if (wakeMode) wakeConversation = true else manualHasSpoken = false
        Wake.busy = true
        voice.stopSpeaking()
        overlay = true; overlayText = ""; phase = Phase.Listening
        voice.listen(lang,
            onPartial = { overlayText = it; if (!wakeMode) manualHasSpoken = true },
            onFinal = { manualHasSpoken = true; send(it, true) },
            onFail = { error ->
                if (wakeMode || wakeConversation) {
                    // The first listen after saying the name is silent: no "I didn't hear you" yet.
                    if (waitSilently && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_CLIENT)) {
                        main.postDelayed({ if (wakeConversation) startListening(wakeMode = true, waitSilently = true) }, 180)
                    }
                } else if (!manualHasSpoken && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_CLIENT)) {
                    // Initial manual opening: simply keep waiting; don't scold the user before they speak.
                    main.postDelayed({ if (overlay && phase == Phase.Listening) startListening() }, 180)
                } else {
                    overlayText = s.micFail; phase = Phase.Idle; Wake.busy = false
                    viewModelScope.launch { delay(1500); if (phase == Phase.Idle) overlay = false }
                }
            })
    }

    fun stopAll() {
        followupJob?.cancel()
        wakeConversation = false
        job?.cancel(); voice.stopSpeaking(); voice.stopListening()
        generating = false; webNote = false
        phase = Phase.Idle; overlay = false; Wake.busy = false
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

    companion object {
        private const val LEGACY_DEV_HASH = "82b2587c09aa1d09417a4b90acbbc23af26744d802fc32840ece10e7f56e2cb1"
    }
}
