package com.styleconverter.runtime.typography

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 22, lane FONT — pins the DECLARED-`normal` line-height discriminator
 * and the shared three-state line-box decision (LineHeightNormal.kt).
 *
 * ## What is being protected
 * `font: 92px Arial` RESETS line-height to `normal` (css-fonts-4 §4.3), and a
 * directly-matching declaration beats an inherited one — so Chromium lays
 * css/css-text-decor/text-decoration-dotted-001.html out on Arial's own metrics
 * (≈105.8px at 92px), not on the browser-ref injection's inherited
 * `line-height: 1.25` (115px). Until this wave the converter never emitted the
 * reset, the IR carried no LineHeight at all, and the runtime applied its
 * ABSENT-line-height WPT calibration to a case where CSS had overridden it.
 *
 * ## The invariant that must never break
 * ABSENT line-height keeps the calibration EXACTLY as before — the whole WPT
 * corpus and all 327 committed baselines ride that branch. ONLY the explicit
 * `normal` keyword changes behaviour. Both halves are pinned below.
 *
 * The `normal` and `no line-height` payloads are the LIVE wire shapes taken
 * from tools/titan/runs/wave21-final/sections/css-text-decor/per-test-ir/
 * wpt__css-text-decor__text-decoration-dotted-001.json (which carries FontSize
 * 92 + FontFamily Arial and NO LineHeight) re-converted through the fixed
 * FontExpander, so these strings are the real converter output, not invented.
 */
class LineHeightNormalTest {

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)

    /** The exact `line-height: normal` payload the converter now emits for
     *  `font: 92px Arial, sans-serif` (verified by running :converter:run on
     *  fixtures/wpt/css-text-decor/text-decoration-dotted-001.json). */
    private val normalWire = json("""{"multiplier":1.2,"original":"normal"}""")

    // ── The discriminator ───────────────────────────────────────────────────

    @Test
    fun declaredNormal_isRecognisedOnTheLiveWire() {
        // The whole lane hinges on this shape: `original` is a bare JSON
        // STRING only for the `normal` keyword (LineHeightSerializer).
        assertTrue(LineHeightNormal.isDeclaredNormal(normalWire))
    }

    @Test
    fun multiplierAloneIsNotNormal() {
        // `line-height: 1.2` (a real authored number) shares the multiplier
        // field but rides `original:{type:"number"}` — it must stay DECLARED,
        // otherwise every unitless line-height in the corpus would silently
        // fall back to font-natural metrics.
        assertFalse(LineHeightNormal.isDeclaredNormal(
            json("""{"multiplier":1.2,"original":{"type":"number","value":1.2}}""")))
    }

    @Test
    fun lengthAndPercentageAreNotNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(
            json("""{"original":{"type":"length","value":{"v":24.0,"u":"PX"}}}""")))
        assertFalse(LineHeightNormal.isDeclaredNormal(
            json("""{"multiplier":1.5,"original":{"type":"percentage","value":150.0}}""")))
    }

    @Test
    fun globalKeywordIsNotNormal() {
        // `line-height: inherit` serialises as an OBJECT at `original`
        // (type:"keyword"), never the bare string — the two must not collide.
        assertFalse(LineHeightNormal.isDeclaredNormal(
            json("""{"original":{"type":"keyword","keyword":"inherit"}}""")))
    }

    @Test
    fun malformedAndAbsentPayloadsAreNotNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(null as JsonElement?))
        assertFalse(LineHeightNormal.isDeclaredNormal(json("""1.2""")))
        assertFalse(LineHeightNormal.isDeclaredNormal(json("""[]""")))
        assertFalse(LineHeightNormal.isDeclaredNormal(json("""{"multiplier":1.2}""")))
    }

    // ── Cascade form (what the renderer calls) ──────────────────────────────

    @Test
    fun propertyList_dottedOneShape_reportsNormal() {
        // The dotted-001 component after the FontExpander fix: FontSize 92 +
        // FontFamily Arial + the reset. Order copied from the live IR.
        val props = listOf(
            IRProperty("FontSize", json("""{"px":92,"original":{"type":"length","px":92}}""")),
            IRProperty("FontFamily", json("""["Arial","sans-serif"]""")),
            IRProperty("LineHeight", normalWire)
        )
        assertTrue(LineHeightNormal.isDeclaredNormal(props))
    }

    @Test
    fun propertyList_withoutLineHeight_reportsFalse() {
        // The PRE-fix dotted-001 shape — and the shape of every corpus
        // component that never declares line-height. Must stay false so the
        // calibration keeps running for them.
        val props = listOf(
            IRProperty("FontSize", json("""{"px":92,"original":{"type":"length","px":92}}""")),
            IRProperty("FontFamily", json("""["Arial","sans-serif"]"""))
        )
        assertFalse(LineHeightNormal.isDeclaredNormal(props))
    }

    @Test
    fun propertyList_lastDeclarationWins() {
        // CSS cascade: a later declaration overrides an earlier one, in both
        // directions. The extractors fold the same list last-wins, so the gate
        // and the value can never disagree about which declaration won.
        val normalLast = listOf(
            IRProperty("LineHeight", json("""{"multiplier":2.0,"original":{"type":"number","value":2.0}}""")),
            IRProperty("LineHeight", normalWire))
        assertTrue(LineHeightNormal.isDeclaredNormal(normalLast))

        val numberLast = listOf(
            IRProperty("LineHeight", normalWire),
            IRProperty("LineHeight", json("""{"multiplier":2.0,"original":{"type":"number","value":2.0}}""")))
        assertFalse(LineHeightNormal.isDeclaredNormal(numberLast))
    }

    // ── The shared three-state table (SwiftUI twin pins the same rows) ──────

    // SKEPTIC REPAIR (wave 22, lane font-shorthand-lh) — this method was found
    // on disk as an older, PRE-GATE draft calling the two-parameter
    // `lineBoxSource(hasDeclaredValue, declaredNormal)`, which no longer exists:
    // the shipped decision in LineHeightNormal.kt takes a third `wptCapture`
    // argument and returns NATURAL only under WPT capture. The stale draft did
    // not compile (`No value passed for parameter 'wptCapture'`), taking the
    // WHOLE :runtime unit-test tree down with it, and its last row asserted the
    // OPPOSITE precedence (DECLARED over NATURAL when both hold) from the
    // contract the renderer actually implements. Rewritten below against the
    // three-argument API, pinning both the gated and ungated halves.

    @Test
    fun lineBoxSource_threeStateTable() {
        // Row 3 — a number/length was declared: use it verbatim, in BOTH modes.
        assertEquals(LineHeightNormal.LineBoxSource.DECLARED,
            LineHeightNormal.lineBoxSource(
                hasDeclaredValue = true, declaredNormal = false, wptCapture = true))
        assertEquals(LineHeightNormal.LineBoxSource.DECLARED,
            LineHeightNormal.lineBoxSource(
                hasDeclaredValue = true, declaredNormal = false, wptCapture = false))
        // Row 2 — `normal` declared: the face's own metrics (THE lane change),
        // and ONLY under WPT capture. The extractors fold the keyword's legacy
        // 1.2 stand-in into a real value, so `hasDeclaredValue` is true here —
        // which is exactly why NATURAL must be tested BEFORE DECLARED.
        assertEquals(LineHeightNormal.LineBoxSource.NATURAL,
            LineHeightNormal.lineBoxSource(
                hasDeclaredValue = true, declaredNormal = true, wptCapture = true))
        assertEquals(LineHeightNormal.LineBoxSource.DECLARED,
            LineHeightNormal.lineBoxSource(
                hasDeclaredValue = true, declaredNormal = true, wptCapture = false))
        // Row 1 — nothing declared: the WPT calibration, UNCHANGED in BOTH
        // modes. This is the row the entire corpus and every committed
        // baseline ride; a slip here regresses everything.
        assertEquals(LineHeightNormal.LineBoxSource.CALIBRATED,
            LineHeightNormal.lineBoxSource(
                hasDeclaredValue = false, declaredNormal = false, wptCapture = true))
        assertEquals(LineHeightNormal.LineBoxSource.CALIBRATED,
            LineHeightNormal.lineBoxSource(
                hasDeclaredValue = false, declaredNormal = false, wptCapture = false))
        // Totality — the keyword declared but no number extracted (no font-size
        // to fold the multiplier against): NATURAL under capture, calibration
        // otherwise. Never DECLARED, which would force-unwrap nil on the
        // SwiftUI twin.
        assertEquals(LineHeightNormal.LineBoxSource.NATURAL,
            LineHeightNormal.lineBoxSource(
                hasDeclaredValue = false, declaredNormal = true, wptCapture = true))
        assertEquals(LineHeightNormal.LineBoxSource.CALIBRATED,
            LineHeightNormal.lineBoxSource(
                hasDeclaredValue = false, declaredNormal = true, wptCapture = false))
    }
}
