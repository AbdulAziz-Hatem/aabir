package com.abdulaziz.aabir.camera

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
    onSweetSpotLocked: () -> Unit = {},
    onRadarPulse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember(minLen, maxLen) {
        VoucherAnalyzer(context, minLen, maxLen, onPinDetected, onGuidanceNeeded, onSweetSpotLocked)
    }

    LaunchedEffect(instantCaptureTrigger) {
        if (instantCaptureTrigger > 0) {
            analyzer.triggerInstantCapture()
        }
    }

    LaunchedEffect(torchEnabled, camera) {
        try {
            camera?.cameraControl?.enableTorch(torchEnabled)
        } catch (ignored: Exception) {}
    }

    // التحصين الحسابي للزوم: ملاءمة نسبة التقريب مع الحد الأقصى الذي يدعمه عتاد الهاتف
    LaunchedEffect(zoomRatio, camera) {
        try {
            camera?.let { cam ->
                val zoomState = cam.cameraInfo.zoomState.value
                val maxZoom = zoomState?.maxZoomRatio ?: 1.0f
                val minZoom = zoomState?.minZoomRatio ?: 1.0f
                val safeZoom = zoomRatio.coerceIn(minZoom, maxZoom)
                cam.cameraControl.setZoomRatio(safeZoom)
            }
        } catch (ignored: Exception) {}
    }

    LaunchedEffect(radarEnabled, camera) {
        while (isActive) {
            delay(2800)
            if (radarEnabled) onRadarPulse()

            try {
                val pView = previewViewRef
                if (pView != null && pView.width > 0 && pView.height > 0) {
                    val factory = pView.meteringPointFactory
                    val centerPoint = factory.createPoint(pView.width / 2f, pView.height / 2f)
                    val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(2500, TimeUnit.MILLISECONDS)
                        .build()
                    camera?.cameraControl?.startFocusAndMetering(action)
                }
            } catch (ignored: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.stop()
            try {
                camera?.cameraControl?.enableTorch(false)
                cameraProviderInstance?.unbindAll()
            } catch (ignored: Exception) {}
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

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    camera = boundCamera

                    // ضبط التقريب الآمن فور بدء الكاميرا
                    val zoomState = boundCamera.cameraInfo.zoomState.value
                    val maxZoom = zoomState?.maxZoomRatio ?: 1.0f
                    val minZoom = zoomState?.minZoomRatio ?: 1.0f
                    boundCamera.cameraControl.setZoomRatio(zoomRatio.coerceIn(minZoom, maxZoom))
                } catch (ignored: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
    )
}
