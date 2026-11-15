package com.abdulaziz.aabir.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object MikrotikClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(2000, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private val strictNoRedirectClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // محول الأرقام الكوني: تحويل الأرقام العربية المشرقية (١٢٣) إلى أرقام ASCII (123)
    fun normalizeToAsciiDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                in '0'..'9' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // فحص ما إذا كان هناك VPN نشط يحجب بوابة ميكروتك المحلية
    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun getActiveGatewayIp(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val ip = dhcp.gateway
                return String.format("%d.%d.%d.%d", (ip and 0xff), (ip shr 8 and 0xff), (ip shr 16 and 0xff), (ip shr 24 and 0xff))
            }
        } catch (ignored: Exception) {}
        return ""
    }

    private fun bindWifiNetwork(context: Context, cm: ConnectivityManager?) {
        try {
            cm?.allNetworks?.forEach { network ->
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    cm.bindProcessToNetwork(network)
                    return
                }
            }
        } catch (ignored: Exception) {}
    }

    suspend fun login(
        context: Context,
        pin: String,
        customGateway: String = ""
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanPin = normalizeToAsciiDigits(pin)
        if (cleanPin.length < 4) {
            return@withContext Pair(false, "رمز الكرت قصير جدا، يرجى إدخال 4 أرقام على الأقل")
        }

        if (!isWifiConnected(context)) {
            return@withContext Pair(false, "أنت غير متصل بالواي فاي! يرجى الاتصال بشبكة ميكروتك أولا")
        }

        // تحذير ذكي في حال وجود VPN نشط
        if (isVpnActive(context)) {
            return@withContext Pair(false, "يرجى تعطيل الـ VPN لكي تتمكن من الوصول لصفحة الراوتر")
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        bindWifiNetwork(context, cm)

        try {
            val detectedGateway = getActiveGatewayIp(context)
            val targetHost = when {
                customGateway.isNotBlank() -> customGateway.trim()
                detectedGateway.isNotBlank() -> detectedGateway
                else -> "10.0.0.1"
            }

            val payloads = listOf(
                Pair(cleanPin, ""),         // النمط اليمني: اسم مستخدم فقط
                Pair(cleanPin, cleanPin)    // نمط التطابق
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

                    val postResponse = client.newCall(postRequest).execute()
                    val postResponseBody = postResponse.body?.string() ?: ""

                    val foundError = extractMikrotikError(postResponseBody)
                    if (foundError != null) {
                        lastDetectedError = foundError
                        continue
                    }

                    kotlinx.coroutines.delay(400)
                    val verifyResult = checkActualLoginStatus(targetHost)
                    if (verifyResult.first) {
                        return@withContext Pair(true, "تم تسجيل الدخول وتفعيل الإنترنت بنجاح! كود الكرت: $cleanPin")
                    } else if (verifyResult.second.isNotEmpty()) {
                        lastDetectedError = verifyResult.second
                    }
                } catch (e: Exception) {
                    lastDetectedError = "تعذر الوصول لبوابة الراوتر (${e.message ?: "خطأ اتصال"})"
                    continue
                }
            }

            val finalError = if (lastDetectedError.isNotEmpty()) {
                lastDetectedError
            } else {
                "فشل تسجيل الدخول؛ الكرت غير صحيح أو منتهي الصلاحية"
            }

            return@withContext Pair(false, finalError)
        } finally {
            try {
                cm?.bindProcessToNetwork(null)
            } catch (ignored: Exception) {}
        }
    }

    private fun checkActualLoginStatus(host: String): Pair<Boolean, String> {
        return try {
            val statusReq = Request.Builder()
                .url("http://$host/status")
                .get()
                .build()

            strictNoRedirectClient.newCall(statusReq).execute().use { res ->
                if (res.code in 301..303) {
                    val location = res.header("Location") ?: ""
                    if (location.contains("login", ignoreCase = true)) {
                        return Pair(false, "الراوتر رفض الكرت وأعاد التوجيه لصفحة الدخول")
                    }
                }

                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val hasLogout = body.contains("logout", ignoreCase = true) || body.contains("action=\"$(link-logout)\"")
                    val isLoginPage = body.contains("name=\"username\"", ignoreCase = true) || body.contains("type=\"password\"", ignoreCase = true)

                    if (hasLogout && !isLoginPage) {
                        return Pair(true, "")
                    }
                }

                Pair(false, "")
            }
        } catch (e: Exception) {
            if (verifyGoogle204Probe()) {
                Pair(true, "")
            } else {
                Pair(false, "لم يتم فتح تدفق الإنترنت")
            }
        }
    }

    private fun verifyGoogle204Probe(): Boolean {
        return try {
            val req = Request.Builder()
                .url("http://connectivitycheck.gstatic.com/generate_204")
                .get()
                .build()
            strictNoRedirectClient.newCall(req).execute().use { it.code == 204 }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractMikrotikError(html: String): String? {
        val lower = html.toLowerCase()
        return when {
            lower.contains("invalid username or password") -> "اسم المستخدم أو كلمة المرور غير صحيحة"
            lower.contains("user") && lower.contains("not found") -> "رمز الكرت غير موجود بالنظام"
            lower.contains("already logged in") -> "الكرت قيد الاستخدام في جهاز آخر حاليا"
            lower.contains("uptime limit") -> "انتهى وقت الكرت المتاح"
            lower.contains("traffic limit") -> "انتهى رصيد بيانات الكرت بالكامل"
            lower.contains("no more sessions") -> "تم استنفاذ عدد الجلسات المسموح بها للكرت"
            lower.contains("credit expired") -> "صلاحية الكرت منتهية"
            else -> null
        }
    }
}
