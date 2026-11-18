package com.abdulaziz.netpass.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abdulaziz.netpass.data.local.PreferencesRepository
import com.abdulaziz.netpass.di.AppContainer
import com.abdulaziz.netpass.domain.VoucherValidator
import com.abdulaziz.netpass.feedback.FeedbackController
import com.abdulaziz.netpass.model.AppSettings
import com.abdulaziz.netpass.model.AppTab
import com.abdulaziz.netpass.network.HotspotAuthClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val authClient: HotspotAuthClient,
    feedbackFactory: (String) -> FeedbackController,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val feedback: FeedbackController = feedbackFactory("")

    private var hasAppliedInitialRoute = false
    private var lastAppliedEngine: String? = null

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.settingsFlow,
                preferencesRepository.historyFlow
            ) { settings, history ->
                settings to history
            }.collect { (settings, history) ->
                if (lastAppliedEngine != settings.selectedTtsEngine) {
                    lastAppliedEngine = settings.selectedTtsEngine
                    feedback.setEngine(settings.selectedTtsEngine)
                }

                _uiState.update { current ->
                    val targetDestination = if (!hasAppliedInitialRoute) {
                        hasAppliedInitialRoute = true
                        if (settings.isFirstLaunch) AppDestination.Welcome else AppDestination.Main
                    } else {
                        current.destination
                    }
                    current.copy(
                        isInitialized = true,
                        settings = settings,
                        history = history,
                        destination = targetDestination
                    )
                }
            }
        }

        refreshActiveGateway()
    }

    fun refreshActiveGateway() {
        viewModelScope.launch(ioDispatcher) {
            val gateway = authClient.getActiveGatewayIp()
            _uiState.update { it.copy(activeWifiGateway = gateway) }
        }
    }

    fun completeOnboarding() {
        _uiState.update { it.copy(destination = AppDestination.Main) }
        viewModelScope.launch {
            preferencesRepository.markOnboardingCompleted()
        }
    }

    fun openOnboarding() {
        _uiState.update { it.copy(destination = AppDestination.Welcome, isScanning = false, torchEnabled = false) }
    }

    fun openAbout() {
        _uiState.update { it.copy(destination = AppDestination.About, isScanning = false, torchEnabled = false) }
    }

    fun closeAbout() {
        _uiState.update { it.copy(destination = AppDestination.Main) }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == AppTab.SETTINGS) {
            refreshActiveGateway()
        }
    }

    fun toggleDarkTheme() {
        updateSettings { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            preferencesRepository.updateSettings(transform)
        }
    }

    fun selectTtsEngine(packageName: String, label: String) {
        lastAppliedEngine = packageName
        feedback.setEngine(packageName)
        updateSettings { it.copy(selectedTtsEngine = packageName) }
        val announcement = if (packageName.isEmpty()) {
            "تم التبديل إلى محرك النظام الافتراضي"
        } else {
            "تم التبديل إلى محرك $label"
        }
        feedback.speak(announcement, true)
    }

    fun resetDefaults() {
        viewModelScope.launch {
            preferencesRepository.resetDefaults()
        }
    }

    fun onPinChange(rawInput: String) {
        val digits = VoucherValidator.normalizeToAsciiDigits(rawInput)
        _uiState.update { it.copy(pinText = digits) }
    }

    fun startScanning() {
        _uiState.update { it.copy(isScanning = true, torchEnabled = true) }
        announceFeedback("الكاميرا تعمل وجّه الهاتف نحو الكرت")
    }

    fun stopScanning() {
        if (_uiState.value.isScanning || _uiState.value.torchEnabled) {
            _uiState.update { it.copy(isScanning = false, torchEnabled = false) }
        }
    }

    fun onCameraPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            startScanning()
        } else {
            announceFeedback("تم رفض إذن الكاميرا يمكنك استخدام الإدخال اليدوي")
            _uiState.update { it.copy(showPermissionRationaleDialog = true) }
        }
    }

    fun dismissPermissionRationale() {
        _uiState.update { it.copy(showPermissionRationaleDialog = false) }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun toggleZoom() {
        val newZoom = if (_uiState.value.currentZoom >= 2.0f) 1.0f else 2.2f
        _uiState.update { it.copy(currentZoom = newZoom) }
        val zoomMsg = if (newZoom >= 2.0f) "تكبير للكروت الصغيرة" else "تكبير عادي"
        announceFeedback(zoomMsg)
    }

    fun requestInstantCapture() {
        _uiState.update { it.copy(instantCaptureCounter = it.instantCaptureCounter + 1) }
        feedback.vibrateFound()
        announceFeedback("جار الالتقاط الفوري")
    }

    fun onGuidanceNeeded(message: String) {
        announceFeedback(message)
    }

    fun onRadarPulse() {
        feedback.playRadarTick()
    }

    fun onPinDetected(detectedPin: String) {
        val autoLogin = _uiState.value.settings.autoLoginOnScan
        _uiState.update {
            it.copy(
                pinText = detectedPin,
                isScanning = false,
                torchEnabled = false
            )
        }
        feedback.vibrateFound()

        if (autoLogin) {
            executeLogin(detectedPin, fromAutoScan = true)
        } else {
            feedback.playSuccessChord()
            _uiState.update { it.copy(statusText = "تم التقاط الكود $detectedPin") }
            announceFeedback("تم التقاط الكود $detectedPin اضغط زر الدخول")
        }
    }

    fun executeLogin(pinToLogin: String, fromAutoScan: Boolean = false) {
        val state = _uiState.value
        if (state.isLoggingIn) return

        val clean = VoucherValidator.normalizeToAsciiDigits(pinToLogin)
        if (clean.length < state.settings.minLen) {
            val alertMsg = if (clean.isEmpty()) {
                "يرجى كتابة أو مسح رمز الكرت أولا"
            } else {
                "رمز الكرت غير مكتمل مطلوب ${state.settings.minLen} أرقام على الأقل"
            }
            _uiState.update { it.copy(statusText = alertMsg) }
            feedback.playErrorTone()
            feedback.vibrateError()
            announceFeedback(alertMsg)
            return
        }

        val startAnnouncement = if (fromAutoScan) {
            "تم التقاط الكود، جارٍ تسجيل الدخول في الشبكة"
        } else {
            "جار تسجيل الدخول في الشبكة"
        }
        _uiState.update {
            it.copy(
                isLoggingIn = true,
                statusText = "جار تسجيل الدخول بالكود $clean"
            )
        }
        announceFeedback(startAnnouncement)

        val gatewayIp = state.settings.gatewayIp
        viewModelScope.launch {
            val result = authClient.login(clean, gatewayIp)
            _uiState.update {
                it.copy(
                    isLoggingIn = false,
                    statusText = result.message
                )
            }

            if (result.success) {
                feedback.playSuccessChord()
                feedback.vibrateSuccess()
                announceFeedback(result.message)
                preferencesRepository.addVoucherToHistory(clean)
            } else {
                feedback.playErrorTone()
                feedback.vibrateError()
                announceFeedback(result.message)
            }
        }
    }

    fun reLoginFromHistory(pin: String) {
        _uiState.update {
            it.copy(
                pinText = pin,
                currentTab = AppTab.SCAN
            )
        }
        executeLogin(pin)
    }

    fun clearHistory() {
        viewModelScope.launch {
            preferencesRepository.clearHistory()
        }
    }

    fun onCopySuccess() {
        _uiState.update { it.copy(statusText = "تم نسخ الرمز إلى الحافظة") }
        announceFeedback("تم النسخ")
    }

    fun handleBackPress(): Boolean {
        val state = _uiState.value
        return when {
            state.destination == AppDestination.About -> {
                closeAbout()
                true
            }

            state.isScanning -> {
                stopScanning()
                true
            }

            state.destination == AppDestination.Main && state.currentTab != AppTab.SCAN -> {
                selectTab(AppTab.SCAN)
                true
            }

            else -> false
        }
    }

    fun announceFeedback(message: String) {
        val settings = _uiState.value.settings
        if (settings.voiceAnnounce) {
            feedback.speak(message, true, settings.selectedVoiceName)
        } else {
            feedback.showToast(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        feedback.release()
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        "Unknown ViewModel class: ${modelClass.name}"
                    }
                    return MainViewModel(
                        preferencesRepository = container.preferencesRepository,
                        authClient = container.authClient,
                        feedbackFactory = container::createFeedbackController
                    ) as T
                }
            }
        }
    }
}
