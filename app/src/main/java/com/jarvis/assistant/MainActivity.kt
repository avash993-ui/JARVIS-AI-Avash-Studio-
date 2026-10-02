package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.assistant.ui.ChatScreen
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.ModelScreen
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
                    if (vm.screen == Screen.Chat) ChatScreen(vm) else ModelScreen(vm)
                }
            }
        }
    }
}
