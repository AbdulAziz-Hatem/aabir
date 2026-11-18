package com.abdulaziz.netpass

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.abdulaziz.netpass.ui.AppDestination
import com.abdulaziz.netpass.ui.MainScreen
import com.abdulaziz.netpass.ui.MainViewModel
import com.abdulaziz.netpass.ui.NetPassTheme
import com.abdulaziz.netpass.ui.screens.WelcomeScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory((application as NetPassApplication).appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold splash until DataStore emits the persisted theme and first-launch flag.
        splashScreen.setKeepOnScreenCondition { !viewModel.uiState.value.isInitialized }
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val darkTheme = uiState.settings.isDarkTheme

            LaunchedEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            NetPassTheme(darkTheme = darkTheme) {
                if (uiState.destination == AppDestination.Welcome) {
                    WelcomeScreen(
                        onGetStarted = viewModel::completeOnboarding
                    )
                } else {
                    MainScreen(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
