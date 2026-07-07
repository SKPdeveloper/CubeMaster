package com.example.cubemaster

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.ui.navigation.AppNavigation
import com.example.cubemaster.ui.theme.CubeMasterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: AuthRepository

    private var pendingShortcutAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingShortcutAction = consumeShortcutAction(intent)
        setContent {
            CubeMasterTheme(darkTheme = isSystemInDarkTheme()) {
                var isReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    auth.ensureSignedIn()
                    isReady = true
                }
                if (isReady) {
                    AppNavigation(pendingShortcutAction = pendingShortcutAction)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShortcutAction = consumeShortcutAction(intent)
    }

    // Ярлик застосунку (shortcuts.xml) передає дію одноразово: прибираємо extra з Intent,
    // щоб пересворення Activity (напр. поворот екрана) не повторювало навігацію.
    private fun consumeShortcutAction(intent: Intent?): String? {
        val action = intent?.getStringExtra(EXTRA_SHORTCUT_ACTION)
        intent?.removeExtra(EXTRA_SHORTCUT_ACTION)
        return action
    }

    companion object {
        private const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
    }
}
