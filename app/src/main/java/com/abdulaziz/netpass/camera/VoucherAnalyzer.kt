package com.abdulaziz.netpass.camera

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.abdulaziz.netpass.domain.VoucherValidator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class VoucherAnalyzer(
    context: Context,
    minLen: Int,
    maxLen: Int,
    private val onPinDetected: (String) -> Unit,
    private val onGuidanceNeeded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isAnalyzing = AtomicBoolean(true)
    private val isInstantCaptureRequested = AtomicBoolean(false)
    private val hasDispatchedPin = AtomicBoolean(false)

    @Volatile
    private var currentMinLen: Int

    @Volatile
    private var currentMaxLen: Int

    private var lastAnalyzedFrameTime = 0L
    private var lastGuidanceTime = 0L
    private var lastSpokenGuidance = ""
    private var hasPromptedEmpty = false

    private var lastCenterX = 0
    private var lastCenterY = 0
    private var stableFrameCount = 0

    private val mainExecutor = ContextCompat.getMainExecutor(context.applicationContext)

    init {
        val (safeMin, safeMax) = VoucherValidator.normalizeBounds(minLen, maxLen)
        currentMinLen = safeMin
        currentMaxLen = safeMax
    }

    fun updateBounds(minLen: Int, maxLen: Int) {
        val (safeMin, safeMax) = VoucherValidator.normalizeBounds(minLen, maxLen)
        currentMinLen = safeMin
        currentMaxLen = safeMax
    }

    fun stop() {
        isAnalyzing.set(false)
    }

    fun close() {
        stop()
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ML Kit TextRecognizer", e)
        }
    }

    fun triggerInstantCapture() {
        if (!hasDispatchedPin.get()) {
            isInstantCaptureRequested.set(true)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        // Throttle to ~10 FPS during guidance to save battery and thermals.
        if (!isAnalyzing.get() || hasDispatchedPin.get() ||
            (now - lastAnalyzedFrameTime < 95 && !isInstantCaptureRequested.get())
        ) {
            imageProxy.close()
            return
        }
        lastAnalyzedFrameTime = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val frameWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val frameHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                if (!isAnalyzing.get() || hasDispatchedPin.get()) return@addOnSuccessListener

                val candidate = extractBestPin(visionText)

                if (isInstantCaptureRequested.getAndSet(false)) {
                    if (candidate != null) {
                        dispatchFinalPin(candidate)
                    } else {
                        mainExecutor.execute {
                            onGuidanceNeeded("لم يتم العثور على أرقام واضحة تأكد من موضع الكرت")
                        }
                    }
                    return@addOnSuccessListener
                }

                val isSweetSpot = checkSweetSpotAndGuide(visionText, frameWidth, frameHeight, now)

                if (isSweetSpot && candidate != null) {
                    dispatchFinalPin(candidate)
                } else if (candidate != null && stableFrameCount >= 2) {
                    dispatchFinalPin(candidate)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "خطأ في تحليل إطار الكاميرا", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun dispatchFinalPin(pin: String) {
        if (hasDispatchedPin.compareAndSet(false, true)) {
            isAnalyzing.set(false)
            mainExecutor.execute {
                onPinDetected(pin)
            }
        }
    }

    private fun checkSweetSpotAndGuide(
        visionText: Text,
        frameWidth: Int,
        frameHeight: Int,
        now: Long
    ): Boolean {
        if (visionText.textBlocks.isEmpty()) {
            stableFrameCount = 0
            if (!hasPromptedEmpty && now - lastGuidanceTime > 2500) {
                hasPromptedEmpty = true
                lastGuidanceTime = now
                mainExecutor.execute { onGuidanceNeeded("ضع الكرت أمام الكاميرا") }
            }
            return false
        }

        hasPromptedEmpty = false
        val mainBlock = visionText.textBlocks.maxByOrNull { it.boundingBox?.height() ?: 0 }
        val box = mainBlock?.boundingBox ?: return false

        val centerX = box.centerX()
        val centerY = box.centerY()
        val boxWidth = box.width()
        val boxHeight = box.height()

        val deltaX = abs(centerX - lastCenterX)
        val deltaY = abs(centerY - lastCenterY)
        lastCenterX = centerX
        lastCenterY = centerY

        if (deltaX < (frameWidth * 0.05) && deltaY < (frameHeight * 0.05)) {
            stableFrameCount++
        } else {
            stableFrameCount = 0
        }

        val isCenteredX = centerX in (frameWidth * 0.30).toInt()..(frameWidth * 0.70).toInt()
        val isCenteredY = centerY in (frameHeight * 0.25).toInt()..(frameHeight * 0.75).toInt()
        val isGoodScale = boxHeight >= 20 && boxWidth in (frameWidth * 0.14).toInt()..(frameWidth * 0.88).toInt()

        if (isCenteredX && isCenteredY && isGoodScale && stableFrameCount >= 2) {
            return true
        }

        val currentCue = when {
            boxHeight < 20 || boxWidth < (frameWidth * 0.14) -> "قرّب الهاتف من الكرت"
            boxWidth > (frameWidth * 0.88) -> "أبعد الهاتف قليلاً عن الكرت"
            centerX < (frameWidth * 0.28) -> "حرّك الهاتف قليلاً لليمين"
            centerX > (frameWidth * 0.72) -> "حرّك الهاتف قليلاً لليسار"
            centerY < (frameHeight * 0.25) -> "حرّك الهاتف للأمام"
            centerY > (frameHeight * 0.75) -> "حرّك الهاتف للخلف"
            else -> null
        }

        if (currentCue != null) {
            val isDirectionChanged = (currentCue != lastSpokenGuidance)
            val isCadenceElapsed = (now - lastGuidanceTime >= 1600)

            if (isDirectionChanged || isCadenceElapsed) {
                lastSpokenGuidance = currentCue
                lastGuidanceTime = now
                mainExecutor.execute {
                    if (isAnalyzing.get() && !hasDispatchedPin.get()) {
                        onGuidanceNeeded(currentCue)
                    }
                }
            }
        }

        return false
    }

    private fun extractBestPin(visionText: Text): String? {
        val min = currentMinLen
        val max = currentMaxLen
        val candidates = mutableListOf<Pair<String, Int>>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val height = line.boundingBox?.height() ?: 0
                val matches = VoucherValidator.extractCandidatesFromLine(
                    lineText = line.text,
                    minLen = min,
                    maxLen = max
                )
                for (digits in matches) {
                    candidates.add(digits to height)
                }
            }
        }

        return candidates.maxWithOrNull(
            compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length }
        )?.first
    }

    companion object {
        private const val TAG = "NetPassAnalyzer"
    }
}
