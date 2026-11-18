package com.abdulaziz.netpass

import android.speech.tts.Voice
import com.abdulaziz.netpass.data.local.PreferencesRepository
import com.abdulaziz.netpass.domain.VoucherValidator
import com.abdulaziz.netpass.feedback.FeedbackController
import com.abdulaziz.netpass.feedback.TtsEngineItem
import com.abdulaziz.netpass.model.AppSettings
import com.abdulaziz.netpass.model.AppTab
import com.abdulaziz.netpass.model.VoucherRecord
import com.abdulaziz.netpass.network.HotspotAuthClient
import com.abdulaziz.netpass.network.LoginResult
import com.abdulaziz.netpass.ui.AppDestination
import com.abdulaziz.netpass.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_setsInitializedAndRoutesToWelcomeWhenFirstLaunch() = runTest(testDispatcher) {
        val repo = FakePreferencesRepository(initialSettings = AppSettings(isFirstLaunch = true))
        val auth = FakeHotspotAuthClient(activeGateway = "192.168.88.1")
        val feedback = FakeFeedbackController()

        val vm = MainViewModel(repo, auth, { feedback }, testDispatcher)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isInitialized)
        assertEquals(AppDestination.Welcome, vm.uiState.value.destination)
        assertEquals("192.168.88.1", vm.uiState.value.activeWifiGateway)
    }

    @Test
    fun toggleDarkTheme_updatesPersistedSettingsAndUiState() = runTest(testDispatcher) {
        val repo = FakePreferencesRepository(initialSettings = AppSettings(isFirstLaunch = false, isDarkTheme = true))
        val vm = MainViewModel(repo, FakeHotspotAuthClient(), { FakeFeedbackController() }, testDispatcher)
        advanceUntilIdle()

        vm.toggleDarkTheme()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.settings.isDarkTheme)
    }

    @Test
    fun resetDefaults_preservesIsFirstLaunchFalse() = runTest(testDispatcher) {
        val repo = FakePreferencesRepository(
            initialSettings = AppSettings(isFirstLaunch = false, gatewayIp = "login.net", isDarkTheme = false)
        )
        val vm = MainViewModel(repo, FakeHotspotAuthClient(), { FakeFeedbackController() }, testDispatcher)
        advanceUntilIdle()

        vm.resetDefaults()
        advanceUntilIdle()

        assertEquals("10.0.0.1", vm.uiState.value.settings.gatewayIp)
        assertTrue(vm.uiState.value.settings.isDarkTheme)
        assertFalse(vm.uiState.value.settings.isFirstLaunch)
    }

    @Test
    fun onPinDetected_withAutoLoginExecutesOnceAndSavesHistoryOnSuccess() = runTest(testDispatcher) {
        val repo = FakePreferencesRepository(initialSettings = AppSettings(isFirstLaunch = false, autoLoginOnScan = true))
        val auth = FakeHotspotAuthClient(nextLoginResult = LoginResult(true, "تم الدخول بنجاح"))
        val feedback = FakeFeedbackController()
        val vm = MainViewModel(repo, auth, { feedback }, testDispatcher)
        advanceUntilIdle()

        vm.startScanning()
        vm.onPinDetected("98451236")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isScanning)
        assertFalse(vm.uiState.value.torchEnabled)
        assertEquals("98451236", vm.uiState.value.pinText)
        assertEquals(1, auth.loginCalls.size)
        assertEquals(1, vm.uiState.value.history.size)
        assertEquals("98451236", vm.uiState.value.history.first().pin)
        assertEquals(1, feedback.vibrateFoundCount)
        assertEquals(1, feedback.successChordCount)
    }

    @Test
    fun executeLogin_rejectsTooShortPinWithoutCallingAuthClient() = runTest(testDispatcher) {
        val repo = FakePreferencesRepository(initialSettings = AppSettings(isFirstLaunch = false, minLen = 6))
        val auth = FakeHotspotAuthClient()
        val feedback = FakeFeedbackController()
        val vm = MainViewModel(repo, auth, { feedback }, testDispatcher)
        advanceUntilIdle()

        vm.executeLogin("1234")
        advanceUntilIdle()

        assertTrue(auth.loginCalls.isEmpty())
        assertEquals(1, feedback.errorToneCount)
        assertEquals(1, feedback.vibrateErrorCount)
    }

    @Test
    fun handleBackPress_navigatesAboutThenScanningThenNonScanTab() = runTest(testDispatcher) {
        val repo = FakePreferencesRepository(initialSettings = AppSettings(isFirstLaunch = false))
        val vm = MainViewModel(repo, FakeHotspotAuthClient(), { FakeFeedbackController() }, testDispatcher)
        advanceUntilIdle()

        vm.selectTab(AppTab.SETTINGS)
        vm.openAbout()
        assertEquals(AppDestination.About, vm.uiState.value.destination)

        assertTrue(vm.handleBackPress())
        assertEquals(AppDestination.Main, vm.uiState.value.destination)
        assertEquals(AppTab.SETTINGS, vm.uiState.value.currentTab)

        assertTrue(vm.handleBackPress())
        assertEquals(AppTab.SCAN, vm.uiState.value.currentTab)

        vm.startScanning()
        assertTrue(vm.handleBackPress())
        assertFalse(vm.uiState.value.isScanning)

        assertFalse(vm.handleBackPress())
    }

    private class FakePreferencesRepository(
        initialSettings: AppSettings = AppSettings(),
        initialHistory: List<VoucherRecord> = emptyList()
    ) : PreferencesRepository {

        private val _settings = MutableStateFlow(initialSettings)
        private val _history = MutableStateFlow(initialHistory)

        override val settingsFlow: Flow<AppSettings> = _settings
        override val historyFlow: Flow<List<VoucherRecord>> = _history

        override suspend fun saveSettings(settings: AppSettings) {
            val (min, max) = VoucherValidator.normalizeBounds(settings.minLen, settings.maxLen)
            _settings.value = settings.copy(minLen = min, maxLen = max)
        }

        override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
            saveSettings(transform(_settings.value))
        }

        override suspend fun resetDefaults() {
            saveSettings(AppSettings(isFirstLaunch = false))
        }

        override suspend fun markOnboardingCompleted() {
            _settings.update { it.copy(isFirstLaunch = false) }
        }

        override suspend fun addVoucherToHistory(pin: String, timestamp: Long) {
            _history.update { current ->
                listOf(VoucherRecord(pin, timestamp)) + current.filter { it.pin != pin }
            }
        }

        override suspend fun clearHistory() {
            _history.value = emptyList()
        }
    }

    private class FakeHotspotAuthClient(
        private val activeGateway: String = "10.0.0.1",
        private val nextLoginResult: LoginResult = LoginResult(true, "OK")
    ) : HotspotAuthClient {

        val loginCalls = mutableListOf<Pair<String, String>>()

        override fun isWifiConnected(): Boolean = true
        override fun isVpnActive(): Boolean = false
        override fun getActiveGatewayIp(): String = activeGateway

        override suspend fun login(pin: String, customGateway: String): LoginResult {
            loginCalls.add(pin to customGateway)
            return nextLoginResult
        }
    }

    private class FakeFeedbackController : FeedbackController {
        var successChordCount = 0
        var errorToneCount = 0
        var radarTickCount = 0
        var vibrateSuccessCount = 0
        var vibrateErrorCount = 0
        var vibrateFoundCount = 0
        var released = false
        val spokenMessages = mutableListOf<String>()

        override fun setEngine(pkg: String) {}
        override fun getInstalledEngines(): List<TtsEngineItem> = emptyList()
        override fun getAvailableVoices(): List<Voice> = emptyList()
        override fun speak(text: String, enabled: Boolean, voiceName: String) {
            if (enabled) spokenMessages.add(text)
        }
        override fun showToast(message: String) {}
        override fun playSuccessChord() { successChordCount++ }
        override fun playErrorTone() { errorToneCount++ }
        override fun playRadarTick() { radarTickCount++ }
        override fun vibrateSuccess() { vibrateSuccessCount++ }
        override fun vibrateError() { vibrateErrorCount++ }
        override fun vibrateFound() { vibrateFoundCount++ }
        override fun release() { released = true }
    }
}
