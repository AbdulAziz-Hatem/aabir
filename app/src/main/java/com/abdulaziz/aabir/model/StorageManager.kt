package com.abdulaziz.aabir.model

import android.content.Context
import android.content.SharedPreferences

class StorageManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("aabir_secure_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            gatewayIp = prefs.getString("gatewayIp", "10.0.0.1") ?: "10.0.0.1",
            autoLoginOnScan = prefs.getBoolean("autoLoginOnScan", true),
            voiceAnnounce = prefs.getBoolean("voiceAnnounce", true),
            selectedVoiceName = prefs.getString("selectedVoiceName", "") ?: "",
            selectedTtsEngine = prefs.getString("selectedTtsEngine", "") ?: "",
            radarSound = prefs.getBoolean("radarSound", true),
            minLen = prefs.getInt("minLen", 4),
            maxLen = prefs.getInt("maxLen", 16),
            isDarkTheme = prefs.getBoolean("isDarkTheme", true)
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString("gatewayIp", settings.gatewayIp)
            .putBoolean("autoLoginOnScan", settings.autoLoginOnScan)
            .putBoolean("voiceAnnounce", settings.voiceAnnounce)
            .putString("selectedVoiceName", settings.selectedVoiceName)
            .putString("selectedTtsEngine", settings.selectedTtsEngine)
            .putBoolean("radarSound", settings.radarSound)
            .putInt("minLen", settings.minLen)
            .putInt("maxLen", settings.maxLen)
            .putBoolean("isDarkTheme", settings.isDarkTheme)
            .apply()
    }

    fun loadHistory(): List<VoucherRecord> {
        val raw = prefs.getString("voucher_history", "") ?: ""
        if (raw.isEmpty()) return emptyList()

        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val pin = parts[0]
                val ts = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                VoucherRecord(pin, ts)
            } else null
        }
    }

    fun saveHistory(list: List<VoucherRecord>) {
        val raw = list.take(50).joinToString(";") { "${it.pin}:${it.timestamp}" }
        prefs.edit().putString("voucher_history", raw).apply()
    }
}
