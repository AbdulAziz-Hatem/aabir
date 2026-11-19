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
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

class FeedbackManager(
    context: Context,
    private var enginePackage: String = ""
) : TextToSpeech.OnInitListener, FeedbackController {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    @Volatile
    private var isTtsReady = false
    @Volatile
    private var isReleased = false

    // Serialize all AudioTrack operations onto a single worker to prevent stop/reload/play races.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val audioDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val audioScope = CoroutineScope(audioDispatcher + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val isScreenReaderActive: Boolean
        get() = accessibilityManager?.isEnabled == true && accessibilityManager.isTouchExplorationEnabled

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val sampleRate = 44100
    private val radarBuffer: ShortArray by lazy { generateSineBuffer(880.0, 25, 0.25f) }
    private val errorBuffer: ShortArray by lazy { generateErrorBuffer() }
    private val successBuffer: ShortArray by lazy { generateSuccessChordBuffer() }

    private var radarTrack: AudioTrack? = null
    private var successTrack: AudioTrack? = null
    private var errorTrack: AudioTrack? = null

    init {
        initTts(enginePackage)
        initAudioTracks()
    }

    private fun createStaticTrack(buffer: ShortArray): AudioTrack? {
        return try {
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
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating static AudioTrack", e)
            null
        }
    }

    private fun initAudioTracks() {
        audioScope.launch {
            if (isReleased) return@launch
            radarTrack = createStaticTrack(radarBuffer)
            successTrack = createStaticTrack(successBuffer)
            errorTrack = createStaticTrack(errorBuffer)
        }
    }

    private fun getOrRecreateTrack(buffer: ShortArray, currentTrack: AudioTrack?): AudioTrack? {
        if (currentTrack != null && currentTrack.state == AudioTrack.STATE_INITIALIZED) {
            return currentTrack
        }
        try {
            currentTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed releasing uninitialized AudioTrack", e)
        }
        return createStaticTrack(buffer)
    }

    private fun playPreloadedTrack(
        buffer: ShortArray,
        trackProvider: () -> AudioTrack?,
        trackUpdater: (AudioTrack?) -> Unit
    ) {
        if (isReleased) return
        audioScope.launch {
            if (isReleased) return@launch
            try {
                val track = getOrRecreateTrack(buffer, trackProvider())
                trackUpdater(track)
                if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.stop()
                        }
                        track.reloadStaticData()
                        track.play()
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "AudioTrack reload failed, recreating static track", e)
                        val freshTrack = createStaticTrack(buffer)
                        trackUpdater(freshTrack)
                        freshTrack?.play()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack playback failed", e)
            }
        }
    }

    private fun initTts(pkg: String) {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting previous TTS instance", e)
        }
        isTtsReady = false
        enginePackage = pkg
        tts = if (pkg.isNotEmpty()) {
            TextToSpeech(appContext, this, pkg)
        } else {
            TextToSpeech(appContext, this)
        }
    }

    override fun setEngine(pkg: String) {
        if (!isReleased && pkg != enginePackage) {
            initTts(pkg)
        }
    }

    override fun getInstalledEngines(): List<TtsEngineItem> {
        return try {
            tts?.engines?.map { TtsEngineItem(it.name, it.label) } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying installed TTS engines", e)
            emptyList()
        }
    }

    override fun onInit(status: Int) {
        if (isReleased) return
        if (status == TextToSpeech.SUCCESS) {
            val defaultLocale = Locale.getDefault()
            val targetLocale = when (defaultLocale.language) {
                "ar", "en" -> defaultLocale
                else -> Locale.forLanguageTag("ar")
            }
            tts?.language = targetLocale
            isTtsReady = true
        }
    }

    override fun speak(text: String, enabled: Boolean, voiceName: String) {
        if (!enabled || isReleased) return

        if (isScreenReaderActive) {
            try {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                    className = javaClass.name
                    packageName = appContext.packageName
                    this.text.add(text)
                }
                accessibilityManager?.sendAccessibilityEvent(event)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed dispatching accessibility announcement", e)
            }
        }

        if (!isTtsReady) return
        tts?.let { engine ->
            if (voiceName.isNotEmpty()) {
                val found = engine.voices?.find { it.name == voiceName }
                if (found != null) engine.voice = found
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NetPassUtteranceId")
        }
    }

    override fun showToast(message: String) {
        if (isReleased) return
        mainHandler.post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun getAvailableVoices(): List<Voice> {
        return try {
            tts?.voices
                ?.filter { it.locale.language.startsWith("ar") || it.locale.language.startsWith("en") }
                ?.toList()
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying TTS voices", e)
            emptyList()
        }
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

    override fun playSuccessChord() {
        playPreloadedTrack(successBuffer, { successTrack }, { successTrack = it })
    }

    override fun playErrorTone() {
        playPreloadedTrack(errorBuffer, { errorTrack }, { errorTrack = it })
    }

    override fun playRadarTick() {
        playPreloadedTrack(radarBuffer, { radarTrack }, { radarTrack = it })
    }

    override fun vibrateSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 60, 160), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 90, 60, 160), -1)
        }
    }

    override fun vibrateError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 80, 220, 80, 220), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 220, 80, 220, 80, 220), -1)
        }
    }

    override fun vibrateFound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(70)
        }
    }

    private fun releaseTrackSafely(track: AudioTrack?) {
        try {
            track?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed releasing AudioTrack", e)
        }
    }

    override fun release() {
        if (isReleased) return
        isReleased = true
        isTtsReady = false
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        audioScope.launch {
            releaseTrackSafely(radarTrack)
            radarTrack = null
            releaseTrackSafely(successTrack)
            successTrack = null
            releaseTrackSafely(errorTrack)
            errorTrack = null
            audioScope.cancel()
        }
    }

    companion object {
        private const val TAG = "FeedbackManager"
    }
}
