package com.abdulaziz.netpass.domain

object VoucherValidator {

    const val MIN_ALLOWED_LENGTH = 4
    const val MAX_ALLOWED_LENGTH = 20

    private val DIGIT_SEPARATOR_REGEX = Regex("""(?<=\d)[\s-]+(?=\d)""")
    private val PHONE_NUMBER_REGEX = Regex("""^(77|78|73|71|70|01|02|03|04|05|06|07)\d{6,8}$""")
    private val SERVICE_CODE_REGEX = Regex("""^(101|102|107|501|601|701|800)\d*$""")

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
        return true
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
