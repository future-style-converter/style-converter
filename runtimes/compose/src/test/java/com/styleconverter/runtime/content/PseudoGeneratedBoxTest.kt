package com.styleconverter.runtime.content

// Wave-49 lane A2 — pin table for PseudoGeneratedBox, the raw `pseudos`
// declarations map → typed generated-BOX bridge.
//
// Every payload below is VERBATIM from the wave48-final per-test IR
// (tools/titan/runs/wave48-final/sections/*/per-test-ir/*.json), i.e. the
// exact bytes the gate replays. A simulation of this file's rules over all
// 30 frozen sections claims exactly ONE bucket
// (css-pseudo/before-as-flex-container) and names exactly one refusal
// (css-anchor-position/anchor-fieldset-001, `position`) — the two tests
// below pin both ends of that census, and the remaining cases pin the
// populations that must stay on their existing path.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PseudoGeneratedBoxTest {

    /** Parse one wire `pseudos` object exactly as the decoder retains it. */
    private fun pseudos(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    /** The typed datum for one property type in a claim, for assertions. */
    private fun datum(c: PseudoGeneratedBox.Claim?, type: String) =
        c?.properties?.firstOrNull { it.type == type }?.data

    // ── the claimed population ──────────────────────────────────────────

    @Test
    fun `before-as-flex-container generates a typed 200x100 green flex box`() {
        // css-pseudo/before-as-flex-container component __0-020, verbatim.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"A B\"", "display": "flex", "justify-content": "space-between", "width": "200px", "height": "100px", "background": "green"}, "_text": "A B", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
        )
        val claim = PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = null)
        assertNotNull(claim)
        // css-display-3 §2: `flex` is a BLOCK-LEVEL outer display, wired in
        // the corpus's SCREAMING_SNAKE spelling.
        assertEquals("FLEX", (datum(claim, "Display") as JsonPrimitive).content)
        // css-sizing-3 §3.1.1 used sizes, in the `{type:length, px:N}` shape
        // every corpus Width/Height carries.
        assertEquals(200.0, datum(claim, "Width")!!.jsonObject["px"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(100.0, datum(claim, "Height")!!.jsonObject["px"]!!.jsonPrimitive.content.toDouble(), 0.0)
        // css-color-4 `green` is #008000 — the sRGB block extractColor reads.
        val srgb = datum(claim, "BackgroundColor")!!.jsonObject["srgb"]!!.jsonObject
        assertEquals(0.0, srgb["r"]!!.jsonPrimitive.content.toDouble(), 1e-6)
        assertEquals(128.0 / 255.0, srgb["g"]!!.jsonPrimitive.content.toDouble(), 1e-6)
        assertEquals(0.0, srgb["b"]!!.jsonPrimitive.content.toDouble(), 1e-6)
        // css-align-3 §5.1 keyword, same wire spelling.
        assertEquals("SPACE_BETWEEN", (datum(claim, "JustifyContent") as JsonPrimitive).content)
        // The producer's BAKED resolution is authoritative over the raw
        // quoted `content` sequence (the `generated-content-baked` marker).
        assertEquals("A B", claim!!.text)
    }

    // ── the refused / not-owned populations ─────────────────────────────

    @Test
    fun `anchor-fieldset-001 refuses because a positioned box is not modeled`() {
        // css-anchor-position/anchor-fieldset-001 component __0__0-524,
        // verbatim: a `position: absolute` generated box needs a
        // containing-block resolution this bridge does not have, so it must
        // keep its existing render rather than paint an in-flow purple box.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"\"", "display": "block", "width": "10px", "height": "10px", "background": "purple", "position": "absolute", "position-anchor": "auto", "position-area": "bottom right"}}}"""
        )
        assertNull(PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = null))
    }

    @Test
    fun `contain-body-dir-001 root-scope bucket stays with RootPseudoBox`() {
        // css-contain/contain-body-dir-001 component __0-011, verbatim: the
        // SAME block/width/height/background shape, but on the body-root —
        // claiming it here would double-render RootPseudoBoxView's orange
        // square (wave-28 lane PG).
        val p = pseudos(
            """{"before": {"properties": {"content": "\"\"", "width": "100px", "height": "100px", "background": "orange", "display": "block"}}}"""
        )
        assertNull(PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = "body-root"))
        // …and the very same bucket on an ORDINARY element IS a box — the
        // role is the only thing separating the two populations.
        assertNotNull(PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = null))
    }

    @Test
    fun `before-dynamic-display-none generates no box at all`() {
        // css-pseudo/before-dynamic-display-none component __1-022, verbatim.
        // css-display-3 §2.5: `none` generates NO boxes, so the FAIL string
        // and its red background must never reach this path.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"FAIL\"", "position": "absolute", "width": "100px", "height": "100px", "background-color": "red", "display": "none"}, "_text": "FAIL", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
        )
        assertNull(PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = null))
    }

    @Test
    fun `scope-pseudo-element inline-block stays on the inline text path`() {
        // css-cascade/scope-pseudo-element component __0__0-120, verbatim:
        // `inline-block` is an INLINE-level outer display (css-display-3
        // §2.1), so the host's inline flow owns it — this path must decline
        // and leave the android-ref 0.9746 PASS render untouched.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"B\"", "width": "20px", "height": "20px", "display": "inline-block", "background-color": "tomato"}, "_text": "B", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
        )
        assertNull(PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = null))
    }

    @Test
    fun `a bucket with no display keyword is never a box`() {
        // The dominant corpus shape (348 `content` declarations, only 33
        // `display`): a baked counter run, which is inline by css-display-3
        // §2's initial value and belongs to PseudoBucketExtractor.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"1\" \"-\" \"5\""}, "_text": "1-5", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
        )
        assertNull(PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = null))
    }

    @Test
    fun `a block box with neither background nor glyphs paints nothing`() {
        // GATE 3 — mirrors RootPseudoSpec.paints: emitting this box would
        // add an invisible 10x10 layout node that displaces real content.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"\"", "display": "block", "width": "10px", "height": "10px"}}}"""
        )
        assertNull(PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = null))
    }

    @Test
    fun `a relative length refuses rather than guessing a px size`() {
        // CLAUDE.md's normalization rule: em/%/calc()/var() are
        // runtime-dependent and cannot be pre-computed at this seam.
        val p = pseudos(
            """{"before": {"properties": {"content": "\"\"", "display": "block", "width": "50%", "height": "2em", "background": "green"}}}"""
        )
        assertNull(PseudoGeneratedBox.claim(
            p["before"] as JsonObject, role = "before", hostRole = null))
    }
}
