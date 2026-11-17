package com.abdulaziz.netpass

import com.abdulaziz.netpass.network.MikrotikClient
import org.junit.Assert.assertEquals
import org.junit.Test

class VoucherValidationTest {

    @Test
    fun normalizeToAsciiDigits_convertsArabicEasternDigitsCorrectly() {
        val input = "١٢٣٤٥٦٧٨٩٠"
        val expected = "1234567890"
        val actual = MikrotikClient.normalizeToAsciiDigits(input)
        assertEquals(expected, actual)
    }

    @Test
    fun normalizeToAsciiDigits_preservesStandardAsciiDigits() {
        val input = "9876543210"
        val actual = MikrotikClient.normalizeToAsciiDigits(input)
        assertEquals("9876543210", actual)
    }

    @Test
    fun normalizeToAsciiDigits_filtersOutNonDigits() {
        val input = "PIN-١٢٣4-TEST"
        val actual = MikrotikClient.normalizeToAsciiDigits(input)
        assertEquals("1234", actual)
    }
}
