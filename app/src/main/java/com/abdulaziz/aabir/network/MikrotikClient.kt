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
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    // التحقق اللحظي من اتصال الهاتف بالواي فاي
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // استخراج عنوان الآي بي الحقيقي لبوابة الواي فاي المتصلة
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

    // تسجيل الدخول في راوتر ميكروتك مع الاستجابة الفورية
    suspend fun login(
        context: Context,
        pin: String,
        customGateway: String = ""
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanPin = pin.filter { it.isDigit() }
        if (cleanPin.length < 4) {
            return@withContext Pair(false, "رمز الكرت قصير جداً، يرجى إدخال 4 أرقام على الأقل")
        }

        // 1. الفحص الفوري: هل الهاتف متصل بالواي فاي؟ (إنهاء في 0ms دون تعليق)
        if (!isWifiConnected(context)) {
            return@withContext Pair(false, "أنت غير متصل بالواي فاي! يرجى الاتصال بشبكة ميكروتك أولاً")
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        
        try {
            // ربط الحزم بمقبس الواي فاي
            cm?.allNetworks?.forEach { network ->
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    cm.bindProcessToNetwork(network)
                    return@forEach
                }
            }

            // 2. تحديد البوابة المباشرة بدقة دون تدوير عشوائي
            val detectedGateway = getActiveGatewayIp(context)
            val targetHost = when {
                customGateway.isNotBlank() -> customGateway.trim()
                detectedGateway.isNotBlank() -> detectedGateway
                else -> "10.0.0.1"
            }

            val payloads = listOf(
                Pair(cleanPin, ""),         // النمط اليمني الشائع: اسم مستخدم فقط
                Pair(cleanPin, cleanPin)    // نمط اسم المستخدم المطابق لكلمة المرور
            )

            for ((user, pass) in payloads) {
                try {
                    val formBuilder = FormBody.Builder()
                        .add("username", user)
                    if (pass.isNotEmpty()) {
                        formBuilder.add("password", pass)
                    }
                    formBuilder.add("dst", "http://$targetHost/status")

                    val request = Request.Builder()
                        .url("http://$targetHost/login")
                        .post(formBuilder.build())
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful || response.code in 200..302) {
                            if (verifyStatusPage(targetHost)) {
                                return@withContext Pair(true, "تم تسجيل الدخول وتفعيل الإنترنت بنجاح! الكود: $cleanPin")
                            }
                        }
                    }
                } catch (ignored: Exception) {
                    continue
                }
            }

            return@withContext Pair(false, "فشل تسجيل الدخول؛ قد يكون الكرت غير صحيح أو تم استخدامه مسبقاً")
        } finally {
            try {
                cm?.bindProcessToNetwork(null)
            } catch (ignored: Exception) {}
        }
    }

    private fun verifyStatusPage(host: String): Boolean {
        return try {
            val statusReq = Request.Builder()
                .url("http://$host/status")
                .get()
                .build()
            client.newCall(statusReq).execute().use { res ->
                val body = res.body?.string() ?: ""
                body.contains("logout", ignoreCase = true) ||
                body.contains("bytes", ignoreCase = true) ||
                body.contains("connected", ignoreCase = true)
            }
        } catch (e: Exception) {
            verifyDirectNumericIp()
        }
    }

    private fun verifyDirectNumericIp(): Boolean {
        return try {
            val req = Request.Builder()
                .url("http://1.1.1.1/generate_204")
                .get()
                .build()
            client.newCall(req).execute().use { it.code in 200..204 }
        } catch (e: Exception) {
            false
        }
    }
}
