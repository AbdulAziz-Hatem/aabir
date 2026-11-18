package com.abdulaziz.netpass.model

data class VoucherRecord(
    val pin: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppSettings(
    val gatewayIp: String = "10.0.0.1",
    val autoLoginOnScan: Boolean = true,
    val voiceAnnounce: Boolean = true,
    val selectedVoiceName: String = "",
    val selectedTtsEngine: String = "",
    val radarSound: Boolean = true,
    val minLen: Int = 4,
    val maxLen: Int = 16,
    val isDarkTheme: Boolean = true,
    val isFirstLaunch: Boolean = true
)

enum class AppTab {
    SCAN,
    HISTORY,
    SETTINGS
}
