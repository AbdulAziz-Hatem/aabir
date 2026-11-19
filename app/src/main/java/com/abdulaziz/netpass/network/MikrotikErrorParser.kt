package com.abdulaziz.netpass.network

import java.nio.charset.Charset

object MikrotikErrorParser {

    private val CHARSET_META_REGEX = Regex("""(?i)<meta[^>]+charset=["']?([a-zA-Z0-9_-]+)""")

    fun decodeResponseBody(bytes: ByteArray, contentTypeCharset: Charset?): String {
        if (contentTypeCharset != null) {
            return String(bytes, contentTypeCharset)
        }

        val preview = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        val detectedCharsetName = CHARSET_META_REGEX.find(preview)?.groupValues?.get(1)

        val targetCharset = try {
            if (detectedCharsetName != null) Charset.forName(detectedCharsetName) else Charsets.UTF_8
        } catch (_: Exception) {
            try { Charset.forName("windows-1256") } catch (_: Exception) { Charsets.UTF_8 }
        }

        return String(bytes, targetCharset)
    }

    fun isLoginRedirect(statusCode: Int, locationHeader: String?): Boolean {
        return statusCode in 301..303 &&
            locationHeader?.contains("login", ignoreCase = true) == true
    }

    fun isStatusPageLoggedIn(html: String): Boolean {
        val hasLogout = html.contains("logout", ignoreCase = true) ||
            html.contains("action=\"\$(link-logout)\"")
        val isLoginPage = html.contains("""name="username"""", ignoreCase = true) ||
            html.contains("""type="password"""", ignoreCase = true)
        return hasLogout && !isLoginPage
    }

    fun extractError(html: String): String? {
        val lower = html.lowercase()
        return when {
            lower.contains("invalid username or password") ||
                lower.contains("غير صحيحة") ||
                lower.contains("غير صحيح") ||
                lower.contains("invalid") -> "رمز الكرت غير صحيح"

            (lower.contains("user") && lower.contains("not found")) ||
                lower.contains("غير موجود") ||
                lower.contains("غير مسجل") -> "رمز الكرت غير موجود بالنظام"

            lower.contains("already logged in") ||
                lower.contains("مستخدم حاليا") ||
                lower.contains("قيد الاستخدام") -> "الكرت قيد الاستخدام في جهاز آخر حاليا"

            lower.contains("uptime limit") ||
                lower.contains("انتهى وقت") -> "انتهى وقت الكرت المتاح"

            lower.contains("traffic limit") ||
                lower.contains("انتهى رصيد") ||
                lower.contains("نفذ رصيد") -> "انتهى رصيد بيانات الكرت بالكامل"

            lower.contains("no more sessions") ||
                lower.contains("استنفاذ الجلسات") -> "تم استنفاذ عدد الجلسات المسموح بها للكرت"

            lower.contains("credit expired") ||
                lower.contains("منتهي الصلاحية") ||
                lower.contains("منتهي") -> "صلاحية الكرت منتهية"

            lower.contains("""class="error"""") ||
                lower.contains("class='error'") ||
                lower.contains("""class="alert"""") -> "الراوتر رفض الكرت يرجى التأكد من الرمز"

            else -> null
        }
    }
}
