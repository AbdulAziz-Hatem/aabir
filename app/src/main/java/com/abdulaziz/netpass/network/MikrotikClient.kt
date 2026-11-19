package com.abdulaziz.netpass.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.abdulaziz.netpass.domain.GatewayHostValidator
import com.abdulaziz.netpass.domain.VoucherValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

class MikrotikGatewayClient(
    context: Context,
    private val baseClient: OkHttpClient = defaultClient,
    private val baseNoRedirectClient: OkHttpClient = defaultNoRedirectClient
) : HotspotAuthClient {

    private val appContext = context.applicationContext

    override fun isWifiConnected(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        return findWifiNetwork(cm) != null
    }

    override fun isVpnActive(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    override fun getActiveGatewayIp(): String {
        try {
            val cm = appContext.getSystemService(ConnectivityManager::class.java)
            val wifiNetwork = findWifiNetwork(cm) ?: cm?.activeNetwork
            if (wifiNetwork != null) {
                val linkProps = cm?.getLinkProperties(wifiNetwork)
                val defaultRoute = linkProps?.routes?.firstOrNull { it.isDefaultRoute && it.hasGateway() }
                    ?: linkProps?.routes?.firstOrNull { it.hasGateway() }
                val gatewayAddress = defaultRoute?.gateway?.hostAddress
                if (!gatewayAddress.isNullOrEmpty()) {
                    return gatewayAddress
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading active gateway via LinkProperties", e)
        }

        try {
            @Suppress("DEPRECATION")
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val ip = dhcp.gateway
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading gateway via legacy DhcpInfo", e)
        }

        return ""
    }

    override suspend fun login(pin: String, customGateway: String): LoginResult =
        withContext(Dispatchers.IO) {
            val cleanPin = VoucherValidator.normalizeToAsciiDigits(pin)
            if (cleanPin.length < VoucherValidator.MIN_ALLOWED_LENGTH) {
                return@withContext LoginResult(
                    success = false,
                    message = "رمز الكرت قصير جداً، يرجى إدخال ${VoucherValidator.MIN_ALLOWED_LENGTH} أرقام على الأقل"
                )
            }

            val cm = appContext.getSystemService(ConnectivityManager::class.java)
            val wifiNetwork = findWifiNetwork(cm)
                ?: return@withContext LoginResult(
                    success = false,
                    message = "أنت غير متصل بشبكة Wi-Fi! يرجى الاتصال بشبكة MikroTik أولاً"
                )

            if (isVpnActive()) {
                return@withContext LoginResult(
                    success = false,
                    message = "يرجى تعطيل الـ VPN لكي تتمكن من الوصول لصفحة الراوتر"
                )
            }

            val detectedGateway = getActiveGatewayIp()
            val targetHost = GatewayHostValidator.resolveTargetHost(
                customGateway = customGateway,
                detectedGateway = detectedGateway
            ) ?: return@withContext LoginResult(
                success = false,
                message = "عنوان بوابة الراوتر المخصص غير صالح، يرجى التحقق منه في الإعدادات"
            )

            // Scope socket + DNS resolution strictly to the Wi-Fi network without mutating process-wide routing.
            val wifiDns = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return wifiNetwork.getAllByName(hostname).toList()
                }
            }
            val scopedClient = baseClient.newBuilder()
                .socketFactory(wifiNetwork.socketFactory)
                .dns(wifiDns)
                .build()
            val scopedNoRedirectClient = baseNoRedirectClient.newBuilder()
                .socketFactory(wifiNetwork.socketFactory)
                .dns(wifiDns)
                .build()

            val payloads = listOf(
                Pair(cleanPin, ""),
                Pair(cleanPin, cleanPin)
            )

            var lastDetectedError = ""

            for ((user, pass) in payloads) {
                try {
                    val formBuilder = FormBody.Builder()
                        .add("username", user)
                    if (pass.isNotEmpty()) {
                        formBuilder.add("password", pass)
                    }
                    formBuilder.add("dst", "http://$targetHost/status")

                    val postRequest = Request.Builder()
                        .url("http://$targetHost/login")
                        .post(formBuilder.build())
                        .build()

                    val postResponseBody = scopedClient.newCall(postRequest).execute().use { res ->
                        res.body?.string().orEmpty()
                    }

                    val foundError = MikrotikErrorParser.extractError(postResponseBody)
                    if (foundError != null) {
                        return@withContext LoginResult(false, foundError)
                    }

                    delay(350)
                    val verifyResult = checkActualLoginStatus(scopedNoRedirectClient, targetHost)
                    if (verifyResult.success) {
                        return@withContext LoginResult(
                            success = true,
                            message = "تم تسجيل الدخول وتفعيل الإنترنت بنجاح! كود الكرت: $cleanPin"
                        )
                    } else if (verifyResult.message.isNotEmpty()) {
                        return@withContext LoginResult(
                            success = false,
                            message = "الكرت غير صحيح أو تم رفضه من الراوتر"
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Login attempt failed for host=$targetHost", e)
                    if (lastDetectedError.isEmpty()) {
                        lastDetectedError = "تعذر الاتصال ببوابة الراوتر (${e.message ?: "خطأ اتصال"})"
                    }
                }
            }

            val finalError = lastDetectedError.ifEmpty {
                "فشل تسجيل الدخول؛ الكرت غير صحيح أو منتهي الصلاحية"
            }

            LoginResult(false, finalError)
        }

    private fun findWifiNetwork(cm: ConnectivityManager?): Network? {
        val active = cm?.activeNetwork
        if (active != null && cm.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return active
        }
        @Suppress("DEPRECATION")
        return cm?.allNetworks?.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun checkActualLoginStatus(noRedirectClient: OkHttpClient, host: String): LoginResult {
        return try {
            val statusReq = Request.Builder()
                .url("http://$host/status")
                .get()
                .build()

            noRedirectClient.newCall(statusReq).execute().use { res ->
                if (MikrotikErrorParser.isLoginRedirect(res.code, res.header("Location"))) {
                    return LoginResult(false, "الراوتر رفض الكرت وأعاد التوجيه لصفحة الدخول")
                }

                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    if (MikrotikErrorParser.isStatusPageLoggedIn(body)) {
                        return LoginResult(true, "")
                    }
                }

                LoginResult(false, "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Status check failed for host=$host; falling back to 204 probe", e)
            if (verifyGoogle204Probe(noRedirectClient)) {
                LoginResult(true, "")
            } else {
                LoginResult(false, "لم يتم فتح تدفق الإنترنت")
            }
        }
    }

    private fun verifyGoogle204Probe(noRedirectClient: OkHttpClient): Boolean {
        return try {
            val req = Request.Builder()
                .url("http://connectivitycheck.gstatic.com/generate_204")
                .get()
                .build()
            noRedirectClient.newCall(req).execute().use { it.code == 204 }
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity 204 probe failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "MikrotikClient"

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(3500, TimeUnit.MILLISECONDS)
                .readTimeout(3500, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
        }

        val defaultNoRedirectClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(2500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }
}
