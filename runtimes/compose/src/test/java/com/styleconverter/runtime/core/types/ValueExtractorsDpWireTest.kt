package com.styleconverter.runtime.core.types

// Pins extractDp against the LIVE converter wire for lengths — including
// the THIRD wire generation discovered on line-height (verified 2026-07 by
// running `:converter:run` on fixtures/properties/typography/line-height.json):
//   'line-height: 24px' → {"original":{"type":"length","px":24.0}}
//   'line-height: 2rem' → {"original":{"type":"length","original":{"v":2.0,"u":"REM"}}}
// i.e. NO top-level px and NO px:0.0 sentinel — the resolved pixel count
// rides INSIDE the typed original wrapper. extractDp previously read only
// the top-level px and the FLAT {v,u} original, so the nested plain-px
// shape was silently dropped (pixel-proven: Android rendered the 24px line
// box at the font-natural height — inkTop 8 instead of 9).

import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValueExtractorsDpWireTest {

    // Shared JSON helper — every test body is one parse + one assert.
    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test
    fun `live wire - nested plain px inside typed original wrapper`() {
        // The exact 'line-height: 24px' wire captured from the running
        // converter — the bug this file exists to pin.
        assertEquals(
            24.dp,
            ValueExtractors.extractDp(parse("""{"original":{"type":"length","px":24.0}}"""))
        )
    }

    @Test
    fun `nested v-u relative pair is NOT resolved here - ownership contract`() {
        // 'line-height: 2rem' wire: the raw IRLength {v,u} sits under a
        // SECOND original key. extractDp must NOT resolve it at a
        // hardcoded 16 — the bases are owned by callers with real context
        // (FontSize resolves em against the threaded INHERITED size;
        // DynamicValueResolver pre-flattens em/rem against the LIVE font
        // size and writes a top-level px, which the first read catches).
        // Resolving here masked the inherited-base FontSize path.
        assertNull(
            ValueExtractors.extractDp(
                parse("""{"original":{"type":"length","original":{"v":2.0,"u":"REM"}}}""")
            )
        )
    }

    @Test
    fun `nested px under the inner original is also reachable`() {
        // Defensive: if a wire generation nests the resolved px one level
        // deeper (inner IRLength carrying px), the single descend finds it.
        assertEquals(
            12.dp,
            ValueExtractors.extractDp(
                parse("""{"original":{"type":"length","original":{"px":12.0}}}""")
            )
        )
    }

    @Test
    fun `top-level px stays canonical and wins over nested shapes`() {
        // The historic normalized shape — first read, unchanged.
        assertEquals(16.dp, ValueExtractors.extractDp(parse("""{"px":16.0}""")))
        // When both exist the TOP-LEVEL px is authoritative (it is what
        // DynamicValueResolver writes after resolving relative units).
        assertEquals(
            10.dp,
            ValueExtractors.extractDp(
                parse("""{"px":10.0,"original":{"type":"length","px":24.0}}""")
            )
        )
    }

    @Test
    fun `px zero sentinel still returns zero`() {
        // The letter-spacing escape hatch in TextStyleApplier RELIES on
        // extractDp returning 0 for the {"px":0.0,"original":…} sentinel
        // wire (it detects the bogus 0 itself) — the nested-px fix must
        // not change that contract.
        assertEquals(
            0.dp,
            ValueExtractors.extractDp(
                parse("""{"px":0.0,"original":{"type":"length","original":{"v":0.25,"u":"REM"}}}""")
            )
        )
    }

    @Test
    fun `flat v-u original keeps its 16px em-rem fallback`() {
        // The historic FLAT {v,u} original (no typed wrapper) — the
        // pre-existing fallback path, still resolved at the 16px default.
        assertEquals(24.dp, ValueExtractors.extractDp(parse("""{"original":{"v":1.5,"u":"EM"}}""")))
    }

    @Test
    fun `unresolvable viewport unit still returns null`() {
        // vw has no honest static base in this extractor — must stay null
        // (no silent guess), same as before the fix.
        assertNull(
            ValueExtractors.extractDp(
                parse("""{"original":{"type":"length","original":{"v":50.0,"u":"VW"}}}""")
            )
        )
    }

    @Test
    fun `bare primitive number still reads as px`() {
        // Legacy shape used by some unit fixtures — untouched.
        assertEquals(10.dp, ValueExtractors.extractDp(parse("10.0")))
    }
}
