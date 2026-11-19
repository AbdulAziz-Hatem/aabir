package com.abdulaziz.netpass

import com.abdulaziz.netpass.network.MikrotikErrorParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MikrotikErrorParserTest {

    @Test
    fun extractError_detectsArabicAndEnglishMikrotikErrors() {
        assertEquals(
            "رمز الكرت غير صحيح",
            MikrotikErrorParser.extractError("<div>Invalid username or password</div>")
        )
        assertEquals(
            "الكرت قيد الاستخدام في جهاز آخر حاليا",
            MikrotikErrorParser.extractError("<p>هذا الكرت قيد الاستخدام حالياً</p>")
        )
        assertEquals(
            "انتهى رصيد بيانات الكرت بالكامل",
            MikrotikErrorParser.extractError("Traffic limit reached")
        )
        assertNull(MikrotikErrorParser.extractError("<html><body>Welcome</body></html>"))
    }

    @Test
    fun isLoginRedirect_detects3xxRedirectToLogin() {
        assertTrue(MikrotikErrorParser.isLoginRedirect(302, "http://10.0.0.1/login"))
        assertFalse(MikrotikErrorParser.isLoginRedirect(302, "http://10.0.0.1/status"))
        assertFalse(MikrotikErrorParser.isLoginRedirect(200, "http://10.0.0.1/login"))
    }

    @Test
    fun isStatusPageLoggedIn_distinguishesActiveSessionFromLoginForm() {
        val statusHtml = """<html><body><form action="/logout"><button>Logout</button></form></body></html>"""
        val loginHtml = """<html><body><form><input name="username"/><input type="password"/></form></body></html>"""
        assertTrue(MikrotikErrorParser.isStatusPageLoggedIn(statusHtml))
        assertFalse(MikrotikErrorParser.isStatusPageLoggedIn(loginHtml))
    }

    @Test
    fun decodeResponseBody_decodesUtf8AndWindows1256MetaHtml() {
        val utf8Html = "<html><head><meta charset=\"utf-8\"></head><body>رمز الكرت غير صحيح</body></html>"
        val utf8Bytes = utf8Html.toByteArray(Charsets.UTF_8)
        val decodedUtf8 = MikrotikErrorParser.decodeResponseBody(utf8Bytes, null)
        assertEquals("رمز الكرت غير صحيح", MikrotikErrorParser.extractError(decodedUtf8))

        val win1256Charset = java.nio.charset.Charset.forName("windows-1256")
        val win1256Html = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1256\"></head><body>رمز الكرت غير صحيح</body></html>"
        val win1256Bytes = win1256Html.toByteArray(win1256Charset)
        val decodedWin1256 = MikrotikErrorParser.decodeResponseBody(win1256Bytes, null)
        assertEquals("رمز الكرت غير صحيح", MikrotikErrorParser.extractError(decodedWin1256))
    }
}
