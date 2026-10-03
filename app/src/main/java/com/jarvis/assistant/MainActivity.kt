package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.assistant.ui.ChatScreen
import com.jarvis.assistant.ui.Gold
import com.jarvis.assistant.ui.IntegrityGate
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.ModelScreen
import com.jarvis.assistant.ui.TermsGate
import com.jarvis.assistant.vm.JarvisViewModel
import com.jarvis.assistant.vm.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: JarvisViewModel = viewModel()
            JarvisTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (vm.lang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize()) {
                        if (vm.screen == Screen.Chat) ChatScreen(vm) else ModelScreen(vm)
                        // creator signature on every screen
                        Text(
                            "AVASH STUDIO", color = Gold.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 3.sp,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                        )
                        if (vm.screen == Screen.Chat) TermsGate(vm)
                        IntegrityGate(vm)
                    }
                }
            }
        }
    }
}
