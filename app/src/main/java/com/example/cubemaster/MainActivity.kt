package com.example.cubemaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.example.cubemaster.data.remote.AuthRepository
import com.example.cubemaster.ui.navigation.AppNavigation
import com.example.cubemaster.ui.navigation.Screen
import com.example.cubemaster.ui.theme.CubeMasterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CubeMasterTheme(darkTheme = isSystemInDarkTheme()) {
                val startDestination = if (auth.currentUser != null)
                    Screen.Projects.route
                else
                    Screen.Auth.route
                AppNavigation(startDestination = startDestination)
            }
        }
    }
}
