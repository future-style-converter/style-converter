package com.styleconverter.test.style.sizing

// Unit tests for extractAspectRatio. Each test matches an IR shape pulled
// verbatim from examples/properties/sizing/aspect-ratio.json.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AspectRatioValueTest {

    // Small helper so each test body fits on one screen.
    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test fun `two-number ratio reads normalizedRatio`() {
        // Ratio_16_9 in aspect-ratio.json.
        val v = extractAspectRatio(parse("""{"ratio":{"w":16.0,"h":9.0},"normalizedRatio":1.7777777777777777}"""))
        assertNotNull(v)
        // 16/9 ≈ 1.777… stored directly from normalizedRatio.
        assertEquals(1.7777777777777777, v!!.ratio, 1e-12)
        assertFalse(v.isAuto)
    }

    @Test fun `single-number ratio form yields that number`() {
        // Ratio_Numeric_1_5.
        val v = extractAspectRatio(parse("""{"ratio":{"value":1.5},"normalizedRatio":1.5}"""))
        assertNotNull(v)
        assertEquals(1.5, v!!.ratio, 1e-12)
        assertFalse(v.isAuto)
    }

    @Test fun `bare auto string yields isAuto with ratio 0`() {
        // Ratio_Auto — whole property collapses to "auto".
        val v = extractAspectRatio(parse(""""auto""""))
        assertNotNull(v)
        // ratio=0.0 is the sentinel for "auto-only" — the Applier skips
        // Modifier.aspectRatio() since Compose would crash on ratio=0.
        assertEquals(0.0, v!!.ratio, 1e-12)
        assertTrue(v.isAuto)
    }

    @Test fun `auto-with-explicit keeps ratio and flags auto`() {
        // Ratio_Auto_With_Explicit: `auto 16/9`. isAuto=true AND ratio>0.
        val v = extractAspectRatio(parse("""{"ratio":{"auto":true,"w":16.0,"h":9.0},"normalizedRatio":1.7777777777777777}"""))
        assertNotNull(v)
        assertTrue(v!!.isAuto)
        assertEquals(1.7777777777777777, v.ratio, 1e-12)
    }

    @Test fun `missing data returns null`() {
        // Null input maps to "not specified" — distinct from explicit auto.
        assertNull(extractAspectRatio(null))
    }

    @Test fun `unrelated object returns null`() {
        // Defensive: shape we don't recognise shouldn't throw.
        assertNull(extractAspectRatio(parse("""{"foo":"bar"}""")))
    }

    @Test fun `fallback uses w over h when normalizedRatio missing`() {
        // Defensive: hand-written fixtures might omit normalizedRatio. We
        // compute ratio from w/h as a fallback.
        val v = extractAspectRatio(parse("""{"ratio":{"w":4.0,"h":3.0}}"""))
        assertNotNull(v)
        assertEquals(4.0 / 3.0, v!!.ratio, 1e-12)
    }
}
