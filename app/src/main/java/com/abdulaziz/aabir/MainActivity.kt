package com.abdulaziz.aabir

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.abdulaziz.aabir.feedback.FeedbackManager
import com.abdulaziz.aabir.ui.AabirTheme
import com.abdulaziz.aabir.ui.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var feedback: FeedbackManager
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        feedback = FeedbackManager(this)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        try {
            val ids = cameraManager?.cameraIdList
            if (!ids.isNullOrEmpty()) {
                cameraId = ids[0]
            }
        } catch (ignored: Exception) {}

        setContent {
            var hasCameraPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasCameraPermission = isGranted
            }

            LaunchedEffect(Unit) {
                if (!hasCameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            AabirTheme {
                MainScreen(
                    feedback = feedback,
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

    override fun onDestroy() {
        super.onDestroy()
        feedback.release()
    }
}
