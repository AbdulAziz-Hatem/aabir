package com.abdulaziz.aabir.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.sin

class FeedbackManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ar")
            isTtsReady = true
        }
    }

    // نطق التنبيهات الصوتية
    fun speak(text: String, enabled: Boolean, voiceName: String = "") {
        if (!enabled || !isTtsReady) return
        tts?.let { engine ->
            if (voiceName.isNotEmpty()) {
                val found = engine.voices?.find { it.name == voiceName }
                if (found != null) engine.voice = found
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AabirUtteranceId")
        }
    }

    // جلب قائمة الأصوات المثبتة
    fun getAvailableVoices(): List<Voice> {
        return tts?.voices?.filter { it.locale.language.startsWith("ar") || it.locale.language.startsWith("en") }
            ?.toList() ?: emptyList()
    }

    // توليد نغمة نقية بالتردد المحدد (PCM Sine Wave Synthesis)
    private fun playTone(freqHz: Double, durationMs: Int, volume: Float = 0.8f) {
        thread {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val generatedSnd = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / freqHz)
                    // تطبيق Envelope خفيف لمنع طقطقة الصوت (Anti-click Fade In/Out)
                    val fadeFactor = when {
                        i < 200 -> i / 200.0
                        i > numSamples - 400 -> (numSamples - i) / 400.0
                        else -> 1.0
                    }
                    val sample = (sin(angle) * 32767 * volume * fadeFactor).toInt()
                    generatedSnd[i] = sample.toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(generatedSnd, 0, numSamples)
                track.play()
                Thread.sleep(durationMs.toLong() + 30)
                track.release()
            } catch (ignored: Exception) {}
        }
    }

    // كورد النجاح الثلاثي المتصاعد (C5 -> E5 -> G5)
    fun playSuccessChord() {
        thread {
            playTone(523.25, 90, 0.7f) // C5
            Thread.sleep(70)
            playTone(659.25, 90, 0.7f) // E5
            Thread.sleep(70)
            playTone(783.99, 180, 0.8f) // G5
        }
    }

    // نغمة الخطأ الحاسمة الهابطة (280Hz -> 110Hz)
    fun playErrorTone() {
        thread {
            playTone(280.0, 120, 0.8f)
            Thread.sleep(60)
            playTone(130.0, 200, 0.85f)
        }
    }

    // تكتكة الرادار الناعمة والهادئة (880Hz لمدة 25ms فقط)
    fun playRadarTick() {
        playTone(880.0, 25, 0.25f)
    }

    // اهتزاز النجاح: نبضتان ناعمتان متتاليتان
    fun vibrateSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 60, 160), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 90, 60, 160), -1)
        }
    }

    // اهتزاز الفشل: ثلاث نبضات قوية متقطعة
    fun vibrateError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 80, 220, 80, 220), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 220, 80, 220, 80, 220), -1)
        }
    }

    // اهتزاز التقاط الرمز: نبضة خفيفة واحدة
    fun vibrateFound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(70)
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
    }
}
