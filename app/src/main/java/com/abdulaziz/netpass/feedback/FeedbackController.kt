package com.abdulaziz.netpass.feedback

import android.speech.tts.Voice

data class TtsEngineItem(val packageName: String, val label: String)

interface FeedbackController {
    fun setEngine(pkg: String)
    fun getInstalledEngines(): List<TtsEngineItem>
    fun getAvailableVoices(): List<Voice>
    fun speak(text: String, enabled: Boolean, voiceName: String = "")
    fun showToast(message: String)
    fun playSuccessChord()
    fun playErrorTone()
    fun playRadarTick()
    fun vibrateSuccess()
    fun vibrateError()
    fun vibrateFound()
    fun release()
}
