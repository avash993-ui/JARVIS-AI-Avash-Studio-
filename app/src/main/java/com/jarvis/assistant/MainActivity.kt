package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.assistant.ui.ChatScreen
import com.jarvis.assistant.ui.ContactsScreen
import com.jarvis.assistant.ui.Gold
import com.jarvis.assistant.ui.IntegrityGate
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.SetupScreen
import com.jarvis.assistant.ui.TermsGate
import com.jarvis.assistant.ui.TutorialScreen
import com.jarvis.assistant.vm.JarvisViewModel
import com.jarvis.assistant.vm.Screen

class MainActivity : ComponentActivity() {
    private val model: JarvisViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("wake", false)) { intent.removeExtra("wake"); model.onWake() }
    }

    override fun onResume() {
        super.onResume()
        if (model.wakeOn && android.os.Build.VERSION.SDK_INT >= 23 && android.provider.Settings.canDrawOverlays(this)) {
            model.startWake()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("wake", false)) { intent.removeExtra("wake"); model.onWake() }
        setContent {
            val vm: JarvisViewModel = viewModel()
            val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermResult() }
            LaunchedEffect(vm.permRequest) {
                if (vm.permRequest.isNotEmpty()) {
                    val p = vm.permRequest.toTypedArray()
                    vm.clearPermRequest()
                    perm.launch(p)
                }
            }
            BackHandler(enabled = vm.screen != Screen.Chat) { vm.back() }
            JarvisTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (vm.lang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize()) {
                        when (vm.screen) {
                            Screen.Chat -> ChatScreen(vm)
                            Screen.Setup -> SetupScreen(vm)
                            Screen.Tutorial -> TutorialScreen(vm)
                            Screen.Contacts -> ContactsScreen(vm)
                        }
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
