package com.abdulaziz.netpass.camera

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.abdulaziz.netpass.network.MikrotikClient
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class VoucherAnalyzer(
    private val context: Context,
    private val minLen: Int,
    private val maxLen: Int,
    private val onPinDetected: (String) -> Unit,
    private val onGuidanceNeeded: (String) -> Unit,
    private val onSweetSpotLocked: () -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isAnalyzing = AtomicBoolean(true)
    private val isInstantCaptureRequested = AtomicBoolean(false)
    private val hasDispatchedPin = AtomicBoolean(false)
    
    // إدارة الأداء وتوفير البطارية (10 إطارات/ثانية أثناء التوجيه)
    private var lastAnalyzedFrameTime = 0L
    private var lastGuidanceTime = 0L
    private var lastSpokenGuidance = ""
    private var hasPromptedEmpty = false

    // تتبع استقرار اليد في البؤرة الذهبية
    private var lastCenterX = 0
    private var lastCenterY = 0
    private var stableFrameCount = 0

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
        val now = System.currentTimeMillis()

        // فحص مهلة توفير الطاقة (10 FPS) لحماية البطارية والمعالج من السخونة
        if (!isAnalyzing.get() || hasDispatchedPin.get() || (now - lastAnalyzedFrameTime < 95 && !isInstantCaptureRequested.get())) {
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

                // إذا طلب المستخدم الالتقاط الفوري اليدوي
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

                // فحص البؤرة الذهبية والتوجيه المكتبي اللحظي
                val isSweetSpot = checkSweetSpotAndGuide(visionText, frameWidth, frameHeight, now)

                // عند تحقق شروط البؤرة الذهبية واستقرار اليد -> التقاط ذاتي فوري!
                if (isSweetSpot && candidate != null) {
                    dispatchFinalPin(candidate)
                } else if (candidate != null && stableFrameCount >= 2) {
                    dispatchFinalPin(candidate)
                }
            }
            .addOnFailureListener { e ->
                Log.e("NetPassAnalyzer", "خطأ في تحليل إطار الكاميرا", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun dispatchFinalPin(pin: String) {
        if (hasDispatchedPin.compareAndSet(false, true)) {
            isAnalyzing.set(false)
            mainExecutor.execute {
                onSweetSpotLocked()
                onPinDetected(pin)
            }
        }
    }

    // خوارزمية التوجيه المكتبي المزدوجة المتطورة (Dual-Cadence Guidance Engine)
    private fun checkSweetSpotAndGuide(
        visionText: com.google.mlkit.vision.text.Text,
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

        // قياس استقرار يد الكفيف فوق الطاولة
        val deltaX = abs(centerX - lastCenterX)
        val deltaY = abs(centerY - lastCenterY)
        lastCenterX = centerX
        lastCenterY = centerY

        if (deltaX < (frameWidth * 0.05) && deltaY < (frameHeight * 0.05)) {
            stableFrameCount++
        } else {
            stableFrameCount = 0
        }

        // شروط البؤرة الذهبية الدقيقة للكروت المصغرة
        val isCenteredX = centerX in (frameWidth * 0.30).toInt()..(frameWidth * 0.70).toInt()
        val isCenteredY = centerY in (frameHeight * 0.25).toInt()..(frameHeight * 0.75).toInt()
        val isGoodScale = boxHeight >= 20 && boxWidth in (frameWidth * 0.14).toInt()..(frameWidth * 0.88).toInt()

        if (isCenteredX && isCenteredY && isGoodScale && stableFrameCount >= 2) {
            return true // الكرت مستقر وجاهز في البؤرة الذهبية
        }

        // تحديد المتجه الحركي الصريح الخالي من الغموض
        val currentCue = when {
            boxHeight < 20 || boxWidth < (frameWidth * 0.14) -> "اقترب بالهاتف"
            boxWidth > (frameWidth * 0.88) -> "ابتعد بالهاتف"
            centerX < (frameWidth * 0.28) -> "حرّك لليسار"
            centerX > (frameWidth * 0.72) -> "حرّك لليمين"
            centerY < (frameHeight * 0.25) -> "أنزل الهاتف"
            centerY > (frameHeight * 0.75) -> "ارفع الهاتف"
            else -> null // داخل النطاق المركزي -> صمت تام ومريح
        }

        if (currentCue != null) {
            val isDirectionChanged = (currentCue != lastSpokenGuidance)
            val isCadenceElapsed = (now - lastGuidanceTime >= 1600) // إعادة النطق الإيقاعي كل 1.6 ثانية

            // إذا تغير الاتجاه: انطق فوراً (0ms). وإذا كان في نفس الاتجاه: أعد التوجيه كل 1.6 ثانية لطمأنة الكفيف
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

    private fun extractBestPin(visionText: com.google.mlkit.vision.text.Text): String? {
        val candidates = mutableListOf<Pair<String, Int>>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val height = line.boundingBox?.height() ?: 0
                val cleanedText = cleanOcrMistakes(line.text)
                
                // دمج الأرقام المتتالية المفصولة بمسافات أو شرطات
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
