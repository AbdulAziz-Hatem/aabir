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
        VoucherAnalyzer(context, minLen, maxLen, onPinDetected, onGuidanceNeeded)
    }

    // تفعيل الالتقاط الفوري عند ضغط زر الالتقاط
    LaunchedEffect(instantCaptureTrigger) {
        if (instantCaptureTrigger > 0) {
            analyzer.triggerInstantCapture()
        }
    }

    // التحكم في فلاش الكاميرا عبر العتاد
    LaunchedEffect(torchEnabled, camera) {
        try {
            camera?.cameraControl?.enableTorch(torchEnabled)
        } catch (ignored: Exception) {}
    }

    // تطبيق نسبة التقريب لتكبير الكروت الصغيرة كعقلة الإصبع
    LaunchedEffect(zoomRatio, camera) {
        try {
            camera?.cameraControl?.setZoomRatio(zoomRatio)
        } catch (ignored: Exception) {}
    }

    // نبضة الرادار الصوتية والتركيز التلقائي المركزي كل 2.8 ثانية
    LaunchedEffect(radarEnabled, camera) {
        while (isActive) {
            delay(2800)
            if (radarEnabled) onRadarPulse()

            // إعادة توجيه بؤرة التركيز نحو المركز حيث يوجد الكرت الصغير
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
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )

                    // ضبط الزوم الافتراضي فور ربط الكاميرا
                    camera?.cameraControl?.setZoomRatio(zoomRatio)
                } catch (ignored: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
    )
}
