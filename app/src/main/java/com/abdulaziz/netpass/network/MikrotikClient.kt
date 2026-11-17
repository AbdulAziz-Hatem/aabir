package com.abdulaziz.netpass.network

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
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private val strictNoRedirectClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // تحويل الأرقام العربية المشرقية (١٢٣) إلى أرقام ASCII (123) وتجريد أي محارف غير رقمية
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
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    /**
     * استخراج عنوان بوابة الراوتر النشط عبر جدول توجيه المسار الافتراضي المعياري في أندرويد
     * مع الحفاظ على مسار احتياطي متدرج لضمان التوافق مع كافة الإصدارات.
     */
    fun getActiveGatewayIp(context: Context): String {
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val activeNetwork = cm?.activeNetwork
            if (activeNetwork != null) {
                val linkProps = cm.getLinkProperties(activeNetwork)
                val defaultRoute = linkProps?.routes?.firstOrNull { it.isDefaultRoute && it.hasGateway() }
                    ?: linkProps?.routes?.firstOrNull { it.hasGateway() }
                val gatewayAddress = defaultRoute?.gateway?.hostAddress
                if (!gatewayAddress.isNullOrEmpty()) {
                    return gatewayAddress
                }
            }
        } catch (ignored: Exception) {}

        // مسار احتياطي للأجهزة القديمة
        try {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
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
            val active = cm?.activeNetwork
            if (active != null) {
                val caps = cm.getNetworkCapabilities(active)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    cm.bindProcessToNetwork(active)
                    return
                }
            }
            @Suppress("DEPRECATION")
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
            return@withContext Pair(false, "رمز الكرت قصير جداً، يرجى إدخال 4 أرقام على الأقل")
        }

        if (!isWifiConnected(context)) {
            return@withContext Pair(false, "أنت غير متصل بشبكة Wi-Fi! يرجى الاتصال بشبكة MikroTik أولاً")
        }

        if (isVpnActive(context)) {
            return@withContext Pair(false, "يرجى تعطيل الـ VPN لكي تتمكن من الوصول لصفحة الراوتر")
        }

        val cm = context.getSystemService(ConnectivityManager::class.java)
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
                Pair(cleanPin, cleanPin)    // نمط اسم المستخدم المطابق لكلمة المرور
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

                    // فحص رد صفحة الدخول المباشر: إذا كان هناك خطأ صريح من MikroTik
                    val foundError = extractMikrotikError(postResponseBody)
                    if (foundError != null) {
                        return@withContext Pair(false, foundError)
                    }

                    // التحقق من صفحة الحالة
                    kotlinx.coroutines.delay(350)
                    val verifyResult = checkActualLoginStatus(targetHost)
                    if (verifyResult.first) {
                        return@withContext Pair(true, "تم تسجيل الدخول وتفعيل الإنترنت بنجاح! كود الكرت: $cleanPin")
                    } else if (verifyResult.second.isNotEmpty()) {
                        return@withContext Pair(false, "الكرت غير صحيح أو تم رفضه من الراوتر")
                    }
                } catch (e: Exception) {
                    if (lastDetectedError.isEmpty()) {
                        lastDetectedError = "تعذر الاتصال ببوابة الراوتر (${e.message ?: "خطأ اتصال"})"
                    }
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
                // إذا رد الراوتر بتحويل 302 نحو صفحة الدخول login: فالكرت مرفوض حتماً
                if (res.code in 301..303) {
                    val location = res.header("Location") ?: ""
                    if (location.contains("login", ignoreCase = true)) {
                        return Pair(false, "الراوتر رفض الكرت وأعاد التوجيه لصفحة الدخول")
                    }
                }

                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val hasLogout = body.contains("logout", ignoreCase = true) || body.contains("action=\"\$(link-logout)\"")
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

    // استخراج أسباب الخطأ الصريحة من قالب MikroTik باللغتين العربية والإنجليزية
    private fun extractMikrotikError(html: String): String? {
        val lower = html.lowercase()
        return when {
            lower.contains("invalid username or password") || lower.contains("غير صحيحة") || lower.contains("غير صحيح") || lower.contains("invalid") -> "رمز الكرت غير صحيح"
            (lower.contains("user") && lower.contains("not found")) || lower.contains("غير موجود") || lower.contains("غير مسجل") -> "رمز الكرت غير موجود بالنظام"
            lower.contains("already logged in") || lower.contains("مستخدم حاليا") || lower.contains("قيد الاستخدام") -> "الكرت قيد الاستخدام في جهاز آخر حاليا"
            lower.contains("uptime limit") || lower.contains("انتهى وقت") -> "انتهى وقت الكرت المتاح"
            lower.contains("traffic limit") || lower.contains("انتهى رصيد") || lower.contains("نفذ رصيد") -> "انتهى رصيد بيانات الكرت بالكامل"
            lower.contains("no more sessions") || lower.contains("استنفاذ الجلسات") -> "تم استنفاذ عدد الجلسات المسموح بها للكرت"
            lower.contains("credit expired") || lower.contains("منتهي الصلاحية") || lower.contains("منتهي") -> "صلاحية الكرت منتهية"
            lower.contains("class=\"error\"") || lower.contains("class='error'") || lower.contains("class=\"alert\"") -> "الراوتر رفض الكرت يرجى التأكد من الرمز"
            else -> null
        }
    }
}
