package com.jarvis.assistant.voice

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.jarvis.assistant.ui.Gold
import com.jarvis.assistant.ui.GoldText
import com.jarvis.assistant.ui.HoloOrb
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.Muted
import com.jarvis.assistant.ui.Panel
import com.jarvis.assistant.vm.JarvisViewModel
import com.jarvis.assistant.vm.Phase

/** Floating JARVIS window, similar in behavior to XRecorder's floating UI. */
class OverlayService : Service() {
    companion object {
        const val ACTION_WAKE = "com.jarvis.assistant.action.WAKE_OVERLAY"
    }

    private lateinit var wm: WindowManager
    private var root: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var vm: JarvisViewModel? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_WAKE) {
            try { showWakeOverlay() } catch (t: Throwable) {
                android.util.Log.e("JARVIS-Overlay", "overlay failed, opening app instead", t)
                cleanup()
                runCatching { startActivity(Intent(this, com.jarvis.assistant.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("wake", true)) }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showWakeOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf(); return
        }
        if (root != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        vm = JarvisViewModel.current ?: JarvisViewModel(application)
        val life = OverlayLifecycleOwner().also { it.start() }
        owner = life
        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(life)
        ViewTreeSavedStateRegistryOwner.set(view, life)
        view.setContent {
            JarvisTheme {
                val model = vm ?: return@JarvisTheme
                Column(
                    Modifier
                        .widthIn(min = 300.dp, max = 360.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Panel)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(model.assistantName, color = Gold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Close, model.tr("بستن", "Close"), tint = Muted,
                            modifier = Modifier.size(28.dp).clickable { stopOverlay() })
                    }
                    HoloOrb(model.phase, 125.dp, Modifier.align(Alignment.CenterHorizontally))
                    val status = when (model.phase) {
                        Phase.Listening -> model.s.stListen
                        Phase.Thinking -> model.s.stThink
                        Phase.Speaking -> model.s.stSpeak
                        Phase.Idle -> model.s.greeting
                    }
                    Text(model.overlayText.ifBlank { status }, color = GoldText, fontSize = 14.sp, lineHeight = 21.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.size(50.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF101A28)).clickable { model.startListening(wakeMode = true, waitSilently = false) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Mic, model.s.mic, tint = Gold, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
        root = view
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            dp(340), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER; x = 0; y = 0 }
        try { wm.addView(view, lp) } catch (e: Throwable) { stopOverlay(); return }
        vm?.onWake()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun stopOverlay() {
        vm?.stopAll()
        cleanup()
        stopSelf()
    }

    private fun cleanup() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        owner?.stop(); owner = null
        vm = null
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        init { controller.performAttach(); controller.performRestore(null) }
        fun start() { registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE); registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        fun stop() { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
    }
}
