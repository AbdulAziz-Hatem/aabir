package com.abdulaziz.aabir.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class VoucherAnalyzer(
    private val minLen: Int,
    private val maxLen: Int,
    private val onPinDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastDetectionTime = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val now = System.currentTimeMillis()
                if (now - lastDetectionTime > 1500) { // منع التكرار اللحظي
                    val candidate = extractBestPin(visionText)
                    if (candidate != null) {
                        lastDetectionTime = now
                        onPinDetected(candidate)
                    }
                }
            }
            .addOnCompleteListener {
                // إغلاق الإطار حتمياً لتحرير ذاكرة العتاد
                imageProxy.close()
            }
    }

    private fun extractBestPin(visionText: com.google.mlkit.vision.text.Text): String? {
        val candidates = mutableListOf<Pair<String, Int>>() // Pair(digits, heightPx)

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val height = line.boundingBox?.height() ?: 0
                val cleanedText = cleanOcrMistakes(line.text)
                
                // استخراج المتواليات الرقمية
                val regex = Regex("""\b\d{4,16}\b""")
                val matches = regex.findAll(cleanedText)

                for (match in matches) {
                    val digits = match.value
                    if (isValidVoucherPin(digits)) {
                        candidates.add(Pair(digits, height))
                    }
                }
            }
        }

        // إعطاء الأولوية للخط الأكبر حجماً (خانة الخدش)، ثم للطول الأطول
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
        // استبعاد أرقام الهواتف في اليمن
        if (digits.matches(Regex("""^(77|78|73|71|70|01|02|03|04|05|06|07)\d{6,8}$"""))) return false
        // استبعاد أرقام خدمة العملاء
        if (digits.matches(Regex("""^(101|102|107|501|601|701|800)\d*$"""))) return false
        // استبعاد السيريال الطويل
        if (digits.length > 15) return false
        return true
    }
}
