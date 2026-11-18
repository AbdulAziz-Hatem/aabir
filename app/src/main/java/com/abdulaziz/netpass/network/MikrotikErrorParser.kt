package com.abdulaziz.netpass.network

object MikrotikErrorParser {

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
