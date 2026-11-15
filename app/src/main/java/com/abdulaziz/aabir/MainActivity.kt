package com.abdulaziz.aabir

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.abdulaziz.aabir.feedback.FeedbackManager
import com.abdulaziz.aabir.model.StorageManager
import com.abdulaziz.aabir.ui.AabirTheme
import com.abdulaziz.aabir.ui.MainScreen
import com.abdulaziz.aabir.ui.screens.WelcomeScreen

class MainActivity : ComponentActivity() {

    private lateinit var feedback: FeedbackManager
    private lateinit var storage: StorageManager
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        storage = StorageManager(this)
        val initialSettings = storage.loadSettings()
        feedback = FeedbackManager(this, initialSettings.selectedTtsEngine)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        try {
            val ids = cameraManager?.cameraIdList
            if (!ids.isNullOrEmpty()) {
                cameraId = ids[0]
            }
        } catch (ignored: Exception) {}

        setContent {
            var showWelcome by remember { mutableStateOf(storage.loadSettings().isFirstLaunch) }

            AabirTheme {
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
                        onOpenOnboarding = { showWelcome = true },
                        onToggleTorch = { enabled ->
                            try {
                                if (cameraId != null) {
                                    cameraManager?.setTorchMode(cameraId!!, enabled)
                                }
                            } catch (ignored: Exception) {}
                        }
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
