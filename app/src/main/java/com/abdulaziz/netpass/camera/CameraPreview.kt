package com.abdulaziz.netpass.camera

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "CameraPreview"

@Composable
fun CameraPreview(
    torchEnabled: Boolean,
    zoomRatio: Float,
    instantCaptureTrigger: Int,
    radarEnabled: Boolean,
    minLen: Int,
    maxLen: Int,
    onPinDetected: (String) -> Unit,
    onGuidanceNeeded: (String) -> Unit,
    onRadarPulse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnPinDetected by rememberUpdatedState(onPinDetected)
    val currentOnGuidanceNeeded by rememberUpdatedState(onGuidanceNeeded)
    val currentOnRadarPulse by rememberUpdatedState(onRadarPulse)
    val currentZoomRatio by rememberUpdatedState(zoomRatio)

    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember {
        VoucherAnalyzer(
            context = context,
            minLen = minLen,
            maxLen = maxLen,
            onPinDetected = { pin -> currentOnPinDetected(pin) },
            onGuidanceNeeded = { msg -> currentOnGuidanceNeeded(msg) }
        )
    }

    LaunchedEffect(minLen, maxLen) {
        analyzer.updateBounds(minLen, maxLen)
    }

    LaunchedEffect(instantCaptureTrigger) {
        if (instantCaptureTrigger > 0) {
            analyzer.triggerInstantCapture()
        }
    }

    LaunchedEffect(torchEnabled, camera) {
        try {
            camera?.cameraControl?.enableTorch(torchEnabled)
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating torch state=$torchEnabled", e)
        }
    }

    LaunchedEffect(zoomRatio, camera) {
        try {
            camera?.let { cam ->
                val zoomState = cam.cameraInfo.zoomState.value
                val maxZoom = zoomState?.maxZoomRatio ?: 1.0f
                val minZoom = zoomState?.minZoomRatio ?: 1.0f
                cam.cameraControl.setZoomRatio(zoomRatio.coerceIn(minZoom, maxZoom))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying zoom ratio=$zoomRatio", e)
        }
    }

    LaunchedEffect(radarEnabled, camera) {
        while (isActive) {
            delay(2800)
            if (radarEnabled) currentOnRadarPulse()

            try {
                val pView = previewViewRef
                if (pView != null && pView.width > 0 && pView.height > 0) {
                    val factory = pView.meteringPointFactory
                    val centerPoint = factory.createPoint(pView.width / 2f, pView.height / 2f)
                    val action = FocusMeteringAction.Builder(
                        centerPoint,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    )
                        .setAutoCancelDuration(2500, TimeUnit.MILLISECONDS)
                        .build()
                    camera?.cameraControl?.startFocusAndMetering(action)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Periodic center autofocus failed", e)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            try {
                camera?.cameraControl?.enableTorch(false)
                cameraProviderInstance?.unbindAll()
            } catch (e: Exception) {
                Log.w(TAG, "Failed releasing camera bindings", e)
            }
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            previewViewRef = previewView
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProviderInstance = cameraProvider

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, analyzer)
                        }

                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    camera = boundCamera

                    val zoomState = boundCamera.cameraInfo.zoomState.value
                    val maxZoom = zoomState?.maxZoomRatio ?: 1.0f
                    val minZoom = zoomState?.minZoomRatio ?: 1.0f
                    boundCamera.cameraControl.setZoomRatio(currentZoomRatio.coerceIn(minZoom, maxZoom))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed binding CameraX use cases", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
    )
}
