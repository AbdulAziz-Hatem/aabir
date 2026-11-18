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
}
