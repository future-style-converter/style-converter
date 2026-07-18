package app.parsing.css.properties.longhands.typography

// Regression suite for the `font-style: oblique <angle>` unit-suffix fix
// (css-fonts-4 §2.4 — font-style = normal | italic | oblique <angle>?).
//
// Root cause pinned here: the old parseAngle tested suffixes in the order
// deg → rad → grad, and "10grad".endsWith("rad") is true — so every grad
// angle hit the rad branch, "10g".toDoubleOrNull() returned null, and the
// declaration silently degraded to bare `oblique` (= the 14deg css-fonts-4
// §2.4 default → italic appearance) when 10grad = 9deg must stay upright
// (below the measured 14deg Chromium synthetic-oblique cutoff the runtimes
// threshold against).
//
// Pinned invariants:
//   1. `oblique 10grad`  → 9deg on the wire (grad ×0.9), GRAD original kept.
//   2. `oblique 100grad` → exactly 90deg (the top of the legal oblique band).
//   3. `oblique 200grad` → 180deg source clamps to 90deg (css-fonts-4 §2.4
//      range [-90deg, 90deg]); original value/unit preserved.
//   4. `oblique 0.2rad`  → 11.459…deg (rad ×180/π).
//   5. `oblique 0.05turn`→ 18deg (turn ×360).
//   6. `oblique garbage` → WHOLE declaration rejected (parse returns null):
//      css-fonts-4 §2.4 grammar + CSS2 §4.2 error handling — an invalid
//      <angle> invalidates the declaration; it must NOT degrade to bare
//      `oblique`.

import app.irmodels.IRAngle
import app.irmodels.properties.typography.FontStyleProperty
import app.irmodels.properties.typography.FontStyleSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontStylePropertyParserTest {

    /** Unwrap helper — parse CSS and return the Oblique variant's IRAngle. */
    private fun angleOf(css: String): IRAngle? {
        // Run the real parser exactly as PropertyParserRegistry would.
        val prop = FontStylePropertyParser.parse(css) as? FontStyleProperty ?: return null
        // Only the Oblique variant carries an angle payload.
        return (prop.style as? FontStyleProperty.FontStyle.Oblique)?.angle
    }

    @Test
    fun `oblique 10grad normalizes to 9deg and keeps the GRAD original`() {
        // The historical bug case: grad suffix must not match the rad branch.
        val angle = assertNotNull(angleOf("oblique 10grad"), "10grad must parse")
        // 10grad × 0.9 = 9deg — below the 14deg cutoff, i.e. upright.
        assertEquals(9.0, angle.degrees, 1e-9)
        // Original value/unit survive for CSS regeneration fidelity.
        assertEquals(10.0, angle.originalValue, 1e-9)
        assertEquals(IRAngle.AngleUnit.GRAD, angle.originalUnit)
    }

    @Test
    fun `oblique 10grad wire shape carries deg 9 with a GRAD original key`() {
        // Serialize through the real wire serializer to pin the byte shape
        // the three runtimes decode: {"oblique":{"deg":9.0,"original":…}}.
        val prop = FontStylePropertyParser.parse("oblique 10grad") as FontStyleProperty
        val wire = Json.encodeToJsonElement(FontStyleSerializer, prop.style).jsonObject
        // The oblique payload object holds the normalized degrees.
        val payload = wire["oblique"]!!.jsonObject
        assertEquals(9.0, payload["deg"]!!.jsonPrimitive.double, 1e-9)
        // Non-deg source units add the "original" {v,u} pair (ValueTypes.kt
        // IRAngleSerializer — only emitted when originalUnit != DEG).
        val original = payload["original"]!!.jsonObject
        assertEquals(10.0, original["v"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("GRAD", original["u"]!!.jsonPrimitive.content)
    }

    @Test
    fun `oblique 100grad is exactly 90deg — the top of the legal band`() {
        // 100grad × 0.9 = 90deg exactly: inside [-90, 90], no clamp needed.
        val angle = assertNotNull(angleOf("oblique 100grad"))
        assertEquals(90.0, angle.degrees, 1e-9)
    }

    @Test
    fun `oblique 200grad clamps to 90deg and preserves the original`() {
        // 200grad = 180deg source — outside css-fonts-4 §2.4's [-90deg,
        // 90deg] oblique range, so the wire degrees clamp to the boundary.
        val angle = assertNotNull(angleOf("oblique 200grad"))
        assertEquals(90.0, angle.degrees, 1e-9)
        // The authored value/unit are still preserved for regeneration.
        assertEquals(200.0, angle.originalValue, 1e-9)
        assertEquals(IRAngle.AngleUnit.GRAD, angle.originalUnit)
    }

    @Test
    fun `oblique -200grad clamps to minus 90deg`() {
        // Negative out-of-range mirrors the positive clamp (band is ±90).
        val angle = assertNotNull(angleOf("oblique -200grad"))
        assertEquals(-90.0, angle.degrees, 1e-9)
    }

    @Test
    fun `oblique 0_2rad normalizes to 11_459deg`() {
        // 0.2rad × 180/π = 11.4591559…deg — the runtimes' below-cutoff pin.
        val angle = assertNotNull(angleOf("oblique 0.2rad"))
        assertEquals(11.459155902616464, angle.degrees, 1e-6)
        // rad source keeps its original for regeneration.
        assertEquals(IRAngle.AngleUnit.RAD, angle.originalUnit)
    }

    @Test
    fun `oblique 0_05turn normalizes to 18deg`() {
        // 0.05turn × 360 = 18deg — the runtimes' above-cutoff pin.
        val angle = assertNotNull(angleOf("oblique 0.05turn"))
        assertEquals(18.0, angle.degrees, 1e-9)
        assertEquals(IRAngle.AngleUnit.TURN, angle.originalUnit)
    }

    @Test
    fun `oblique 14deg still parses as plain degrees`() {
        // Backward compat: the deg path is unchanged by the regex rewrite.
        val angle = assertNotNull(angleOf("oblique 14deg"))
        assertEquals(14.0, angle.degrees, 1e-9)
        assertEquals(IRAngle.AngleUnit.DEG, angle.originalUnit)
    }

    @Test
    fun `unparseable angle rejects the whole declaration`() {
        // css-fonts-4 §2.4 grammar: `oblique <angle>?` — an invalid <angle>
        // token makes the declaration invalid (CSS2 §4.2: ignore it), so the
        // parser returns null instead of degrading to bare `oblique`.
        assertNull(FontStylePropertyParser.parse("oblique garbage"))
        // A unitless number is not an <angle> (css-values-4 §6.1 — no
        // unitless-zero exception applies to font-style).
        assertNull(FontStylePropertyParser.parse("oblique 15"))
        // Trailing garbage after a valid-looking angle is rejected too
        // (matchEntire, not prefix match).
        assertNull(FontStylePropertyParser.parse("oblique 14degx"))
    }

    @Test
    fun `bare keywords still parse`() {
        // The three keyword forms are untouched by the angle fix.
        assertTrue((FontStylePropertyParser.parse("normal") as FontStyleProperty).style
            is FontStyleProperty.FontStyle.Normal)
        assertTrue((FontStylePropertyParser.parse("italic") as FontStyleProperty).style
            is FontStyleProperty.FontStyle.Italic)
        // Bare `oblique` keeps a null angle: the 14deg §2.4 default is a
        // decode-time concern for the runtimes, not baked into the wire.
        val bare = (FontStylePropertyParser.parse("oblique") as FontStyleProperty).style
        assertNull((bare as FontStyleProperty.FontStyle.Oblique).angle)
    }
}
