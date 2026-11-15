package com.abdulaziz.aabir.camera

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.abdulaziz.aabir.network.MikrotikClient
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class VoucherAnalyzer(
    private val context: Context,
    private val minLen: Int,
    private val maxLen: Int,
    private val onPinDetected: (String) -> Unit,
    private val onGuidanceNeeded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isAnalyzing = AtomicBoolean(true)
    private val isInstantCaptureRequested = AtomicBoolean(false)
    private val hasDispatchedPin = AtomicBoolean(false)
    private var lastDetectionTime = 0L
    private var lastGuidanceTime = 0L
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    fun stop() {
        isAnalyzing.set(false)
    }

    fun triggerInstantCapture() {
        if (!hasDispatchedPin.get()) {
            isInstantCaptureRequested.set(true)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isAnalyzing.get() || hasDispatchedPin.get()) {
            imageProxy.close()
            return
        }

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

                val now = System.currentTimeMillis()
                val candidate = extractBestPin(visionText)

                if (isInstantCaptureRequested.getAndSet(false)) {
                    if (candidate != null) {
                        if (hasDispatchedPin.compareAndSet(false, true)) {
                            isAnalyzing.set(false)
                            mainExecutor.execute { onPinDetected(candidate) }
                        }
                    } else {
                        mainExecutor.execute {
                            onGuidanceNeeded("لم يتم العثور على أرقام واضحة تأكد من وضع الكرت أمام الكاميرا")
                        }
                    }
                    return@addOnSuccessListener
                }

                if (candidate != null) {
                    if (now - lastDetectionTime > 1500) {
                        lastDetectionTime = now
                        if (hasDispatchedPin.compareAndSet(false, true)) {
                            isAnalyzing.set(false)
                            mainExecutor.execute { onPinDetected(candidate) }
                        }
                    }
                } else {
                    if (now - lastGuidanceTime > 3000) {
                        lastGuidanceTime = now
                        calculateSpatialGuidance(visionText, frameWidth, frameHeight)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("AabirAnalyzer", "خطأ في تحليل إطار الكاميرا", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun calculateSpatialGuidance(
        visionText: com.google.mlkit.vision.text.Text,
        frameWidth: Int,
        frameHeight: Int
    ) {
        if (visionText.textBlocks.isEmpty()) {
            mainExecutor.execute { onGuidanceNeeded("ضع الكرت أمام الكاميرا") }
            return
        }

        val mainBlock = visionText.textBlocks.maxByOrNull { it.boundingBox?.height() ?: 0 }
        val box = mainBlock?.boundingBox ?: return

        val centerX = box.centerX()
        val centerY = box.centerY()
        val boxWidth = box.width()
        val boxHeight = box.height()

        val guideMsg = when {
            boxHeight < 20 || boxWidth < (frameWidth * 0.12) -> "اقترب بالهاتف قليلا"
            boxWidth > (frameWidth * 0.90) -> "ابتعد بالهاتف قليلا"
            centerX < (frameWidth * 0.20) -> "حرك الهاتف قليلا إلى اليسار"
            centerX > (frameWidth * 0.80) -> "حرك الهاتف قليلا إلى اليمين"
            centerY < (frameHeight * 0.20) -> "انزل الهاتف قليلا"
            centerY > (frameHeight * 0.80) -> "ارفع الهاتف قليلا"
            else -> "ثبت يدك جار القراءة"
        }

        mainExecutor.execute {
            if (isAnalyzing.get() && !hasDispatchedPin.get()) {
                onGuidanceNeeded(guideMsg)
            }
        }
    }

    private fun extractBestPin(visionText: com.google.mlkit.vision.text.Text): String? {
        val candidates = mutableListOf<Pair<String, Int>>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val height = line.boundingBox?.height() ?: 0
                val cleanedText = cleanOcrMistakes(line.text)
                
                // دمج الأرقام المتتالية المفصولة بمسافات أو شرطات (مثال: 4920-5819 -> 49205819)
                val joinedText = cleanedText.replace(Regex("""(?<=\d)[\s-]+(?=\d)"""), "")
                
                val regex = Regex("""\b\d{4,16}\b""")
                val matches = regex.findAll(joinedText)

                for (match in matches) {
                    val rawDigits = match.value
                    val asciiDigits = MikrotikClient.normalizeToAsciiDigits(rawDigits)
                    if (isValidVoucherPin(asciiDigits)) {
                        candidates.add(Pair(asciiDigits, height))
                    }
                }
            }
        }

        return candidates.maxWithOrNull(
            compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length }
        )?.first
    }

    private fun cleanOcrMistakes(text: String): String {
        return text
            .replace(Regex("[Oo]"), "0")
            .replace(Regex("[Il|]"), "1")
            .replace(Regex("[Ss]"), "5")
            .replace("B", "8")
    }

    private fun isValidVoucherPin(digits: String): Boolean {
        if (digits.length < minLen || digits.length > maxLen) return false
        if (digits.matches(Regex("""^(77|78|73|71|70|01|02|03|04|05|06|07)\d{6,8}$"""))) return false
        if (digits.matches(Regex("""^(101|102|107|501|601|701|800)\d*$"""))) return false
        if (digits.length > 15) return false
        return true
    }
}
