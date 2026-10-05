package com.neilturner.aerialviews.ui.toposcan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HdrStaticMetadataTest {
    @Test
    fun `CTA metadata uses correct chromaticity and luminance units`() {
        val data = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN).put(0)
        listOf(34000, 16000, 13250, 34500, 7500, 3000, 15635, 16450, 1000, 50, 1200, 400).forEach { data.putShort(it.toShort()) }
        val values = HdrStaticMetadata.attributes(data.array())
        assertEquals(34000, values[0x3341])
        assertEquals(50_000_000, values[0x3349])
        assertEquals(250, values[0x334A])
        assertEquals(60_000_000, values[0x3360])
        assertEquals(20_000_000, values[0x3361])
    }

    @Test
    fun `missing malformed and unknown metadata never invents mastering values`() {
        assertTrue(HdrStaticMetadata.attributes(null).isEmpty())
        assertTrue(HdrStaticMetadata.attributes(ByteArray(24)).isEmpty())
        assertTrue(HdrStaticMetadata.attributes(ByteArray(25)).isEmpty())
        assertTrue(HdrStaticMetadata.attributes(ByteArray(25) { 1 }).isEmpty())
    }
}
