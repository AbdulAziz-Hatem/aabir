package com.abdulaziz.netpass

import com.abdulaziz.netpass.domain.VoucherValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherValidationTest {

    @Test
    fun normalizeToAsciiDigits_convertsArabicAndPersianDigitsCorrectly() {
        assertEquals("1234567890", VoucherValidator.normalizeToAsciiDigits("١٢٣٤٥٦٧٨٩٠"))
        assertEquals("1234567890", VoucherValidator.normalizeToAsciiDigits("۱۲۳۴۵۶۷۸۹۰"))
    }

    @Test
    fun normalizeToAsciiDigits_preservesAsciiAndFiltersNonDigits() {
        assertEquals("9876543210", VoucherValidator.normalizeToAsciiDigits("9876543210"))
        assertEquals("1234", VoucherValidator.normalizeToAsciiDigits("PIN-١٢٣4-TEST"))
    }

    @Test
    fun normalizeBounds_clampsOutOfRangeAndInvertedBounds() {
        assertEquals(4 to 16, VoucherValidator.normalizeBounds(1, 16))
        assertEquals(12 to 12, VoucherValidator.normalizeBounds(12, 6))
        assertEquals(4 to 20, VoucherValidator.normalizeBounds(4, 99))
    }

    @Test
    fun isValidPin_respectsDynamicMaxLenUpTo20() {
        val pin18 = "294817362514839201"
        assertTrue(VoucherValidator.isValidPin(pin18, minLen = 4, maxLen = 20))
        assertFalse(VoucherValidator.isValidPin(pin18, minLen = 4, maxLen = 16))
    }

    @Test
    fun isValidPin_rejectsPhoneNumbersAndServiceCodes() {
        assertFalse(VoucherValidator.isValidPin("771234567", minLen = 4, maxLen = 16))
        assertFalse(VoucherValidator.isValidPin("781234567", minLen = 4, maxLen = 16))
        assertFalse(VoucherValidator.isValidPin("8001234", minLen = 4, maxLen = 16))
        assertTrue(VoucherValidator.isValidPin("98451236", minLen = 4, maxLen = 16))
    }

    @Test
    fun extractCandidatesFromLine_fixesOcrMistakesAndJoinsGroups() {
        val candidates = VoucherValidator.extractCandidatesFromLine(
            lineText = "PIN: 4O12 - S8I9",
            minLen = 4,
            maxLen = 16
        )
        assertEquals(listOf("40125819"), candidates)
    }

    @Test
    fun extractCandidatesFromLine_handlesEasternDigitsWithSpaces() {
        val candidates = VoucherValidator.extractCandidatesFromLine(
            lineText = "الكرت ٤٥٦٧ ٨٩٠١",
            minLen = 6,
            maxLen = 12
        )
        assertEquals(listOf("45678901"), candidates)
    }
}
