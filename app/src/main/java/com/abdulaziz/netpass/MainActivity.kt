package com.abdulaziz.netpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.abdulaziz.netpass.feedback.FeedbackManager
import com.abdulaziz.netpass.model.StorageManager
import com.abdulaziz.netpass.ui.NetPassTheme
import com.abdulaziz.netpass.ui.MainScreen
import com.abdulaziz.netpass.ui.screens.WelcomeScreen

class MainActivity : ComponentActivity() {

    private lateinit var feedback: FeedbackManager
    private lateinit var storage: StorageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        storage = StorageManager(this)
        val initialSettings = storage.loadSettings()
        feedback = FeedbackManager(this, initialSettings.selectedTtsEngine)

        setContent {
            var showWelcome by remember { mutableStateOf(storage.loadSettings().isFirstLaunch) }

            NetPassTheme {
                if (showWelcome) {
                    WelcomeScreen(
                        onGetStarted = {
                            val current = storage.loadSettings()
                            storage.saveSettings(current.copy(isFirstLaunch = false))
                            showWelcome = false
                        }
                    )
                } else {
                    MainScreen(
                        feedback = feedback,
                        onOpenOnboarding = { showWelcome = true }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        feedback.release()
    }
}
