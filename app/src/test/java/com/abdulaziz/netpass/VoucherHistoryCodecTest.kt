package com.abdulaziz.netpass

import com.abdulaziz.netpass.data.local.VoucherHistoryCodec
import com.abdulaziz.netpass.model.VoucherRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherHistoryCodecTest {

    @Test
    fun encodeAndDecode_roundTripsJsonRecords() {
        val records = listOf(
            VoucherRecord("12345678", 1700000000000L),
            VoucherRecord("87654321", 1700000005000L)
        )
        val encoded = VoucherHistoryCodec.encode(records)
        assertTrue(encoded.startsWith("["))
        assertEquals(records, VoucherHistoryCodec.decode(encoded))
    }

    @Test
    fun decode_migratesLegacySemicolonFormatSeamlessly() {
        val legacy = "11223344:1700000000001;55667788:1700000000002;corrupted"
        val decoded = VoucherHistoryCodec.decode(legacy)
        assertEquals(
            listOf(
                VoucherRecord("11223344", 1700000000001L),
                VoucherRecord("55667788", 1700000000002L)
            ),
            decoded
        )
    }

    @Test
    fun encode_enforcesMaxHistoryLimit() {
        val records = (1..60).map { VoucherRecord("1000$it", it.toLong()) }
        val decoded = VoucherHistoryCodec.decode(VoucherHistoryCodec.encode(records))
        assertEquals(VoucherHistoryCodec.MAX_HISTORY_ENTRIES, decoded.size)
        assertEquals("10001", decoded.first().pin)
    }

    @Test
    fun decode_handlesEmptyInput() {
        assertEquals(emptyList<VoucherRecord>(), VoucherHistoryCodec.decode(""))
        assertEquals(emptyList<VoucherRecord>(), VoucherHistoryCodec.decode("[]"))
    }
}
