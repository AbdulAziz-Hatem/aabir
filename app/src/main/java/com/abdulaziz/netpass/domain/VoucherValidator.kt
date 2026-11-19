package com.abdulaziz.netpass.domain

object VoucherValidator {

    const val MIN_ALLOWED_LENGTH = 4
    const val MAX_ALLOWED_LENGTH = 20

    private val DIGIT_SEPARATOR_REGEX = Regex("""(?<=\d)[\s-]+(?=\d)""")
    private val PHONE_NUMBER_REGEX = Regex("""^(77|78|73|71|70|01|02|03|04|05|06|07)\d{6,8}$""")
    private val SERVICE_CODE_REGEX = Regex("""^(101|102|107|501|601|701|800)\d*$""")
    private val CALENDAR_YEAR_REGEX = Regex("""^(202[4-9]|203[0-5])$""")

    private val POSITIVE_CONTEXT_KEYWORDS = listOf(
        "رمز", "الرمز", "كود", "الكود", "كرت", "الكرت", "سري", "السري",
        "دخول", "الدخول", "مستخدم", "المستخدم", "pin", "code", "pass",
        "password", "user", "voucher", "card"
    )

    private val NEGATIVE_CONTEXT_KEYWORDS = listOf(
        "هاتف", "تلفون", "موبايل", "جوال", "اتصال", "تواصل", "خدمة", "عملاء",
        "دعم", "شكاوى", "مبيعات", "استفسار", "تسلسلي", "تسلسل", "رقم",
        "tel", "phone", "mobile", "sn", "s/n", "serial", "no", "call",
        "ريال", "yer", "yr", "ميجا", "جيجا", "mb", "gb", "ساعة", "يوم"
    )

    private val ROUND_DENOMINATIONS = setOf(
        "100", "200", "250", "300", "500", "1000", "1500", "2000", "2500", "3000", "5000", "10000"
    )

    fun normalizeBounds(minLen: Int, maxLen: Int): Pair<Int, Int> {
        val safeMin = minLen.coerceIn(MIN_ALLOWED_LENGTH, MAX_ALLOWED_LENGTH)
        val safeMax = maxLen.coerceIn(safeMin, MAX_ALLOWED_LENGTH)
        return safeMin to safeMax
    }

    fun normalizeToAsciiDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch - '٠')).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch - '۰')).toChar())
                in '0'..'9' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun convertEasternDigitsPreservingText(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch - '٠')).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch - '۰')).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun cleanOcrMistakes(text: String): String {
        return convertEasternDigitsPreservingText(text)
            .replace(Regex("[Oo]"), "0")
            .replace(Regex("[Il|]"), "1")
            .replace(Regex("[Ss]"), "5")
            .replace('B', '8')
    }

    fun isValidPin(
        digits: String,
        minLen: Int = MIN_ALLOWED_LENGTH,
        maxLen: Int = 16
    ): Boolean {
        val (safeMin, safeMax) = normalizeBounds(minLen, maxLen)
        if (digits.length !in safeMin..safeMax) return false
        if (!digits.all { it in '0'..'9' }) return false
        if (PHONE_NUMBER_REGEX.matches(digits)) return false
        if (SERVICE_CODE_REGEX.matches(digits)) return false
        if (CALENDAR_YEAR_REGEX.matches(digits)) return false
        return true
    }

    fun evaluateCandidateScore(
        candidate: String,
        lineText: String,
        blockText: String,
        lineHeight: Int,
        isCenteredX: Boolean,
        isCenteredY: Boolean
    ): Int {
        var score = 100

        if (candidate.length == 4 && CALENDAR_YEAR_REGEX.matches(candidate)) {
            score -= 150
        }

        if (ROUND_DENOMINATIONS.contains(candidate)) {
            score -= 120
        }

        val combinedContext = "$lineText $blockText".lowercase()

        for (neg in NEGATIVE_CONTEXT_KEYWORDS) {
            if (combinedContext.contains(neg)) {
                score -= 80
                break
            }
        }

        for (pos in POSITIVE_CONTEXT_KEYWORDS) {
            if (combinedContext.contains(pos)) {
                score += 90
                break
            }
        }

        if (isCenteredX) score += 30
        if (isCenteredY) score += 30

        score += lineHeight.coerceAtMost(50)

        if (candidate.length in 6..12) {
            score += 20
        }

        return score
    }

    fun extractCandidatesFromLine(
        lineText: String,
        minLen: Int = MIN_ALLOWED_LENGTH,
        maxLen: Int = 16
    ): List<String> {
        val (safeMin, safeMax) = normalizeBounds(minLen, maxLen)
        val cleaned = cleanOcrMistakes(lineText)
        val joined = cleaned.replace(DIGIT_SEPARATOR_REGEX, "")
        val tokenRegex = Regex("""\b\d{$safeMin,$safeMax}\b""")

        return tokenRegex.findAll(joined)
            .map { it.value }
            .filter { isValidPin(it, safeMin, safeMax) }
            .toList()
    }
}
