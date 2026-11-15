package com.abdulaziz.netpass.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.sin

data class TtsEngineItem(val packageName: String, val label: String)

class FeedbackManager(private val context: Context, private var enginePackage: String = "") : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val audioScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val sampleRate = 44100
    private val radarBuffer: ShortArray by lazy { generateSineBuffer(880.0, 25, 0.25f) }
    private val errorBuffer: ShortArray by lazy { generateErrorBuffer() }
    private val successBuffer: ShortArray by lazy { generateSuccessChordBuffer() }

    init {
        initTts(enginePackage)
    }

    private fun initTts(pkg: String) {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (ignored: Exception) {}
        isTtsReady = false
        enginePackage = pkg
        tts = if (pkg.isNotEmpty()) {
            TextToSpeech(context, this, pkg)
        } else {
            TextToSpeech(context, this)
        }
    }

    // تغيير محرك تحويل النص إلى كلام ديناميكياً
    fun setEngine(pkg: String) {
        if (pkg != enginePackage) {
            initTts(pkg)
        }
    }

    // قراءة كافة محركات تحويل النص إلى كلام المثبتة على الهاتف (Google, Samsung, Vocalizer, etc.)
    fun getInstalledEngines(): List<TtsEngineItem> {
        return try {
            tts?.engines?.map { TtsEngineItem(it.name, it.label) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ar")
            isTtsReady = true
        }
    }

    fun speak(text: String, enabled: Boolean, voiceName: String = "") {
        if (!enabled || !isTtsReady) return
        tts?.let { engine ->
            if (voiceName.isNotEmpty()) {
                val found = engine.voices?.find { it.name == voiceName }
                if (found != null) engine.voice = found
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NetPassUtteranceId")
        }
    }

    // إرسال Toast نظام حقيقي لقارئ الشاشة (TalkBack / Jieshuo)
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun getAvailableVoices(): List<Voice> {
        return tts?.voices?.filter { it.locale.language.startsWith("ar") || it.locale.language.startsWith("en") }
            ?.toList() ?: emptyList()
    }

    private fun generateSineBuffer(freqHz: Double, durationMs: Int, volume: Float): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate / freqHz)
            val fadeFactor = when {
                i < 150 -> i / 150.0
                i > numSamples - 300 -> (numSamples - i) / 300.0
                else -> 1.0
            }
            buffer[i] = (sin(angle) * 32767 * volume * fadeFactor).toInt().toShort()
        }
        return buffer
    }

    private fun generateSuccessChordBuffer(): ShortArray {
        val c5 = generateSineBuffer(523.25, 90, 0.6f)
        val e5 = generateSineBuffer(659.25, 90, 0.65f)
        val g5 = generateSineBuffer(783.99, 180, 0.7f)
        val gap = ShortArray((sampleRate * 0.05).toInt())

        val total = c5.size + gap.size + e5.size + gap.size + g5.size
        val result = ShortArray(total)
        var offset = 0

        System.arraycopy(c5, 0, result, offset, c5.size); offset += c5.size
        System.arraycopy(gap, 0, result, offset, gap.size); offset += gap.size
        System.arraycopy(e5, 0, result, offset, e5.size); offset += e5.size
        System.arraycopy(gap, 0, result, offset, gap.size); offset += gap.size
        System.arraycopy(g5, 0, result, offset, g5.size)

        return result
    }

    private fun generateErrorBuffer(): ShortArray {
        val high = generateSineBuffer(280.0, 110, 0.75f)
        val gap = ShortArray((sampleRate * 0.04).toInt())
        val low = generateSineBuffer(130.0, 190, 0.8f)

        val total = high.size + gap.size + low.size
        val result = ShortArray(total)
        var offset = 0

        System.arraycopy(high, 0, result, offset, high.size); offset += high.size
        System.arraycopy(gap, 0, result, offset, gap.size); offset += gap.size
        System.arraycopy(low, 0, result, offset, low.size)

        return result
    }

    private fun playBufferAsync(buffer: ShortArray) {
        audioScope.launch {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
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
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                val durationMs = (buffer.size * 1000L / sampleRate) + 20
                delay(durationMs)
            } catch (ignored: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun playSuccessChord() {
        playBufferAsync(successBuffer)
    }

    fun playErrorTone() {
        playBufferAsync(errorBuffer)
    }

    fun playRadarTick() {
        playBufferAsync(radarBuffer)
    }

    fun vibrateSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 60, 160), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 90, 60, 160), -1)
        }
    }

    fun vibrateError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 80, 220, 80, 220), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 220, 80, 220, 80, 220), -1)
        }
    }

    fun vibrateFound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(70)
        }
    }

    fun release() {
        audioScope.cancel()
        tts?.stop()
        tts?.shutdown()
    }
}
