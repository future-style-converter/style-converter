package com.styleconverter.runtime.borders

// Retro R6 (audit finding A6#0) — both live `border-image-repeat` wire shapes.
// The converter emits the single-keyword form as a BARE STRING ("ROUND") and
// the two-keyword form as {"horizontal": …, "vertical": …}; the Compose
// reader accepted only the object, so `round | repeat | space` all rendered
// stretch/stretch on Android (iOS handled both shapes). The strings below are
// VERBATIM converter output for fixtures/properties/borders/border-image-
// repeat.json (`./gradlew :converter:run --args="convert --from css --to ir
// -i fixtures/properties/borders/border-image-repeat.json -o <dir>"`).
// css-backgrounds-3 §5.5: one keyword sets both axes; "If the second keyword
// is absent, it is assumed to be the same as the first."
// Proven able to fail: restoring `if (data !is JsonObject) return stretch`
// fails the first test; restoring the non-null extractRepeatValue fails
// `object form without vertical mirrors horizontal`.

import com.styleconverter.runtime.borders.image.BorderImageExtractor
import com.styleconverter.runtime.borders.image.BorderImageRepeatValue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BorderImageRepeatWireTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun repeatOf(wire: String): Pair<BorderImageRepeatValue, BorderImageRepeatValue> {
        val cfg = BorderImageExtractor.extractBorderImageConfig(listOf("BorderImageRepeat" to parse(wire)))
        return cfg.repeatHorizontal to cfg.repeatVertical
    }

    @Test
    fun `bare-string single keyword sets both axes`() {
        // BIR_Round / BIR_Space / V3_repeat / V2_stretch, verbatim.
        assertEquals(BorderImageRepeatValue.ROUND to BorderImageRepeatValue.ROUND, repeatOf("\"ROUND\""))
        assertEquals(BorderImageRepeatValue.SPACE to BorderImageRepeatValue.SPACE, repeatOf("\"SPACE\""))
        assertEquals(BorderImageRepeatValue.REPEAT to BorderImageRepeatValue.REPEAT, repeatOf("\"REPEAT\""))
        assertEquals(BorderImageRepeatValue.STRETCH to BorderImageRepeatValue.STRETCH, repeatOf("\"STRETCH\""))
    }

    @Test
    fun `object form carries independent axes`() {
        assertEquals(
            BorderImageRepeatValue.REPEAT to BorderImageRepeatValue.STRETCH,
            repeatOf("""{"horizontal": "REPEAT", "vertical": "STRETCH"}"""),
        )
        assertEquals(
            BorderImageRepeatValue.ROUND to BorderImageRepeatValue.SPACE,
            repeatOf("""{"horizontal": "ROUND", "vertical": "SPACE"}"""),
        )
    }

    @Test
    fun `object form without vertical mirrors horizontal`() {
        // §5.5 absent-second-keyword rule — the elvis that kotlinc flagged as
        // dead ("always returns the left operand") is live now.
        assertEquals(BorderImageRepeatValue.SPACE to BorderImageRepeatValue.SPACE, repeatOf("""{"horizontal": "SPACE"}"""))
    }

    @Test
    fun `absent or unknown keywords fall back to the initial stretch`() {
        // Initial value (§5.5) for no property at all…
        val none = BorderImageExtractor.extractBorderImageConfig(emptyList())
        assertEquals(BorderImageRepeatValue.STRETCH to BorderImageRepeatValue.STRETCH, none.repeatHorizontal to none.repeatVertical)
        // …and for a keyword this reader does not know (logged, not silent).
        assertEquals(BorderImageRepeatValue.STRETCH to BorderImageRepeatValue.STRETCH, repeatOf("\"BOGUS\""))
        assertEquals(BorderImageRepeatValue.STRETCH to BorderImageRepeatValue.STRETCH, repeatOf("""{"horizontal": "BOGUS"}"""))
    }
}
