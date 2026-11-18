package com.abdulaziz.netpass.data.local

import com.abdulaziz.netpass.model.VoucherRecord

object VoucherHistoryCodec {

    const val MAX_HISTORY_ENTRIES = 50

    private val JSON_OBJECT_REGEX = Regex("""\{[^{}]*\}""")
    private val PIN_FIELD_REGEX = Regex(""""pin"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    private val TIMESTAMP_FIELD_REGEX = Regex(""""timestamp"\s*:\s*(-?\d+)""")

    fun encode(records: List<VoucherRecord>, maxEntries: Int = MAX_HISTORY_ENTRIES): String {
        val limited = records.take(maxEntries)
        if (limited.isEmpty()) return "[]"

        return limited.joinToString(separator = ",", prefix = "[", postfix = "]") { record ->
            val escapedPin = escapeJson(record.pin)
            """{"pin":"$escapedPin","timestamp":${record.timestamp}}"""
        }
    }

    fun decode(raw: String): List<VoucherRecord> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        return if (trimmed.startsWith("[")) {
            decodeJson(trimmed)
        } else {
            decodeLegacy(trimmed)
        }
    }

    private fun decodeJson(json: String): List<VoucherRecord> {
        return JSON_OBJECT_REGEX.findAll(json).mapNotNull { objMatch ->
            val obj = objMatch.value
            val pinEncoded = PIN_FIELD_REGEX.find(obj)?.groupValues?.get(1) ?: return@mapNotNull null
            val timestamp = TIMESTAMP_FIELD_REGEX.find(obj)?.groupValues?.get(1)?.toLongOrNull()
                ?: return@mapNotNull null
            val pin = unescapeJson(pinEncoded)
            if (pin.isEmpty()) return@mapNotNull null
            VoucherRecord(pin = pin, timestamp = timestamp)
        }.toList()
    }

    private fun decodeLegacy(raw: String): List<VoucherRecord> {
        return raw.split(";").mapNotNull { entry ->
            val sep = entry.lastIndexOf(':')
            if (sep <= 0 || sep >= entry.lastIndex) return@mapNotNull null
            val pin = entry.substring(0, sep)
            val ts = entry.substring(sep + 1).toLongOrNull() ?: return@mapNotNull null
            if (pin.isEmpty()) return@mapNotNull null
            VoucherRecord(pin = pin, timestamp = ts)
        }
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(value[i + 1])
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}
