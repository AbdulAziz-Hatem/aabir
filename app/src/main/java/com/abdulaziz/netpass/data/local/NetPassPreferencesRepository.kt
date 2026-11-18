package com.abdulaziz.netpass.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abdulaziz.netpass.domain.VoucherValidator
import com.abdulaziz.netpass.model.AppSettings
import com.abdulaziz.netpass.model.VoucherRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val LEGACY_PREFS_NAME = "netpass_prefs"
private const val DATASTORE_NAME = "netpass_datastore"

private val Context.netPassDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, LEGACY_PREFS_NAME))
    }
)

interface PreferencesRepository {
    val settingsFlow: Flow<AppSettings>
    val historyFlow: Flow<List<VoucherRecord>>

    suspend fun saveSettings(settings: AppSettings)
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
    suspend fun resetDefaults()
    suspend fun markOnboardingCompleted()
    suspend fun addVoucherToHistory(pin: String, timestamp: Long = System.currentTimeMillis())
    suspend fun clearHistory()
}

class NetPassPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    private object Keys {
        val GATEWAY_IP = stringPreferencesKey("gatewayIp")
        val AUTO_LOGIN_ON_SCAN = booleanPreferencesKey("autoLoginOnScan")
        val VOICE_ANNOUNCE = booleanPreferencesKey("voiceAnnounce")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selectedVoiceName")
        val SELECTED_TTS_ENGINE = stringPreferencesKey("selectedTtsEngine")
        val RADAR_SOUND = booleanPreferencesKey("radarSound")
        val MIN_LEN = intPreferencesKey("minLen")
        val MAX_LEN = intPreferencesKey("maxLen")
        val IS_DARK_THEME = booleanPreferencesKey("isDarkTheme")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("isFirstLaunch")
        val VOUCHER_HISTORY = stringPreferencesKey("voucher_history")
    }

    private val safeData: Flow<Preferences> = dataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.w(TAG, "Failed reading preferences from DataStore; falling back to defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    override val settingsFlow: Flow<AppSettings> = safeData.map { prefs ->
        readSettingsFromPreferences(prefs)
    }

    override val historyFlow: Flow<List<VoucherRecord>> = safeData.map { prefs ->
        val raw = prefs[Keys.VOUCHER_HISTORY].orEmpty()
        VoucherHistoryCodec.decode(raw)
    }

    override suspend fun saveSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            writeSettingsToPreferences(prefs, settings)
        }
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = readSettingsFromPreferences(prefs)
            writeSettingsToPreferences(prefs, transform(current))
        }
    }

    override suspend fun resetDefaults() {
        dataStore.edit { prefs ->
            writeSettingsToPreferences(prefs, AppSettings(isFirstLaunch = false))
        }
    }

    override suspend fun markOnboardingCompleted() {
        dataStore.edit { prefs ->
            prefs[Keys.IS_FIRST_LAUNCH] = false
        }
    }

    override suspend fun addVoucherToHistory(pin: String, timestamp: Long) {
        dataStore.edit { prefs ->
            val current = VoucherHistoryCodec.decode(prefs[Keys.VOUCHER_HISTORY].orEmpty())
            val updated = listOf(VoucherRecord(pin = pin, timestamp = timestamp)) +
                current.filter { it.pin != pin }
            prefs[Keys.VOUCHER_HISTORY] = VoucherHistoryCodec.encode(updated)
        }
    }

    override suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs[Keys.VOUCHER_HISTORY] = "[]"
        }
    }

    private fun readSettingsFromPreferences(prefs: Preferences): AppSettings {
        val rawMin = prefs[Keys.MIN_LEN] ?: 4
        val rawMax = prefs[Keys.MAX_LEN] ?: 16
        val (safeMin, safeMax) = VoucherValidator.normalizeBounds(rawMin, rawMax)

        return AppSettings(
            gatewayIp = prefs[Keys.GATEWAY_IP] ?: "10.0.0.1",
            autoLoginOnScan = prefs[Keys.AUTO_LOGIN_ON_SCAN] ?: true,
            voiceAnnounce = prefs[Keys.VOICE_ANNOUNCE] ?: true,
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME] ?: "",
            selectedTtsEngine = prefs[Keys.SELECTED_TTS_ENGINE] ?: "",
            radarSound = prefs[Keys.RADAR_SOUND] ?: true,
            minLen = safeMin,
            maxLen = safeMax,
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
            isFirstLaunch = prefs[Keys.IS_FIRST_LAUNCH] ?: true
        )
    }

    private fun writeSettingsToPreferences(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        settings: AppSettings
    ) {
        val (safeMin, safeMax) = VoucherValidator.normalizeBounds(settings.minLen, settings.maxLen)
        prefs[Keys.GATEWAY_IP] = settings.gatewayIp
        prefs[Keys.AUTO_LOGIN_ON_SCAN] = settings.autoLoginOnScan
        prefs[Keys.VOICE_ANNOUNCE] = settings.voiceAnnounce
        prefs[Keys.SELECTED_VOICE_NAME] = settings.selectedVoiceName
        prefs[Keys.SELECTED_TTS_ENGINE] = settings.selectedTtsEngine
        prefs[Keys.RADAR_SOUND] = settings.radarSound
        prefs[Keys.MIN_LEN] = safeMin
        prefs[Keys.MAX_LEN] = safeMax
        prefs[Keys.IS_DARK_THEME] = settings.isDarkTheme
        prefs[Keys.IS_FIRST_LAUNCH] = settings.isFirstLaunch
    }

    companion object {
        private const val TAG = "NetPassPrefsRepo"

        fun create(context: Context): NetPassPreferencesRepository {
            return NetPassPreferencesRepository(context.applicationContext.netPassDataStore)
        }
    }
}
