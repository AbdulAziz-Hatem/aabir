package com.abdulaziz.netpass

import com.abdulaziz.netpass.domain.GatewayHostValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayHostValidatorTest {

    @Test
    fun sanitizeAndValidate_acceptsValidIpv4AndPorts() {
        assertEquals("10.0.0.1", GatewayHostValidator.sanitizeAndValidate(" 10.0.0.1 "))
        assertEquals("192.168.88.1:8080", GatewayHostValidator.sanitizeAndValidate("192.168.88.1:8080"))
    }

    @Test
    fun sanitizeAndValidate_acceptsLocalMikrotikHostnames() {
        assertEquals("login.net", GatewayHostValidator.sanitizeAndValidate("LOGIN.NET"))
        assertEquals("hotspot.local:80", GatewayHostValidator.sanitizeAndValidate("hotspot.local:80"))
    }

    @Test
    fun sanitizeAndValidate_rejectsMalformedIpsAndInjectionAttempts() {
        assertNull(GatewayHostValidator.sanitizeAndValidate(""))
        assertNull(GatewayHostValidator.sanitizeAndValidate("999.1.1.1"))
        assertNull(GatewayHostValidator.sanitizeAndValidate("010.0.0.1"))
        assertNull(GatewayHostValidator.sanitizeAndValidate("http://10.0.0.1"))
        assertNull(GatewayHostValidator.sanitizeAndValidate("10.0.0.1/login"))
        assertNull(GatewayHostValidator.sanitizeAndValidate("10.0.0.1@evil.com"))
        assertNull(GatewayHostValidator.sanitizeAndValidate("10.0.0.1:70000"))
        assertNull(GatewayHostValidator.sanitizeAndValidate("10.0.0.1\r\nX-Injected: 1"))
    }

    @Test
    fun resolveTargetHost_prioritizesCustomThenDetectedThenDefault() {
        assertEquals(
            "login.net",
            GatewayHostValidator.resolveTargetHost("login.net", "192.168.88.1")
        )
        assertNull(
            GatewayHostValidator.resolveTargetHost("http://bad/host", "192.168.88.1")
        )
        assertEquals(
            "192.168.88.1",
            GatewayHostValidator.resolveTargetHost("", "192.168.88.1")
        )
        assertEquals(
            "10.0.0.1",
            GatewayHostValidator.resolveTargetHost("", "")
        )
    }
}
