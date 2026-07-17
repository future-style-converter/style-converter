package com.styleconverter.runtime.core.images

// Pinning for the RFC 2397 data:-URI decoder that feeds Coil 3 ByteArray
// models (wave-8 #36 — background-image/mask-image/content url("data:…")).

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataUriTest {

    // A 1x1 transparent PNG — the canonical inline-image fixture payload.
    private val onePxPngB64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

    @Test
    fun `scheme detection is case-insensitive and precise`() {
        assertTrue(DataUri.isDataUri("data:image/png;base64,AAAA"))
        assertTrue(DataUri.isDataUri("DATA:image/png;base64,AAAA"))
        assertFalse(DataUri.isDataUri("https://example.com/a.png"))
        assertFalse(DataUri.isDataUri("database://nope"))
    }

    @Test
    fun `base64 payloads decode to their bytes`() {
        val bytes = DataUri.decode("data:image/png;base64,$onePxPngB64")!!
        // PNG magic — the decoded bytes are a real image payload.
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }

    @Test
    fun `plain-text payloads percent-decode as UTF-8`() {
        // The SVG data-URI form CSS commonly uses (non-base64).
        val bytes = DataUri.decode("data:image/svg+xml,%3Csvg%3E%3C/svg%3E")!!
        assertArrayEquals("<svg></svg>".toByteArray(), bytes)
    }

    @Test
    fun `malformed URIs return null instead of throwing`() {
        assertNull(DataUri.decode("data:image/png;base64")) // no comma
        assertNull(DataUri.decode("data:image/png;base64,!!!not-base64!!!"))
        assertNull(DataUri.decode("https://example.com/a.png")) // not data:
    }

    @Test
    fun `toModel returns bytes for data URIs and the string otherwise`() {
        val model = DataUri.toModel("data:image/png;base64,$onePxPngB64")
        assertTrue(model is ByteArray)
        val urlModel = DataUri.toModel("https://example.com/a.png")
        assertEquals("https://example.com/a.png", urlModel)
    }

    @Test
    fun `percent decoding leaves plus signs alone`() {
        // '+' is a base64 data character — URLDecoder-style '+'→space
        // corruption is exactly what this local decoder avoids.
        assertEquals("a+b", DataUri.percentDecode("a+b"))
        assertEquals("a b", DataUri.percentDecode("a%20b"))
    }

    @Test
    fun `non-base64 binary payloads decode to raw bytes, not UTF-8 mangling`() {
        // Wave-3 regression pin: %89 must decode to the single byte 0x89 —
        // the old String round-trip re-encoded it as UTF-8 (0xC2 0x89),
        // corrupting every percent-encoded PNG at its very signature, so
        // BitmapFactory returned null and Android painted no border-image
        // while iOS matched Chromium at 1.00.
        val bytes = DataUri.decode("data:image/png,%89PNG%0D%0A%1A%0A")
        assertNotNull(bytes)
        val expected = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(),
            'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `ascii svg payloads decode byte-identically through the bytes path`() {
        // Text SVG (pure ASCII) must be unaffected by the binary-path fix.
        val svg = DataUri.decode("data:image/svg+xml;utf8,%3Csvg%3E%3C/svg%3E")
        assertNotNull(svg)
        assertEquals("<svg></svg>", svg!!.toString(Charsets.UTF_8))
    }
}
