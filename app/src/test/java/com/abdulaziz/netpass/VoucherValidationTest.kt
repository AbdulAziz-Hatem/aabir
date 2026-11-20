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
    fun isValidPin_rejectsRepeatedDigitsAndSerialPrefixes() {
        assertFalse(VoucherValidator.isValidPin("00000000", minLen = 4, maxLen = 16))
        assertFalse(VoucherValidator.isValidPin("11111111", minLen = 4, maxLen = 16))
        assertFalse(VoucherValidator.isValidPin("0000123456", minLen = 4, maxLen = 16))
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

    @Test
    fun extractCandidatesFromLine_extractsVariableLengths10_12_14() {
        val pin10 = VoucherValidator.extractCandidatesFromLine(
            lineText = "كود الكرت: 1234 5678 90",
            minLen = 6,
            maxLen = 16
        )
        assertEquals(listOf("1234567890"), pin10)

        val pin12 = VoucherValidator.extractCandidatesFromLine(
            lineText = "PIN: 4012 - 5819 - 2314",
            minLen = 6,
            maxLen = 16
        )
        assertEquals(listOf("401258192314"), pin12)

        val pin14 = VoucherValidator.extractCandidatesFromLine(
            lineText = "الرمز 9812.3456.7890.12",
            minLen = 6,
            maxLen = 16
        )
        assertEquals(listOf("98123456789012"), pin14)
    }

    @Test
    fun evaluateCandidateScore_prioritizesVoucherContextOverNoise() {
        val pinScore = VoucherValidator.evaluateCandidateScore(
            candidate = "4567",
            lineText = "رمز الكرت 4567",
            blockText = "شبكة المجد - كود الدخول",
            lineHeight = 35,
            isCenteredX = true,
            isCenteredY = true
        )
        val phoneScore = VoucherValidator.evaluateCandidateScore(
            candidate = "771234567",
            lineText = "للتواصل هاتف: 771234567",
            blockText = "خدمة العملاء والمبيعات",
            lineHeight = 20,
            isCenteredX = false,
            isCenteredY = false
        )
        val yearScore = VoucherValidator.evaluateCandidateScore(
            candidate = "2026",
            lineText = "صلاحية الكرت 2026",
            blockText = "تاريخ الانتهاء",
            lineHeight = 20,
            isCenteredX = true,
            isCenteredY = false
        )

        assertTrue("Voucher PIN score must exceed phone number score", pinScore > phoneScore)
        assertTrue("Voucher PIN score must exceed calendar year score", pinScore > yearScore)
        assertTrue("Calendar year score should be penalized heavily", yearScore < 0)
    }

    @Test
    fun evaluateCandidateScore_prioritizesStandardVoucherLengthsAndPenalizesSerials() {
        val pin12Score = VoucherValidator.evaluateCandidateScore(
            candidate = "401258192314",
            lineText = "PIN: 401258192314",
            blockText = "كود كرت الدخول",
            lineHeight = 40,
            isCenteredX = true,
            isCenteredY = true
        )

        val serialScore = VoucherValidator.evaluateCandidateScore(
            candidate = "000012938475",
            lineText = "S/N: 000012938475",
            blockText = "الرقم التسلسلي للطباعة",
            lineHeight = 20,
            isCenteredX = false,
            isCenteredY = false
        )

        assertTrue("Standard voucher PIN score must exceed serial number score", pin12Score > serialScore)
        assertTrue("Serial with 0000 and negative context should have low/negative score", serialScore < 50)
    }
}
