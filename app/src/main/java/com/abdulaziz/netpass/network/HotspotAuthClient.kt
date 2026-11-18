package com.abdulaziz.netpass.network

data class LoginResult(
    val success: Boolean,
    val message: String
)

interface HotspotAuthClient {
    fun isWifiConnected(): Boolean
    fun isVpnActive(): Boolean
    fun getActiveGatewayIp(): String
    suspend fun login(pin: String, customGateway: String = ""): LoginResult
}
